package com.nbfc.itsm.admin;

import com.nbfc.itsm.domain.AssignmentGroup;
import com.nbfc.itsm.domain.AssignmentGroupMemberRepository;
import com.nbfc.itsm.domain.AssignmentGroupRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.RoleRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.identity.PortalUserService;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.seed.CatalogSeedService;
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

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EmployeeSetupServiceTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EmployeeSetupService setupService;
    @Autowired
    private CatalogSeedService catalogSeedService;
    @Autowired
    private PortalUserService portalUserService;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private EmployeeRoleAssignmentRepository assignmentRepository;
    @Autowired
    private AssignmentGroupRepository groupRepository;
    @Autowired
    private AssignmentGroupMemberRepository memberRepository;

    private ItsmUserPrincipal sysAdmin;
    private Employee alice;
    private Employee bob;

    @BeforeEach
    void setUp() {
        catalogSeedService.ensureSeeded();
        sysAdmin = portalUserService.toPrincipal(employee("E-SU-SYS", "Setup SysAdmin", "SYSTEM_ADMINISTRATOR"));
        alice = employee("E-SU-ALI", "Setup Alice", "EMPLOYEE");
        bob = employee("E-SU-BOB", "Setup Bob", "EMPLOYEE");
        actAs(sysAdmin);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void setsManagerAndRefusesLoopsAndSelf() {
        setupService.updateReportingLine(alice.getEmployeeId(), bob.getEmployeeId(), null, null, sysAdmin);
        assertEquals(bob.getEmployeeId(), employeeRepository.findById(alice.getEmployeeId()).get().getManager().getEmployeeId());

        ItsmException loop = assertThrows(ItsmException.class,
                () -> setupService.updateReportingLine(bob.getEmployeeId(), alice.getEmployeeId(), null, null, sysAdmin));
        assertTrue(loop.getMessage().contains("loop"), loop.getMessage());

        ItsmException self = assertThrows(ItsmException.class,
                () -> setupService.updateReportingLine(alice.getEmployeeId(), alice.getEmployeeId(), null, null, sysAdmin));
        assertTrue(self.getMessage().contains("own manager"));
    }

    @Test
    void inactiveManagerIsRefused() {
        bob.setPortalActive(false);
        ItsmException ex = assertThrows(ItsmException.class,
                () -> setupService.updateReportingLine(alice.getEmployeeId(), bob.getEmployeeId(), null, null, sysAdmin));
        assertTrue(ex.getMessage().contains("active portal access"));
    }

    @Test
    void groupMembershipIsReplaced() {
        AssignmentGroup desk = groupRepository.findByCode("IT_SERVICE_DESK").orElseThrow(IllegalStateException::new);
        AssignmentGroup impl = groupRepository.findByCode("IT_IMPLEMENTORS").orElseThrow(IllegalStateException::new);
        setupService.updateGroups(alice.getEmployeeId(), Arrays.asList(desk.getAssignmentGroupId(), impl.getAssignmentGroupId()), sysAdmin);
        assertTrue(memberRepository.existsByAssignmentGroupAndEmployee(desk, alice));
        assertTrue(memberRepository.existsByAssignmentGroupAndEmployee(impl, alice));

        setupService.updateGroups(alice.getEmployeeId(), Collections.singletonList(impl.getAssignmentGroupId()), sysAdmin);
        assertFalse(memberRepository.existsByAssignmentGroupAndEmployee(desk, alice));
        assertTrue(memberRepository.existsByAssignmentGroupAndEmployee(impl, alice));
    }

    @Test
    void onlySystemAdministratorCanChangeSetup() {
        ItsmUserPrincipal itAdmin = portalUserService.toPrincipal(employee("E-SU-ITA", "Setup IT Admin", "IT_ADMIN"));
        actAs(itAdmin);
        assertThrows(AccessDeniedException.class,
                () -> setupService.updateReportingLine(alice.getEmployeeId(), bob.getEmployeeId(), null, null, itAdmin));
        assertThrows(AccessDeniedException.class,
                () -> setupService.updateGroups(alice.getEmployeeId(), Collections.<Long>emptyList(), itAdmin));
    }

    @Test
    void userPageShowsSetupAndWarnsWhenNoManager() throws Exception {
        mockMvc.perform(get("/admin/users/{id}", alice.getEmployeeId())
                        .with(authentication(new UsernamePasswordAuthenticationToken(sysAdmin, null, sysAdmin.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Save reporting line")))
                .andExpect(content().string(containsString("Save groups")))
                .andExpect(content().string(containsString("No manager set")));
    }

    private Employee employee(String no, String name, String roleCode) {
        Employee e = new Employee();
        e.setEmployeeNo(no);
        e.setSamAccountName(no.toLowerCase());
        e.setDisplayName(name);
        e.setPortalActive(true);
        e = employeeRepository.save(e);
        EmployeeRoleAssignment row = new EmployeeRoleAssignment();
        row.setEmployee(e);
        row.setRole(roleRepository.findByCode(roleCode).orElseThrow(() -> new IllegalStateException(roleCode)));
        assignmentRepository.save(row);
        return e;
    }

    private static void actAs(ItsmUserPrincipal p) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }
}
