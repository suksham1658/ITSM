package com.nbfc.itsm.workflow;

import com.nbfc.itsm.admin.WorkflowDesignService;
import com.nbfc.itsm.admin.WorkflowDesignService.StageForm;
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
import com.nbfc.itsm.domain.WorkflowDefinition;
import com.nbfc.itsm.domain.WorkflowStage;
import com.nbfc.itsm.identity.PortalUserService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A workflow whose service desk step is an APPROVAL by the IT Service Desk role (like Service Request v2 on
 * production): the ticket is in the Ticket Queue and a desk agent holding only that role can act on it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DeskApprovalStepTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CatalogSeedService catalogSeedService;
    @Autowired private WorkflowDesignService design;
    @Autowired private TicketService ticketService;
    @Autowired private PortalUserService portalUserService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private EmployeeRoleAssignmentRepository assignmentRepository;
    @Autowired private AssignmentGroupRepository groupRepository;
    @Autowired private TicketTypeRepository ticketTypeRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SubCategoryRepository subCategoryRepository;
    @Autowired private TicketRepository ticketRepository;

    private Employee requester;
    private Employee deskOnly;

    @BeforeEach
    void setUp() {
        catalogSeedService.ensureSeeded();
        Employee admin = employee("E-DA-SA", "Approval Admin", "SYSTEM_ADMINISTRATOR");
        requester = employee("E-DA-REQ", "Approval Requester", "EMPLOYEE");
        deskOnly = employee("E-DA-SD", "Desk Only Agent", "IT_SERVICE_DESK");

        as(admin);
        WorkflowDefinition d = design.createWorkflow("DESK_APPROVAL_FLOW", "Desk approval flow", null, null);
        StageForm desk = new StageForm();
        desk.setLabel("IT SERVICE DESK");
        desk.setStageType("APPROVAL");
        desk.setActorStrategy("NAMED_ROLE");
        desk.setRoleId(roleRepository.findByCode("IT_SERVICE_DESK").get().getRoleId());
        WorkflowStage s = design.addStage(d.getWorkflowDefinitionId(), desk);
        design.moveStage(d.getWorkflowDefinitionId(), s.getWorkflowStageId(), true);
        StageForm impl = new StageForm();
        impl.setLabel("Implementor");
        impl.setStageType("FULFILMENT");
        impl.setActorStrategy("IMPLEMENTOR");
        impl.setGroupId(groupRepository.findByCode("IT_IMPLEMENTORS").get().getAssignmentGroupId());
        WorkflowStage i = design.addStage(d.getWorkflowDefinitionId(), impl);
        design.moveStage(d.getWorkflowDefinitionId(), i.getWorkflowStageId(), true);
        design.publish(d.getWorkflowDefinitionId());
        design.saveRule(null, "Software via desk approval", 1, "Active", "{\"category\":[\"Software\"]}", d.getWorkflowDefinitionId());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deskApprovalStepIsInTheTicketQueueAndTheDeskRoleCanApprove() throws Exception {
        Ticket t = raise();
        assertTrue(ticketService.queueByStageType("ASSIGNMENT").stream().anyMatch(x -> x.getTicketId().equals(t.getTicketId())),
                "in the service desk Ticket Queue");

        mockMvc.perform(get("/queue/desk").with(authentication(token(deskOnly))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(t.getPublicNumber())));

        assertTrue(ticketService.detail(as(deskOnly), t.getTicketId()).isCanAct(), "Take action shown for the desk role");
        ticketService.applyAction(as(deskOnly), t.getTicketId(), "APPROVE", "Checked by the service desk, forwarding", (Long) null);
        assertEquals("Approved", ticketRepository.findById(t.getTicketId()).get().getStatusCode());
    }

    private Ticket raise() {
        Category software = categoryRepository.findByCode("SOFTWARE").orElseThrow(IllegalStateException::new);
        TicketForm f = new TicketForm();
        f.setTicketTypeId(ticketTypeRepository.findByCode("SERVICE_REQUEST").orElseThrow(IllegalStateException::new).getTicketTypeId());
        f.setCategoryId(software.getCategoryId());
        f.setSubCategoryId(subCategoryRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(software).get(0).getSubCategoryId());
        f.setSubject("Install a PDF editor");
        f.setDescription("Need a PDF editor for contract review work.");
        f.setIntent("submit");
        return ticketService.save(as(requester), f);
    }

    private ItsmUserPrincipal as(Employee e) {
        ItsmUserPrincipal p = portalUserService.toPrincipal(employeeRepository.findById(e.getEmployeeId()).get());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
        return p;
    }

    private UsernamePasswordAuthenticationToken token(Employee e) {
        ItsmUserPrincipal p = portalUserService.toPrincipal(employeeRepository.findById(e.getEmployeeId()).get());
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    private Employee employee(String no, String name, String... roles) {
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
        return e;
    }
}
