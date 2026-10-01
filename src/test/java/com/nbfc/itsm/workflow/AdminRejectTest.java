package com.nbfc.itsm.workflow;

import com.nbfc.itsm.domain.Category;
import com.nbfc.itsm.domain.CategoryRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.NotificationRepository;
import com.nbfc.itsm.domain.RoleRepository;
import com.nbfc.itsm.domain.SubCategoryRepository;
import com.nbfc.itsm.domain.Ticket;
import com.nbfc.itsm.domain.TicketRepository;
import com.nbfc.itsm.domain.TicketTypeRepository;
import com.nbfc.itsm.exception.ItsmException;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A System Administrator can reject any open ticket at any step. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminRejectTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CatalogSeedService catalogSeedService;
    @Autowired private TicketService ticketService;
    @Autowired private PortalUserService portalUserService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private EmployeeRoleAssignmentRepository assignmentRepository;
    @Autowired private TicketTypeRepository ticketTypeRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SubCategoryRepository subCategoryRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private NotificationRepository notificationRepository;

    private Employee admin;
    private Employee manager;
    private Employee requester;

    @BeforeEach
    void setUp() {
        catalogSeedService.ensureSeeded();
        admin = employee("E-AR-SA", "Reject Admin", null, "EMPLOYEE", "SYSTEM_ADMINISTRATOR");
        manager = employee("E-AR-MGR", "Reject Manager", null, "EMPLOYEE", "HOD");
        requester = employee("E-AR-REQ", "Reject Requester", manager, "EMPLOYEE");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void systemAdministratorRejectsATicketWaitingForSomeoneElse() throws Exception {
        Ticket sr = raise();
        mockMvc.perform(get("/tickets/{id}", sr.getTicketId()).with(authentication(token(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Reject ticket (System Administrator)")));
        mockMvc.perform(get("/tickets/{id}", sr.getTicketId()).with(authentication(token(manager))))
                .andExpect(content().string(not(containsString("Reject ticket (System Administrator)"))));

        mockMvc.perform(post("/tickets/{id}/admin-reject", sr.getTicketId()).param("remarks", "short")
                        .with(csrf()).with(authentication(token(admin))))
                .andExpect(flash().attribute("errorMessage", containsString("Remarks are mandatory")));

        mockMvc.perform(post("/tickets/{id}/admin-reject", sr.getTicketId()).param("remarks", "Raised under the wrong flow, please raise again")
                        .with(csrf()).with(authentication(token(admin))))
                .andExpect(flash().attribute("message", containsString("rejected by System Administrator")));
        Ticket t = ticketRepository.findById(sr.getTicketId()).get();
        assertEquals("Rejected", t.getStatusCode());
        assertTrue(notificationRepository.findAll().stream().anyMatch(n -> n.getRecipientId().equals(requester.getEmployeeId())
                && n.getTitle().startsWith("Declined")), "requester notified");

        assertThrows(ItsmException.class, () -> ticketService.adminReject(as(admin), sr.getTicketId(), "Rejecting a second time"));
    }

    @Test
    void approvalsPageShowsTheApproversStep() throws Exception {
        Ticket sr = raise();
        mockMvc.perform(get("/approvals").with(authentication(token(manager))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(sr.getPublicNumber())))
                .andExpect(content().string(containsString(">Approve</span>")));
        mockMvc.perform(get("/approvals").with(authentication(token(requester))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/tickets/" + sr.getTicketId() + "\""))))
                .andExpect(content().string(containsString("Nothing is waiting for you")));
    }

    @Test
    void othersCannotUseTheOverride() throws Exception {
        Ticket sr = raise();
        assertThrows(AccessDeniedException.class,
                () -> ticketService.adminReject(as(manager), sr.getTicketId(), "Not an administrator trying"));
        mockMvc.perform(post("/tickets/{id}/admin-reject", sr.getTicketId()).param("remarks", "Not an administrator trying")
                        .with(csrf()).with(authentication(token(manager))))
                .andExpect(status().isForbidden());
    }

    private Ticket raise() {
        Category network = categoryRepository.findByCode("NETWORK").orElseThrow(IllegalStateException::new);
        TicketForm f = new TicketForm();
        f.setTicketTypeId(ticketTypeRepository.findByCode("SERVICE_REQUEST").orElseThrow(IllegalStateException::new).getTicketTypeId());
        f.setCategoryId(network.getCategoryId());
        f.setSubCategoryId(subCategoryRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(network).get(0).getSubCategoryId());
        f.setSubject("VPN access for remote work");
        f.setDescription("Need VPN access to work from the branch office.");
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

    private Employee employee(String no, String name, Employee manager, String... roles) {
        Employee e = new Employee();
        e.setEmployeeNo(no);
        e.setSamAccountName(no.toLowerCase());
        e.setDisplayName(name);
        e.setManager(manager);
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
