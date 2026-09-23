package com.nbfc.itsm.reporting;

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
import com.nbfc.itsm.util.TimeUtc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReportingServiceTest {

    @Autowired
    private CatalogSeedService catalogSeedService;
    @Autowired
    private ReportingService reportingService;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private EmployeeRoleAssignmentRepository assignmentRepository;
    @Autowired
    private TicketRepository ticketRepository;
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

    private Employee employee;
    private Employee peer;
    private Employee manager;
    private Employee desk;
    private Department it;

    @BeforeEach
    void seed() {
        catalogSeedService.ensureSeeded();
        it = departmentRepository.findByCode("IT").orElse(null);
        String tag = UUID.randomUUID().toString().substring(0, 8);
        manager = employee("M-" + tag, "mgr." + tag, "Manager " + tag, "MANAGER");
        employee = employee("E-" + tag, "emp." + tag, "Employee " + tag, "EMPLOYEE");
        employee.setManager(manager);
        employee = employeeRepository.save(employee);
        peer = employee("P-" + tag, "peer." + tag, "Peer " + tag, "EMPLOYEE");
        desk = employee("D-" + tag, "desk." + tag, "Desk " + tag, "IT_SERVICE_DESK");
        ticket("ITSM-T-" + tag + "-1", employee, "In Progress", Instant.now());
        ticket("ITSM-T-" + tag + "-2", employee, "Closed", Instant.now().minusSeconds(86400));
        ticket("ITSM-T-" + tag + "-3", peer, "In Progress", Instant.now());
    }

    @Test
    void employeeScopeExcludesPeerTickets() {
        ItsmUserPrincipal empPrin = portalUserService.toPrincipal(employee);
        ItsmUserPrincipal deskPrin = portalUserService.toPrincipal(desk);
        long empOpen = reportingService.countTickets(empPrin, employee, new ReportFilter(), extraOpen());
        long deskOpen = reportingService.countTickets(deskPrin, desk, new ReportFilter(), extraOpen());
        assertEquals(1L, empOpen);
        assertTrue(deskOpen >= 2L);
        String mine = kpi(reportingService.dashboard(empPrin), "My tickets");
        assertEquals("2", mine);
    }

    @Test
    void managerTeamIncludesDirectReports() {
        ItsmUserPrincipal mgrPrin = portalUserService.toPrincipal(manager);
        long teamOpen = reportingService.countTickets(mgrPrin, manager, new ReportFilter(), extraOpen());
        assertEquals(1L, teamOpen);
        String myTickets = kpi(reportingService.dashboard(mgrPrin), "My tickets");
        assertEquals("0", myTickets);
    }

    @Test
    void kpisMatchInsertedOpenAndClosed() {
        ItsmUserPrincipal empPrin = portalUserService.toPrincipal(employee);
        DashboardSnapshot snap = reportingService.dashboard(empPrin);
        assertEquals("1", kpi(snap, "Total Open Tickets"));
        assertEquals("Your tickets and assignments", snap.getScopeLabel());
        assertTrue(!snap.getStatusChart().isEmpty());
    }

    @Test
    void securityReportDeniedWithoutScope() {
        ItsmUserPrincipal empPrin = portalUserService.toPrincipal(employee);
        assertThrows(AccessDeniedException.class, () -> reportingService.assertReportAllowed(empPrin, "security"));
        ItsmUserPrincipal deskPrin = portalUserService.toPrincipal(desk);
        reportingService.assertReportAllowed(deskPrin, "security");
    }

    @Test
    void volumeReportIsServerSideAndScoped() {
        ItsmUserPrincipal deskPrin = portalUserService.toPrincipal(desk);
        ReportFilter filter = new ReportFilter();
        filter.setFrom(LocalDate.now().minusDays(7));
        filter.setTo(LocalDate.now());
        NamedReport report = reportingService.run(deskPrin, "volume", filter, 0, 25);
        assertEquals("Ticket Volume", report.getTitle());
        assertTrue(report.getTotalRows() > 0);
        long opened = 0;
        for (ReportRow row : report.getRows()) {
            opened += Long.parseLong(row.getCells().get(1));
        }
        assertTrue(opened >= 3L);
    }

    @Test
    void reopenReportIsHonestZero() {
        ItsmUserPrincipal deskPrin = portalUserService.toPrincipal(desk);
        NamedReport report = reportingService.run(deskPrin, "reopen", new ReportFilter(), 0, 25);
        assertTrue(report.getNote() != null && report.getNote().toLowerCase().contains("not modelled"));
        assertEquals("0", report.getRows().get(0).getCells().get(1));
    }

    private ReportingService.ExtraPredicate extraOpen() {
        return reportingService.statusNotIn(java.util.Arrays.asList("Closed", "Rejected"));
    }

    private String kpi(DashboardSnapshot snap, String label) {
        for (KpiCard card : snap.getKpis()) {
            if (label.equals(card.getLabel())) {
                return card.getValue();
            }
        }
        throw new AssertionError("Missing KPI " + label);
    }

    private Employee employee(String no, String sam, String name, String roleCode) {
        Employee emp = new Employee();
        emp.setEmployeeNo(no);
        emp.setSamAccountName(sam);
        emp.setDisplayName(name);
        emp.setEmail(sam + "@localhost");
        emp.setDepartment(it);
        emp.setPortalActive(true);
        emp = employeeRepository.save(emp);
        emp.setHod(emp);
        emp = employeeRepository.save(emp);
        Role role = roleRepository.findByCode(roleCode).orElse(null);
        if (role != null) {
            EmployeeRoleAssignment row = new EmployeeRoleAssignment();
            row.setEmployee(emp);
            row.setRole(role);
            row.setAssignedAtUtc(TimeUtc.now());
            assignmentRepository.save(row);
        }
        return emp;
    }

    private Ticket ticket(String number, Employee requester, String status, Instant created) {
        TicketType type = ticketTypeRepository.findByCode("INCIDENT").orElse(null);
        Category cat = categoryRepository.findByCode("HARDWARE").orElse(null);
        SubCategory sub = subCategoryRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(cat).get(0);
        Ticket t = new Ticket();
        t.setPublicNumber(number);
        t.setTicketType(type);
        t.setCategory(cat);
        t.setSubCategory(sub);
        t.setSubject("Reporting fixture " + number);
        t.setDescription("Scoped aggregation fixture.");
        t.setPriorityCode("Medium");
        t.setImpactCode("Medium");
        t.setUrgencyCode("Medium");
        t.setConfidentialityCode("Internal");
        t.setRequester(requester);
        t.setDepartment(it);
        t.setStatusCode(status);
        t.setCreatedAtUtc(created);
        t.setUpdatedAtUtc(created);
        t.setMajorIncident(false);
        return ticketRepository.save(t);
    }
}
