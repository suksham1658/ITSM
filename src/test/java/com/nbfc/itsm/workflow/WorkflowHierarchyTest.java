package com.nbfc.itsm.workflow;

import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.Role;
import com.nbfc.itsm.domain.RoleRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.seed.CatalogSeedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WorkflowHierarchyTest {

    @Autowired
    private CatalogSeedService catalogSeedService;
    @Autowired
    private WorkflowEngine workflowEngine;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private EmployeeRoleAssignmentRepository assignmentRepository;

    @BeforeEach
    void seed() {
        catalogSeedService.ensureSeeded();
    }

    @Test
    void hodOfSelfHasNoManagerHops() {
        Employee hod = employee("hod.chain");
        hod.setHod(hod);
        hod.setManager(hod);
        hod = employeeRepository.save(hod);
        grant(hod, "HOD");
        List<Employee> hops = workflowEngine.managerHops(hod);
        assertTrue(hops.isEmpty());
    }

    @Test
    void hopsWalkUntilHod() {
        Employee hod = employee("hod.top");
        hod.setHod(hod);
        hod = employeeRepository.save(hod);
        grant(hod, "HOD");
        Employee manager = employee("mgr.mid");
        manager.setManager(hod);
        manager.setHod(hod);
        manager = employeeRepository.save(manager);
        Employee emp = employee("emp.leaf");
        emp.setManager(manager);
        emp.setHod(hod);
        emp = employeeRepository.save(emp);
        List<Employee> hops = workflowEngine.managerHops(emp);
        assertEquals(2, hops.size());
        assertEquals(manager.getEmployeeId(), hops.get(0).getEmployeeId());
        assertEquals(hod.getEmployeeId(), hops.get(1).getEmployeeId());
    }

    @Test
    void missingManagerFailsClosed() {
        Employee emp = employee("no.mgr");
        emp.setManager(null);
        emp.setHod(null);
        final Employee orphan = employeeRepository.save(emp);
        ItsmException ex = assertThrows(ItsmException.class, () -> workflowEngine.managerHops(orphan));
        assertEquals("NO_MANAGER_CHAIN", ex.getCode());
    }

    private Employee employee(String sam) {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        Employee emp = new Employee();
        emp.setEmployeeNo("E-" + tag);
        emp.setSamAccountName(sam + "." + tag);
        emp.setDisplayName(sam);
        emp.setEmail(sam + tag + "@localhost");
        emp.setPortalActive(true);
        return employeeRepository.save(emp);
    }

    private void grant(Employee emp, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow(() -> new IllegalStateException(roleCode));
        EmployeeRoleAssignment row = new EmployeeRoleAssignment();
        row.setEmployee(emp);
        row.setRole(role);
        assignmentRepository.save(row);
    }
}
