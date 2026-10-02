package com.nbfc.itsm.sla;

import com.nbfc.itsm.domain.AssignmentGroupMember;
import com.nbfc.itsm.domain.AssignmentGroupMemberRepository;
import com.nbfc.itsm.domain.AssignmentGroupRepository;
import com.nbfc.itsm.domain.Category;
import com.nbfc.itsm.domain.CategoryRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.Holiday;
import com.nbfc.itsm.domain.HolidayRepository;
import com.nbfc.itsm.domain.NotificationRepository;
import com.nbfc.itsm.domain.RoleRepository;
import com.nbfc.itsm.domain.SlaPolicy;
import com.nbfc.itsm.domain.SlaPolicyRepository;
import com.nbfc.itsm.domain.SubCategoryRepository;
import com.nbfc.itsm.domain.Ticket;
import com.nbfc.itsm.domain.TicketCommentRepository;
import com.nbfc.itsm.domain.TicketRepository;
import com.nbfc.itsm.domain.TicketSla;
import com.nbfc.itsm.domain.TicketSlaRepository;
import com.nbfc.itsm.domain.TicketTypeRepository;
import com.nbfc.itsm.identity.PortalUserService;
import com.nbfc.itsm.notification.TicketEmailEvent;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.seed.CatalogSeedService;
import com.nbfc.itsm.ticket.TicketForm;
import com.nbfc.itsm.ticket.TicketService;
import com.nbfc.itsm.util.TimeUtc;
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SLA: business time, real pause, met vs late, late response, re-open window, priority change, monitor, admin page. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
class SlaCompleteTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationEvents events;
    @Autowired private CatalogSeedService catalogSeedService;
    @Autowired private SlaService slaService;
    @Autowired private SlaMonitorService monitor;
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
    @Autowired private TicketSlaRepository slaRepository;
    @Autowired private SlaPolicyRepository policyRepository;
    @Autowired private HolidayRepository holidayRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private TicketCommentRepository commentRepository;

    private Employee requester;
    private Employee desk;
    private Employee impl;
    private Employee admin;

    @BeforeEach
    void setUp() {
        catalogSeedService.ensureSeeded();
        requester = employee("E-SL-REQ", "Sana Requester", null, "EMPLOYEE");
        desk = employee("E-SL-SD", "Deep Desk", "IT_SERVICE_DESK", "EMPLOYEE", "IT_SERVICE_DESK");
        impl = employee("E-SL-IM", "Ira Implementor", "IT_IMPLEMENTORS", "EMPLOYEE", "IT_IMPLEMENTOR");
        admin = employee("E-SL-SA", "Sam Admin", null, "EMPLOYEE", "SYSTEM_ADMINISTRATOR");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void businessTimeSkipsEveningsWeekendsAndHolidays() {
        Holiday h = new Holiday();
        h.setHolidayDate(LocalDate.of(2026, 10, 2));
        h.setName("Gandhi Jayanti");
        holidayRepository.save(h);
        Instant thu4pm = LocalDateTime.of(2026, 10, 1, 16, 0).atZone(IST).toInstant();
        Instant wed1pm = LocalDateTime.of(2026, 10, 7, 13, 0).atZone(IST).toInstant();
        assertEquals(wed1pm, slaService.addMinutes(thu4pm, 1440, false), "24 working hours from Thu 16:00 = Wed 13:00");
        assertEquals(1440, slaService.businessMinutesBetween(thu4pm, wed1pm, false));
        assertEquals(0, slaService.businessMinutesBetween(
                LocalDateTime.of(2026, 10, 3, 10, 0).atZone(IST).toInstant(),
                LocalDateTime.of(2026, 10, 4, 17, 0).atZone(IST).toInstant(), false), "weekend counts nothing");
    }

    @Test
    void holdTimeIsAddedToTheDueTimes() {
        allHours("Medium");
        Ticket t = assigned();
        TicketSla sla = clock(t);
        Instant dueBefore = sla.getResolveDueUtc();
        ticketService.applyAction(as(impl), t.getTicketId(), "HOLD", "Waiting for the vendor to ship", null);
        sla = clock(t);
        assertTrue(sla.isPaused());
        sla.setPausedAtUtc(TimeUtc.now().minus(Duration.ofHours(3))); // pretend it has been on hold for 3 hours
        slaRepository.save(sla);
        ticketService.applyAction(as(impl), t.getTicketId(), "REASSIGN", "Vendor engineer will take over", impl.getEmployeeId());
        sla = clock(t);
        assertFalse(sla.isPaused());
        assertTrue(sla.getPausedMinutes() >= 179 && sla.getPausedMinutes() <= 181, "about 180 minutes on hold");
        long moved = Duration.between(dueBefore, sla.getResolveDueUtc()).toMinutes();
        assertTrue(moved >= 179 && moved <= 181, "resolve due moved by the hold time, was " + moved);
    }

    @Test
    void lateResolutionIsBreachedNotMetAndOnTimeIsMet() {
        Ticket late = assigned();
        TicketSla s = clock(late);
        s.setResolveDueUtc(TimeUtc.now().minus(Duration.ofMinutes(5)));
        slaRepository.save(s);
        ticketService.applyAction(as(impl), late.getTicketId(), "RESOLVE", "Fixed after the deadline", null);
        assertEquals("BREACHED", clock(late).getStateCode());
        assertNotNull(clock(late).getResolvedUtc());

        Ticket onTime = assigned();
        ticketService.applyAction(as(impl), onTime.getTicketId(), "RESOLVE", "Fixed well in time", null);
        assertEquals("MET", clock(onTime).getStateCode());
    }

    @Test
    void lateFirstResponseIsFlagged() {
        Ticket t = raise();
        TicketSla s = clock(t);
        s.setResponseDueUtc(TimeUtc.now().minus(Duration.ofMinutes(1)));
        slaRepository.save(s);
        ticketService.applyAction(as(desk), t.getTicketId(), "ASSIGN", null, impl.getEmployeeId());
        assertTrue(clock(t).isResponseBreached());
    }

    @Test
    void notResolvedGivesAFreshResolutionWindow() {
        allHours("Medium");
        Ticket t = assigned();
        TicketSla s = clock(t);
        s.setResolveDueUtc(TimeUtc.now().minus(Duration.ofHours(1)));
        slaRepository.save(s);
        ticketService.applyAction(as(impl), t.getTicketId(), "RESOLVE", "Restarted the service", null);
        ticketService.applyAction(as(requester), t.getTicketId(), "SEND_BACK", "Still failing every morning", null);
        s = clock(t);
        assertEquals("WITHIN", s.getStateCode(), "fresh window, not breached straight away");
        int resolution = policyRepository.findByPriorityCodeAndActiveTrue("Medium").get().getResolutionMinutes();
        long left = Duration.between(TimeUtc.now(), s.getResolveDueUtc()).toMinutes();
        assertTrue(left >= resolution - 2 && left <= resolution, "new due about one full window away");
    }

    @Test
    void priorityChangeRecalculatesTheSla() throws Exception {
        allHours("Critical");
        allHours("Medium");
        Ticket t = raise();
        Instant start = clock(t).getSlaStartUtc();
        assertThrows(AccessDeniedException.class,
                () -> ticketService.changePriority(as(requester), t.getTicketId(), "Critical", "Whole branch is down now"));
        mockMvc.perform(post("/tickets/{id}/priority", t.getTicketId()).param("priority", "Critical")
                        .param("remarks", "Whole branch is down now").with(csrf()).with(authentication(token(desk))))
                .andExpect(flash().attribute("message", containsString("SLA due times recalculated")));
        TicketSla s = clock(t);
        assertEquals("Critical", s.getSlaPolicy().getPriorityCode());
        int critical = policyRepository.findByPriorityCodeAndActiveTrue("Critical").get().getResolutionMinutes();
        assertEquals(start.plusSeconds(critical * 60L), s.getResolveDueUtc());
        assertTrue(commentRepository.findByTicketOrderByCreatedAtUtcAsc(ticketRepository.findById(t.getTicketId()).get())
                .stream().anyMatch(c -> c.getBody().startsWith("Priority changed from Medium to Critical")));
    }

    @Test
    void monitorSavesTheStateAndAlertsOncePerLevel() {
        allHours("Medium");
        Ticket t = assigned();
        TicketSla s = clock(t);
        // 10% of the window left: at risk.
        Instant now = TimeUtc.now();
        s.setSlaStartUtc(now.minus(Duration.ofMinutes(900)));
        s.setResolveDueUtc(now.plus(Duration.ofMinutes(100)));
        slaRepository.save(s);
        assertEquals(1, monitor.run(now));
        assertEquals("NEAR", clock(t).getStateCode(), "saved, not only shown");
        assertEquals(0, monitor.run(now), "no second NEAR alert");

        s = clock(t);
        s.setResolveDueUtc(now.minus(Duration.ofMinutes(1)));
        slaRepository.save(s);
        assertEquals(1, monitor.run(now));
        assertEquals("BREACHED", clock(t).getStateCode());
        assertTrue(events.stream(TicketEmailEvent.class).anyMatch(e -> e.getKind() == TicketEmailEvent.Kind.SLA_BREACHED
                && e.getRecipientIds().contains(impl.getEmployeeId()) && e.getRecipientIds().contains(desk.getEmployeeId())),
                "implementor and service desk e-mailed");
        assertTrue(notificationRepository.findByRecipientIdAndTicketId(impl.getEmployeeId(), t.getTicketId()).stream()
                .anyMatch(n -> n.getTitle().startsWith("SLA breached")));
        assertTrue(events.stream(TicketEmailEvent.class).noneMatch(e -> e.getRecipientIds().contains(requester.getEmployeeId())
                && (e.getKind() == TicketEmailEvent.Kind.SLA_NEAR || e.getKind() == TicketEmailEvent.Kind.SLA_BREACHED)),
                "never the requester");
    }

    @Test
    void systemAdministratorEditsTargetsHoursAndHolidays() throws Exception {
        SlaPolicy high = policyRepository.findByPriorityCodeAndActiveTrue("High").get();
        mockMvc.perform(get("/admin/sla").with(authentication(token(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Targets per priority")))
                .andExpect(content().string(containsString("Save working hours")))
                .andExpect(content().string(containsString("Add holiday")));
        mockMvc.perform(post("/admin/sla/policies/{id}", high.getSlaPolicyId()).param("responseMinutes", "45")
                        .param("resolutionMinutes", "600").with(csrf()).with(authentication(token(admin))))
                .andExpect(flash().attribute("message", containsString("saved")));
        assertEquals(600, policyRepository.findById(high.getSlaPolicyId()).get().getResolutionMinutes());
        mockMvc.perform(post("/admin/sla/policies/{id}", high.getSlaPolicyId()).param("responseMinutes", "700")
                        .param("resolutionMinutes", "600").with(csrf()).with(authentication(token(admin))))
                .andExpect(flash().attribute("errorMessage", containsString("cannot be shorter")));

        String[] start = {"09:30", "09:30", "09:30", "09:30", "09:30", "10:00", "10:00"};
        String[] end = {"18:30", "18:30", "18:30", "18:30", "18:30", "14:00", "14:00"};
        mockMvc.perform(post("/admin/sla/calendar").param("workingDays", "1", "2", "3", "4", "5", "6")
                        .param("start", start).param("end", end).with(csrf()).with(authentication(token(admin))))
                .andExpect(flash().attribute("message", containsString("Working hours saved")));

        mockMvc.perform(post("/admin/sla/holidays").param("date", "2026-11-09").param("name", "Diwali")
                        .with(csrf()).with(authentication(token(admin))))
                .andExpect(flash().attribute("message", containsString("added")));
        assertTrue(holidayRepository.existsByHolidayDate(LocalDate.of(2026, 11, 9)));

        mockMvc.perform(post("/admin/sla/holidays").param("date", "2026-11-10").param("name", "Not allowed")
                        .with(csrf()).with(authentication(token(desk))))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ helpers

    private void allHours(String priority) {
        SlaPolicy p = policyRepository.findByPriorityCodeAndActiveTrue(priority).get();
        p.setAllHours(true);
        policyRepository.save(p);
    }

    private Ticket raise() {
        Category net = categoryRepository.findByCode("NETWORK").orElseThrow(IllegalStateException::new);
        TicketForm f = new TicketForm();
        f.setTicketTypeId(ticketTypeRepository.findByCode("INCIDENT").orElseThrow(IllegalStateException::new).getTicketTypeId());
        f.setCategoryId(net.getCategoryId());
        f.setSubCategoryId(subCategoryRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(net).get(0).getSubCategoryId());
        f.setSubject("Branch network is slow");
        f.setDescription("Everything on the branch network is very slow since 10am.");
        f.setPriorityCode("Medium");
        f.setIntent("submit");
        return ticketService.save(as(requester), f);
    }

    private Ticket assigned() {
        Ticket t = raise();
        ticketService.applyAction(as(desk), t.getTicketId(), "ASSIGN", null, impl.getEmployeeId());
        return t;
    }

    private TicketSla clock(Ticket t) {
        return slaRepository.findByTicket(ticketRepository.findById(t.getTicketId()).get()).get();
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
            m.setAssignmentGroup(groupRepository.findByCode(groupCode).orElseThrow(() -> new IllegalStateException(groupCode)));
            m.setEmployee(e);
            memberRepository.save(m);
        }
        return e;
    }
}
