package com.nbfc.itsm.notification;

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
import com.nbfc.itsm.identity.PortalUserService;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.seed.CatalogSeedService;
import com.nbfc.itsm.ticket.TicketForm;
import com.nbfc.itsm.ticket.TicketService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Requester e-mails: only on submit and on close; content and addressing. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
class TicketEmailTest {

    @MockBean private JavaMailSender mailSender;
    @Autowired private ApplicationEvents applicationEvents;
    @Autowired private TicketEmailService ticketEmailService;
    @Autowired private CatalogSeedService catalogSeedService;
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

    private Employee requester;
    private Employee desk;
    private Employee impl;

    @BeforeEach
    void setUp() {
        catalogSeedService.ensureSeeded();
        requester = employee("E-EM-REQ", "Mail Requester", "mail.requester@authum.com", null, null, "EMPLOYEE");
        desk = employee("E-EM-SD", "Mail Desk", null, null, "IT_SERVICE_DESK", "EMPLOYEE", "IT_SERVICE_DESK");
        impl = employee("E-EM-IM", "Mail Implementor", null, null, "IT_IMPLEMENTORS", "EMPLOYEE", "IT_IMPLEMENTOR");
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void mailEventsOnlyOnSubmitAndOnClose() {
        TicketForm draft = incident();
        draft.setIntent("draft");
        ticketService.save(as(requester), draft);
        assertEquals(0, kinds().size(), "no mail for a draft");

        Ticket t = ticketService.save(as(requester), incident());
        assertEquals(java.util.Collections.singletonList("CREATED:" + t.getTicketId()), kinds());

        ticketService.applyAction(as(desk), t.getTicketId(), "ASSIGN", "Please check the laptop", impl.getEmployeeId());
        ticketService.applyAction(as(impl), t.getTicketId(), "START", null, null);
        ticketService.applyAction(as(impl), t.getTicketId(), "RESOLVE", "Replaced the faulty RAM module", null);
        assertEquals(1, kinds().size(), "nothing in between");

        ticketService.applyAction(as(requester), t.getTicketId(), "APPROVE", "Working fine now, thank you", null);
        assertEquals("Closed", ticketRepository.findById(t.getTicketId()).get().getStatusCode());
        assertEquals(java.util.Arrays.asList("CREATED:" + t.getTicketId(), "CLOSED:" + t.getTicketId()), kinds(),
                "closed mail even though the requester closed it");
    }

    @Test
    void rejectedServiceRequestGetsNoClosedMail() {
        Employee manager = employee("E-EM-MGR", "Mail Manager", null, null, null, "EMPLOYEE", "HOD");
        requester.setManager(manager);
        employeeRepository.save(requester);
        Ticket sr = ticketService.save(as(requester), serviceRequest());
        ticketService.applyAction(as(manager), sr.getTicketId(), "REJECT", "Not needed for this role", null);
        assertEquals(java.util.Collections.singletonList("CREATED:" + sr.getTicketId()), kinds());
    }

    @Test
    void mailGoesToTheRequesterWithTheTicketDetails() throws Exception {
        Ticket t = ticketService.save(as(requester), incident());
        assertTrue(ticketEmailService.send(t.getTicketId(), TicketEmailEvent.Kind.CREATED));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        MimeMessage m = sent.getValue();
        assertEquals("mail.requester@authum.com", ((InternetAddress) m.getAllRecipients()[0]).getAddress());
        assertEquals("no-reply@authum.com", ((InternetAddress) m.getFrom()[0]).getAddress());
        assertTrue(m.getSubject().startsWith("Incident " + t.getPublicNumber() + " created:"), m.getSubject());
        String html = (String) m.getContent();
        assertTrue(html.contains("Dear Mail Requester") && html.contains(t.getPublicNumber())
                && html.contains("Laptop not starting"), html);

        assertTrue(ticketEmailService.subject(t, TicketEmailEvent.Kind.CLOSED).contains(" closed: "));
    }

    @Test
    void requesterWithoutEmailIsSkipped() throws Exception {
        requester.setEmail(null);
        employeeRepository.save(requester);
        Ticket t = ticketService.save(as(requester), incident());
        assertFalse(ticketEmailService.send(t.getTicketId(), TicketEmailEvent.Kind.CREATED));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // ------------------------------------------------------------------ helpers

    private List<String> kinds() {
        return applicationEvents.stream(TicketEmailEvent.class)
                .map(e -> e.getKind() + ":" + e.getTicketId()).collect(Collectors.toList());
    }

    private TicketForm incident() {
        Category hw = categoryRepository.findByCode("HARDWARE").orElseThrow(IllegalStateException::new);
        TicketForm f = new TicketForm();
        f.setTicketTypeId(ticketTypeRepository.findByCode("INCIDENT").orElseThrow(IllegalStateException::new).getTicketTypeId());
        f.setCategoryId(hw.getCategoryId());
        f.setSubCategoryId(subCategoryRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(hw).get(0).getSubCategoryId());
        f.setSubject("Laptop not starting");
        f.setDescription("The laptop does not power on since this morning.");
        f.setPriorityCode("High");
        f.setIntent("submit");
        return f;
    }

    private TicketForm serviceRequest() {
        Category network = categoryRepository.findByCode("NETWORK").orElseThrow(IllegalStateException::new);
        TicketForm f = new TicketForm();
        f.setTicketTypeId(ticketTypeRepository.findByCode("SERVICE_REQUEST").orElseThrow(IllegalStateException::new).getTicketTypeId());
        f.setCategoryId(network.getCategoryId());
        f.setSubCategoryId(subCategoryRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(network).get(0).getSubCategoryId());
        f.setSubject("VPN access for remote work");
        f.setDescription("Need VPN access to work from the branch office.");
        f.setIntent("submit");
        return f;
    }

    private ItsmUserPrincipal as(Employee e) {
        ItsmUserPrincipal p = portalUserService.toPrincipal(employeeRepository.findById(e.getEmployeeId()).get());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
        return p;
    }

    private Employee employee(String no, String name, String email, Employee manager, String groupCode, String... roles) {
        Employee e = new Employee();
        e.setEmployeeNo(no);
        e.setSamAccountName(no.toLowerCase());
        e.setDisplayName(name);
        e.setEmail(email);
        e.setManager(manager);
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
