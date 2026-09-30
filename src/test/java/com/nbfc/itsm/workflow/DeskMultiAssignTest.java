package com.nbfc.itsm.workflow;

import com.nbfc.itsm.admin.CategoryImplementorService;
import com.nbfc.itsm.domain.AssignmentGroupMember;
import com.nbfc.itsm.domain.AssignmentGroupMemberRepository;
import com.nbfc.itsm.domain.AssignmentGroupRepository;
import com.nbfc.itsm.domain.Category;
import com.nbfc.itsm.domain.CategoryRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.RoleRepository;
import com.nbfc.itsm.domain.SubCategoryRepository;
import com.nbfc.itsm.domain.Ticket;
import com.nbfc.itsm.domain.TicketRepository;
import com.nbfc.itsm.domain.TicketTypeRepository;
import com.nbfc.itsm.domain.WorkflowInstance;
import com.nbfc.itsm.domain.WorkflowInstanceRepository;
import com.nbfc.itsm.domain.WorkflowInstanceStage;
import com.nbfc.itsm.domain.WorkflowInstanceStageRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.identity.PortalUserService;
import com.nbfc.itsm.notification.TicketEmailEvent;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.seed.CatalogSeedService;
import com.nbfc.itsm.ticket.TicketForm;
import com.nbfc.itsm.ticket.TicketService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** IT Service Desk sends a ticket to one, several or all implementors of its category, or rejects it. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
class DeskMultiAssignTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationEvents events;
    @Autowired private CatalogSeedService catalogSeedService;
    @Autowired private CategoryImplementorService categoryImplementors;
    @Autowired private WorkflowEngine workflowEngine;
    @Autowired private TicketService ticketService;
    @Autowired private PortalUserService portalUserService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private EmployeeRoleAssignmentRepository assignmentRepository;
    @Autowired private AssignmentGroupRepository groupRepository;
    @Autowired private AssignmentGroupMemberRepository memberRepository;
    @Autowired private TicketTypeRepository ticketTypeRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SubCategoryRepository subCategoryRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private WorkflowInstanceRepository instanceRepository;
    @Autowired private WorkflowInstanceStageRepository instanceStageRepository;

    private Employee admin;
    private Employee requester;
    private Employee desk;
    private Employee impl1;
    private Employee impl2;
    private Employee impl3;
    private Category hardware;

    @BeforeEach
    void setUp() {
        catalogSeedService.ensureSeeded();
        admin = employee("E-DM-SA", "Desk Admin", null, "EMPLOYEE", "SYSTEM_ADMINISTRATOR");
        requester = employee("E-DM-REQ", "Desk Requester", null, "EMPLOYEE");
        desk = employee("E-DM-SD", "Desk Agent", "IT_SERVICE_DESK", "EMPLOYEE", "IT_SERVICE_DESK");
        impl1 = employee("E-DM-I1", "Anita Implementor", "IT_IMPLEMENTORS", "EMPLOYEE", "IT_IMPLEMENTOR");
        impl2 = employee("E-DM-I2", "Bharat Implementor", "IT_IMPLEMENTORS", "EMPLOYEE", "IT_IMPLEMENTOR");
        impl3 = employee("E-DM-I3", "Chetan Network", "IT_IMPLEMENTORS", "EMPLOYEE", "IT_IMPLEMENTOR");
        hardware = categoryRepository.findByCode("HARDWARE").orElseThrow(IllegalStateException::new);
        as(admin);
        categoryImplementors.save(hardware.getCategoryId(), Arrays.asList(impl1.getEmployeeId(), impl2.getEmployeeId(),
                impl1.getEmployeeId()), principal(admin));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deskSeesOnlyTheCategoryImplementorsAndCanSendToSeveral() {
        Ticket t = raiseIncident();
        List<Employee> eligible = workflowEngine.eligibleImplementors(t, stages(t), current(t));
        assertEquals(Arrays.asList("Anita Implementor", "Bharat Implementor"),
                eligible.stream().map(Employee::getDisplayName).collect(Collectors.toList()), "category list, no repeats");

        ItsmException outside = assertThrows(ItsmException.class, () -> ticketService.applyActionFor(as(desk), t.getTicketId(),
                "ASSIGN", null, Arrays.asList(impl3.getEmployeeId())));
        assertTrue(outside.getMessage().contains("not an implementor for this ticket's category"), outside.getMessage());

        ticketService.applyActionFor(as(desk), t.getTicketId(), "ASSIGN", "Please check",
                Arrays.asList(impl1.getEmployeeId(), impl2.getEmployeeId(), impl2.getEmployeeId()));
        WorkflowInstanceStage work = current(t);
        assertEquals("FULFILMENT", work.getStageType());
        assertNull(work.getResolvedEmployee(), "not owned until someone picks it up");
        assertEquals(new HashSet<Long>(Arrays.asList(impl1.getEmployeeId(), impl2.getEmployeeId())), work.getAssigneeIds());
        assertEquals(new HashSet<Long>(Arrays.asList(impl1.getEmployeeId(), impl2.getEmployeeId())),
                new HashSet<Long>(lastQueueMailTo()), "e-mail to exactly the ticked implementors");
        assertTrue(ticketService.assignedTo(as(impl1)).stream().anyMatch(x -> x.getTicketId().equals(t.getTicketId())),
                "in each selected implementor's My Assigned Tickets");

        assertThrows(AccessDeniedException.class,
                () -> ticketService.applyAction(as(impl3), t.getTicketId(), "START", null, (Long) null), "not selected");

        ticketService.applyAction(as(impl2), t.getTicketId(), "START", null, (Long) null);
        assertEquals(impl2.getEmployeeId(), current(t).getResolvedEmployee().getEmployeeId(), "first to start owns it");
        assertEquals(impl2.getEmployeeId(), ticketRepository.findById(t.getTicketId()).get().getAssignedImplementor().getEmployeeId());
    }

    @Test
    void oneImplementorIsAssignedDirectly() {
        Ticket t = raiseIncident();
        ticketService.applyActionFor(as(desk), t.getTicketId(), "ASSIGN", null, Arrays.asList(impl1.getEmployeeId()));
        assertEquals(impl1.getEmployeeId(), current(t).getResolvedEmployee().getEmployeeId());
        assertTrue(current(t).getAssigneeIds().isEmpty());
    }

    @Test
    void deskCanRejectWithRemarks() {
        Ticket t = raiseIncident();
        assertThrows(ItsmException.class, () -> ticketService.applyAction(as(desk), t.getTicketId(), "REJECT", "no", (Long) null));
        ticketService.applyAction(as(desk), t.getTicketId(), "REJECT", "Duplicate of an existing incident", (Long) null);
        assertEquals("Rejected", ticketRepository.findById(t.getTicketId()).get().getStatusCode());
    }

    @Test
    void categoryWithoutImplementorsOffersTheWholeGroup() {
        as(admin);
        categoryImplementors.save(hardware.getCategoryId(), null, principal(admin));
        Ticket t = raiseIncident();
        List<String> names = workflowEngine.eligibleImplementors(t, stages(t), current(t)).stream()
                .map(Employee::getDisplayName).collect(Collectors.toList());
        assertTrue(names.containsAll(Arrays.asList("Anita Implementor", "Bharat Implementor", "Chetan Network")), names.toString());
    }

    @Test
    void pagesShowThePickerAndTheCategoryEditor() throws Exception {
        Ticket t = raiseIncident();
        mockMvc.perform(get("/tickets/{id}", t.getTicketId()).with(authentication(token(desk))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Select all"), containsString("name=\"assigneeIds\""),
                        containsString("Anita Implementor"), not(containsString("Chetan Network")), containsString("REJECT"))));
        mockMvc.perform(get("/admin/categories").with(authentication(token(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Choose implementors"), containsString("Bharat Implementor"))));
    }

    // ------------------------------------------------------------------ helpers

    private List<Long> lastQueueMailTo() {
        List<TicketEmailEvent> w = events.stream(TicketEmailEvent.class)
                .filter(e -> e.getKind() == TicketEmailEvent.Kind.WAITING).collect(Collectors.toList());
        return w.get(w.size() - 1).getRecipientIds();
    }

    private List<WorkflowInstanceStage> stages(Ticket t) {
        WorkflowInstance inst = instanceRepository.findByTicketId(t.getTicketId()).orElseThrow(IllegalStateException::new);
        return instanceStageRepository.findByWorkflowInstanceOrderByStageOrderAsc(inst);
    }

    private WorkflowInstanceStage current(Ticket t) {
        for (WorkflowInstanceStage s : stages(t)) {
            if ("Current".equals(s.getStatusCode())) {
                return s;
            }
        }
        throw new IllegalStateException("no current stage");
    }

    private Ticket raiseIncident() {
        TicketForm f = new TicketForm();
        f.setTicketTypeId(ticketTypeRepository.findByCode("INCIDENT").orElseThrow(IllegalStateException::new).getTicketTypeId());
        f.setCategoryId(hardware.getCategoryId());
        f.setSubCategoryId(subCategoryRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(hardware).get(0).getSubCategoryId());
        f.setSubject("Laptop not starting");
        f.setDescription("The laptop does not power on since this morning.");
        f.setPriorityCode("High");
        f.setIntent("submit");
        return ticketService.save(as(requester), f);
    }

    private ItsmUserPrincipal as(Employee e) {
        ItsmUserPrincipal p = principal(e);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
        return p;
    }

    private ItsmUserPrincipal principal(Employee e) {
        return portalUserService.toPrincipal(employeeRepository.findById(e.getEmployeeId()).get());
    }

    private UsernamePasswordAuthenticationToken token(Employee e) {
        ItsmUserPrincipal p = principal(e);
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    private Employee employee(String no, String name, String groupCode, String... roles) {
        Employee e = new Employee();
        e.setEmployeeNo(no);
        e.setSamAccountName(no.toLowerCase());
        e.setDisplayName(name);
        e.setPortalActive(true);
        e = employeeRepository.save(e);
        for (String code : roles) {
            EmployeeRoleAssignment row = new EmployeeRoleAssignment();
            row.setEmployee(e);
            row.setRole(roleRepository.findByCode(code).orElseThrow(() -> new IllegalStateException(code)));
            assignmentRepository.save(row);
        }
        if (groupCode != null) {
            AssignmentGroupMember m = new AssignmentGroupMember();
            m.setEmployee(e);
            m.setAssignmentGroup(groupRepository.findByCode(groupCode).orElseThrow(IllegalStateException::new));
            memberRepository.save(m);
        }
        return e;
    }
}
