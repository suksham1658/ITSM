package com.nbfc.itsm.config;

import com.nbfc.itsm.domain.AssignmentGroup;
import com.nbfc.itsm.domain.AssignmentGroupMember;
import com.nbfc.itsm.domain.AssignmentGroupMemberRepository;
import com.nbfc.itsm.domain.AssignmentGroupRepository;
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
import com.nbfc.itsm.domain.SlaPolicy;
import com.nbfc.itsm.domain.SlaPolicyRepository;
import com.nbfc.itsm.domain.SubCategory;
import com.nbfc.itsm.domain.SubCategoryRepository;
import com.nbfc.itsm.domain.Ticket;
import com.nbfc.itsm.domain.TicketRepository;
import com.nbfc.itsm.domain.TicketSla;
import com.nbfc.itsm.domain.TicketSlaRepository;
import com.nbfc.itsm.domain.TicketType;
import com.nbfc.itsm.domain.TicketTypeRepository;
import com.nbfc.itsm.seed.CatalogSeedService;
import com.nbfc.itsm.util.TimeUtc;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * H2 preview org: catalog + LDAP-hop stubs so one login can walk SR and Incident.
 * Password is never stored; H2_PREVIEW_PASSWORD is checked in memory.
 */
@Component
@Profile("h2")
@Order(1)
public class H2PreviewDataLoader implements ApplicationRunner {

    private final CatalogSeedService catalogSeedService;
    private final EmployeeRepository employeeRepository;
    private final RoleRepository roleRepository;
    private final EmployeeRoleAssignmentRepository assignmentRepository;
    private final AssignmentGroupRepository groupRepository;
    private final AssignmentGroupMemberRepository groupMemberRepository;
    private final DepartmentRepository departmentRepository;
    private final TicketRepository ticketRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final CategoryRepository categoryRepository;
    private final SubCategoryRepository subCategoryRepository;
    private final SlaPolicyRepository slaPolicyRepository;
    private final TicketSlaRepository ticketSlaRepository;

    public H2PreviewDataLoader(CatalogSeedService catalogSeedService,
                               EmployeeRepository employeeRepository,
                               RoleRepository roleRepository,
                               EmployeeRoleAssignmentRepository assignmentRepository,
                               AssignmentGroupRepository groupRepository,
                               AssignmentGroupMemberRepository groupMemberRepository,
                               DepartmentRepository departmentRepository,
                               TicketRepository ticketRepository,
                               TicketTypeRepository ticketTypeRepository,
                               CategoryRepository categoryRepository,
                               SubCategoryRepository subCategoryRepository,
                               SlaPolicyRepository slaPolicyRepository,
                               TicketSlaRepository ticketSlaRepository) {
        this.catalogSeedService = catalogSeedService;
        this.employeeRepository = employeeRepository;
        this.roleRepository = roleRepository;
        this.assignmentRepository = assignmentRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.departmentRepository = departmentRepository;
        this.ticketRepository = ticketRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.categoryRepository = categoryRepository;
        this.subCategoryRepository = subCategoryRepository;
        this.slaPolicyRepository = slaPolicyRepository;
        this.ticketSlaRepository = ticketSlaRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        catalogSeedService.ensureSeeded();
        ensureEmployees();
        ensureDemoTickets();
    }

    private void ensureEmployees() {
        if (employeeRepository.findBySamAccountNameIgnoreCase("preview.admin").isPresent()) {
            return;
        }
        Department it = departmentRepository.findByCode("IT").orElse(null);
        Department fin = departmentRepository.findByCode("FIN").orElse(null);

        Employee hod = employee("E-HOD", "hod.preview", "Neha Singh", "IT Head", it, null, null);
        hod.setHod(hod);
        hod = employeeRepository.save(hod);

        Employee manager = employee("E-MGR", "manager.preview", "Kiran Rao", "IT Manager", it, hod, hod);
        manager = employeeRepository.save(manager);

        Employee admin = employee("E-PREVIEW", "preview.admin", "Preview Administrator", "Local H2 preview", it, null, null);
        admin.setHod(admin);
        admin.setManager(admin);
        admin = employeeRepository.save(admin);

        Employee finance = employee("E-FIN", "finance.preview", "Ananya Shah", "Finance Analyst", fin, manager, hod);
        employeeRepository.save(finance);

        grant(admin, "SYSTEM_ADMINISTRATOR");
        grant(admin, "CISO");
        grant(admin, "IT_SERVICE_DESK");
        grant(admin, "IT_IMPLEMENTOR");
        grant(admin, "HOD");
        grant(manager, "MANAGER");
        grant(hod, "HOD");
        grant(finance, "EMPLOYEE");

        addToGroup(admin, "IT_SERVICE_DESK");
        addToGroup(admin, "IT_IMPLEMENTORS");
        addToGroup(admin, "IT_SECURITY_TEAM");
    }

