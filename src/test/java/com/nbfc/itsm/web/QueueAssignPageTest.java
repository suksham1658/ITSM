package com.nbfc.itsm.web;

import com.nbfc.itsm.domain.Category;
import com.nbfc.itsm.domain.CategoryRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.RoleRepository;
import com.nbfc.itsm.domain.SubCategoryRepository;
import com.nbfc.itsm.domain.Ticket;
import com.nbfc.itsm.domain.TicketTypeRepository;
import com.nbfc.itsm.identity.PortalUserService;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.seed.CatalogSeedService;
import com.nbfc.itsm.ticket.TicketForm;
import com.nbfc.itsm.ticket.TicketService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A service-desk user with the role (no group set-up) sees the Assign button and the Take action form. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class QueueAssignPageTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CatalogSeedService catalogSeedService;
    @Autowired private PortalUserService portalUserService;
    @Autowired private TicketService ticketService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private EmployeeRoleAssignmentRepository assignmentRepository;
    @Autowired private TicketTypeRepository ticketTypeRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SubCategoryRepository subCategoryRepository;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deskAgentSeesAssignButtonAndImplementorsInTheForm() throws Exception {
        catalogSeedService.ensureSeeded();
        Employee requester = employee("E-QA-REQ", "Queue Requester", "EMPLOYEE");
        Employee desk = employee("E-QA-SD", "Queue Desk Agent", "EMPLOYEE", "IT_SERVICE_DESK");
        employee("E-QA-IMP", "Queue Implementor", "EMPLOYEE", "IT_IMPLEMENTOR");

        ItsmUserPrincipal req = principal(requester);
        SecurityContextHolder.getContext().setAuthentication(token(req));
        Ticket t = ticketService.save(req, incident());
        SecurityContextHolder.clearContext();

        ItsmUserPrincipal agent = principal(desk);
        mockMvc.perform(get("/queue/desk").with(authentication(token(agent))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/tickets/" + t.getTicketId() + "#take-action")))
                .andExpect(content().string(containsString("fa-user-plus")));

        mockMvc.perform(get("/tickets/{id}", t.getTicketId()).with(authentication(token(agent))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("id=\"take-action\""),
                        containsString("Send to implementor(s)"),
                        containsString("Queue Implementor"),
                        containsString("it is your turn"))));
    }

    private TicketForm incident() {
        Category hw = categoryRepository.findByCode("HARDWARE").orElseThrow(IllegalStateException::new);
        TicketForm f = new TicketForm();
        f.setSerialMode("NA");
        f.setTicketTypeId(ticketTypeRepository.findByCode("INCIDENT").orElseThrow(IllegalStateException::new).getTicketTypeId());
        f.setCategoryId(hw.getCategoryId());
        f.setSubCategoryId(subCategoryRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(hw).get(0).getSubCategoryId());
        f.setSubject("Monitor flickering badly");
        f.setDescription("The monitor flickers every few seconds.");
        f.setIntent("submit");
        return f;
    }

    private ItsmUserPrincipal principal(Employee e) {
        return portalUserService.toPrincipal(employeeRepository.findById(e.getEmployeeId()).get());
    }

    private static UsernamePasswordAuthenticationToken token(ItsmUserPrincipal p) {
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
