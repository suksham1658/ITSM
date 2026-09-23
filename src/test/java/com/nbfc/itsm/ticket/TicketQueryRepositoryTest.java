package com.nbfc.itsm.ticket;

import com.nbfc.itsm.domain.Category;
import com.nbfc.itsm.domain.CategoryRepository;
import com.nbfc.itsm.domain.Department;
import com.nbfc.itsm.domain.DepartmentRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.Role;
import com.nbfc.itsm.domain.RoleRepository;
import com.nbfc.itsm.domain.SubCategory;
import com.nbfc.itsm.domain.SubCategoryRepository;
import com.nbfc.itsm.domain.Ticket;
import com.nbfc.itsm.domain.TicketRepository;
import com.nbfc.itsm.domain.TicketType;
import com.nbfc.itsm.domain.TicketTypeRepository;
import com.nbfc.itsm.identity.PortalUserService;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.seed.CatalogSeedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketQueryRepositoryTest {

    @Autowired
    private CatalogSeedService catalogSeedService;
    @Autowired
    private TicketService ticketService;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private EmployeeRoleAssignmentRepository assignmentRepository;
    @Autowired
    private TicketTypeRepository ticketTypeRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private SubCategoryRepository subCategoryRepository;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private PortalUserService portalUserService;

    private Employee manager;
    private Employee report;
    private Employee peer;
    private Ticket reportTicket;
    private Ticket peerTicket;

    @BeforeEach
    void seed() {
        catalogSeedService.ensureSeeded();
        Department it = departmentRepository.findByCode("IT").orElse(null);
        String tag = UUID.randomUUID().toString().substring(0, 8);
        manager = employee("MGR-" + tag, "mgrq." + tag, "Manager Q " + tag, "MANAGER", it);
        report = employee("REP-" + tag, "repq." + tag, "Report Q " + tag, "EMPLOYEE", it);
        report.setManager(manager);
        report = employeeRepository.save(report);
        peer = employee("PEER-" + tag, "peerq." + tag, "Peer Q " + tag, "EMPLOYEE", it);
        reportTicket = ticket("ITSM-Q-" + tag + "-1", report, "In Progress");
        peerTicket = ticket("ITSM-Q-" + tag + "-2", peer, "In Progress");
        ticket("ITSM-Q-" + tag + "-3", report, "Closed");
    }

    @Test
    void teamSearchIsDirectReportsNotWholeDepartment() {
        ItsmUserPrincipal mgr = portalUserService.toPrincipal(manager);
        Page<Ticket> page = ticketService.search(mgr, "team", null, null, null, null, PageRequest.of(0, 20));
        boolean sawReport = false;
        boolean sawPeer = false;
        for (Ticket t : page.getContent()) {
            if (t.getTicketId().equals(reportTicket.getTicketId())) {
                sawReport = true;
            }
            if (t.getTicketId().equals(peerTicket.getTicketId())) {
                sawPeer = true;
            }
        }
        assertTrue(sawReport);
        assertTrue(!sawPeer);
        assertTrue(page.getTotalElements() >= 2L);
    }

    @Test
    void mineSearchFiltersStatusWithoutLoadingEveryRow() {
        ItsmUserPrincipal emp = portalUserService.toPrincipal(report);
        Page<Ticket> open = ticketService.search(emp, "mine", null, "In Progress", null, null, PageRequest.of(0, 10));
        assertEquals(1, open.getTotalElements());
        Page<Ticket> closed = ticketService.search(emp, "mine", null, "Closed", null, null, PageRequest.of(0, 10));
        assertEquals(1, closed.getTotalElements());
        Page<Ticket> paged = ticketService.search(emp, "mine", null, null, null, null, PageRequest.of(0, 1));
        assertEquals(1, paged.getContent().size());
        assertEquals(2, paged.getTotalElements());
    }

    @Test
    void countOpenExcludesClosedAndRejected() {
        long open = ticketRepository.countByStatusCodeNotIn(Arrays.asList("Closed", "Rejected"));
        assertTrue(open >= 2L);
    }

    private Employee employee(String no, String sam, String name, String roleCode, Department dept) {
        Employee emp = new Employee();
        emp.setEmployeeNo(no);
        emp.setSamAccountName(sam);
        emp.setDisplayName(name);
        emp.setEmail(sam + "@localhost");
        emp.setDepartment(dept);
        emp.setPortalActive(true);
        emp = employeeRepository.save(emp);
        emp.setHod(emp);
        emp = employeeRepository.save(emp);
        Role role = roleRepository.findByCode(roleCode).orElse(null);
        if (role != null) {
            EmployeeRoleAssignment row = new EmployeeRoleAssignment();
            row.setEmployee(emp);
            row.setRole(role);
            assignmentRepository.save(row);
        }
        return emp;
    }

    private Ticket ticket(String number, Employee requester, String status) {
        TicketType type = ticketTypeRepository.findByCode("INCIDENT").orElse(null);
        Category cat = categoryRepository.findByCode("HARDWARE").orElse(null);
        SubCategory sub = subCategoryRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(cat).get(0);
        Ticket t = new Ticket();
        t.setPublicNumber(number);
        t.setTicketType(type);
        t.setCategory(cat);
        t.setSubCategory(sub);
        t.setSubject("Query fixture " + number);
        t.setDescription("Repository query fixture.");
        t.setPriorityCode("Medium");
        t.setImpactCode("Medium");
        t.setUrgencyCode("Medium");
        t.setConfidentialityCode("Internal");
        t.setRequester(requester);
        t.setDepartment(requester.getDepartment());
        t.setStatusCode(status);
        t.setCreatedAtUtc(Instant.now());
        t.setUpdatedAtUtc(Instant.now());
        t.setMajorIncident(false);
        return ticketRepository.save(t);
    }
}