    private void ensureDemoTickets() {
        if (ticketRepository.count() > 0) {
            return;
        }
        Employee admin = employeeRepository.findBySamAccountNameIgnoreCase("preview.admin").orElse(null);
        Employee manager = employeeRepository.findBySamAccountNameIgnoreCase("manager.preview").orElse(admin);
        Employee hod = employeeRepository.findBySamAccountNameIgnoreCase("hod.preview").orElse(admin);
        Employee finance = employeeRepository.findBySamAccountNameIgnoreCase("finance.preview").orElse(manager);
        if (admin == null) {
            return;
        }
        Instant now = Instant.now();
        Ticket t1 = ticket("ITSM-2026-900001", "INCIDENT", "HARDWARE", "Laptop will not boot after Windows update",
                "Critical", "In Progress", admin, admin.getDepartment(), now.minus(Duration.ofHours(6)), admin);
        sla(t1, "NEAR", now.minus(Duration.ofHours(6)), null);

        Ticket t2 = ticket("ITSM-2026-900002", "SERVICE_REQUEST", "SOFTWARE", "Install DLP client on underwriting laptop",
                "Medium", "Pending Approval", manager, manager.getDepartment(), now.minus(Duration.ofDays(2)), null);
        sla(t2, "WITHIN", now.minus(Duration.ofDays(2)), null);

        Ticket t3 = ticket("ITSM-2026-900003", "INCIDENT", "NETWORK", "VPN drops every 10 minutes from HO",
                "High", "Assigned", hod, hod.getDepartment(), now.minus(Duration.ofDays(1)), admin);
        sla(t3, "WITHIN", now.minus(Duration.ofDays(1)), null);

        Ticket t4 = ticket("ITSM-2026-900004", "INCIDENT", "EMAIL", "Outlook cannot send to external domains",
                "Medium", "Resolved", finance, finance.getDepartment(), now.minus(Duration.ofDays(8)), admin);
        sla(t4, "WITHIN", now.minus(Duration.ofDays(8)), now.minus(Duration.ofDays(7)));

        Ticket t5 = ticket("ITSM-2026-900005", "INCIDENT", "HARDWARE", "Desktop power supply failed — branch 14",
                "High", "Closed", manager, manager.getDepartment(), now.minus(Duration.ofDays(40)), admin);
        sla(t5, "WITHIN", now.minus(Duration.ofDays(40)), now.minus(Duration.ofDays(39)));

        Ticket t6 = ticket("ITSM-2026-900006", "SECURITY_INCIDENT", "CYBER_SECURITY", "Phishing mail targeting collections officers",
                "Critical", "In Progress", finance, finance.getDepartment(), now.minus(Duration.ofHours(20)), admin);
        t6.setConfidentialityCode("Highly Confidential");
        ticketRepository.save(t6);
        sla(t6, "BREACHED", now.minus(Duration.ofHours(30)), null);

        Ticket t7 = ticket("ITSM-2026-900007", "SERVICE_REQUEST", "ACCESS_MGMT", "Unlock Active Directory account",
                "Low", "Draft", manager, manager.getDepartment(), now.minus(Duration.ofHours(2)), null);

        Ticket t8 = ticket("ITSM-2026-900008", "INCIDENT", "APPLICATION", "LOS application timeout on credit appraisal",
                "High", "On Hold", hod, hod.getDepartment(), now.minus(Duration.ofDays(5)), admin);
        sla(t8, "NEAR", now.minus(Duration.ofDays(5)), null);

        Ticket t9 = ticket("ITSM-2026-900009", "HARDWARE_REQUEST", "HARDWARE", "Replacement laptop for field collections",
                "Medium", "Assigned", finance, finance.getDepartment(), now.minus(Duration.ofDays(12)), admin);
        sla(t9, "WITHIN", now.minus(Duration.ofDays(12)), null);

        Ticket t10 = ticket("ITSM-2026-900010", "INCIDENT", "SOFTWARE", "Core banking print spooler stuck",
                "Medium", "Closed", admin, admin.getDepartment(), now.minus(Duration.ofDays(70)), admin);
        sla(t10, "WITHIN", now.minus(Duration.ofDays(70)), now.minus(Duration.ofDays(69)));

        Ticket t11 = ticket("ITSM-2026-900011", "SERVICE_REQUEST", "EMAIL", "Shared mailbox for legal notices",
                "Low", "Resolved", manager, manager.getDepartment(), now.minus(Duration.ofDays(18)), admin);
        sla(t11, "WITHIN", now.minus(Duration.ofDays(18)), now.minus(Duration.ofDays(16)));

        Ticket t12 = ticket("ITSM-2026-900012", "INCIDENT", "NETWORK", "Wi-Fi dead on 3rd floor HO",
                "Medium", "In Progress", hod, hod.getDepartment(), now.minus(Duration.ofDays(3)), admin);
        sla(t12, "WITHIN", now.minus(Duration.ofDays(3)), null);

        Ticket t13 = ticket("ITSM-2026-900013", "INCIDENT", "OTHER", "Printer jam in operations hub",
                "Low", "Rejected", finance, finance.getDepartment(), now.minus(Duration.ofDays(25)), null);

        ticket("ITSM-2026-900014", "SERVICE_REQUEST", "APPLICATION", "New report pack for monthly ALCO",
                "Medium", "Approved", manager, manager.getDepartment(), now.minus(Duration.ofDays(4)), null);
    }

    private Ticket ticket(String number, String typeCode, String catCode, String subject, String priority,
                          String status, Employee requester, Department dept, Instant created, Employee implementor) {
        TicketType type = ticketTypeRepository.findByCode(typeCode).orElse(null);
        Category cat = categoryRepository.findByCode(catCode).orElse(null);
        SubCategory sub = cat == null ? null : firstSub(cat);
        Ticket t = new Ticket();
        t.setPublicNumber(number);
        t.setTicketType(type);
        t.setCategory(cat);
        t.setSubCategory(sub);
        t.setSubject(subject);
        t.setDescription(subject + ". Seeded for H2 dashboard and reports preview.");
        t.setPriorityCode(priority);
        t.setImpactCode("Medium");
        t.setUrgencyCode(priority);
        t.setConfidentialityCode("Internal");
        t.setRequester(requester);
        t.setDepartment(dept);
        t.setStatusCode(status);
        t.setAssignedImplementor(implementor);
        t.setMajorIncident("Critical".equals(priority));
        t.setCreatedAtUtc(created);
        t.setUpdatedAtUtc(created.plus(Duration.ofHours(2)));
        return ticketRepository.save(t);
    }

    private SubCategory firstSub(Category cat) {
        java.util.List<SubCategory> list = subCategoryRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(cat);
        return list.isEmpty() ? null : list.get(0);
    }

    private void sla(Ticket ticket, String state, Instant start, Instant resolved) {
        SlaPolicy policy = slaPolicyRepository.findByPriorityCodeAndActiveTrue(ticket.getPriorityCode()).orElse(null);
        if (policy == null) {
            return;
        }
        TicketSla sla = new TicketSla();
        sla.setTicket(ticket);
        sla.setSlaPolicy(policy);
        sla.setSlaStartUtc(start);
        sla.setResponseDueUtc(start.plus(Duration.ofMinutes(policy.getResponseMinutes())));
        sla.setResolveDueUtc(start.plus(Duration.ofMinutes(policy.getResolutionMinutes())));
        sla.setStateCode(state);
        sla.setPaused(false);
        sla.setResolvedUtc(resolved);
        if (resolved != null) {
            sla.setFirstResponseUtc(start.plus(Duration.ofMinutes(20)));
        }
        ticketSlaRepository.save(sla);
    }

    private Employee employee(String no, String sam, String name, String desig, Department dept, Employee manager, Employee hod) {
        Employee emp = new Employee();
        emp.setEmployeeNo(no);
        emp.setSamAccountName(sam);
        emp.setDisplayName(name);
        emp.setDesignation(desig);
        emp.setEmail(sam + "@localhost");
        emp.setDepartment(dept);
        emp.setManager(manager);
        emp.setHod(hod);
        emp.setPortalActive(true);
        return employeeRepository.save(emp);
    }

    private void grant(Employee emp, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElse(null);
        if (role == null) {
            return;
        }
        if (assignmentRepository.findByEmployeeAndRole(emp, role).isPresent()) {
            return;
        }
        EmployeeRoleAssignment row = new EmployeeRoleAssignment();
        row.setEmployee(emp);
        row.setRole(role);
        row.setAssignedAtUtc(TimeUtc.now());
        assignmentRepository.save(row);
    }

    private void addToGroup(Employee emp, String groupCode) {
        AssignmentGroup group = groupRepository.findByCode(groupCode).orElse(null);
        if (group == null || groupMemberRepository.existsByAssignmentGroupAndEmployee(group, emp)) {
            return;
        }
        AssignmentGroupMember m = new AssignmentGroupMember();
        m.setAssignmentGroup(group);
        m.setEmployee(emp);
        groupMemberRepository.save(m);
    }
}
