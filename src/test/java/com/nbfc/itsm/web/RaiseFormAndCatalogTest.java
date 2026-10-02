package com.nbfc.itsm.web;

import com.nbfc.itsm.domain.Category;
import com.nbfc.itsm.domain.CategoryRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.RoleRepository;
import com.nbfc.itsm.domain.SubCategory;
import com.nbfc.itsm.domain.SubCategoryRepository;
import com.nbfc.itsm.domain.Ticket;
import com.nbfc.itsm.domain.TicketAttachmentRepository;
import com.nbfc.itsm.domain.TicketRepository;
import com.nbfc.itsm.domain.TicketType;
import com.nbfc.itsm.domain.TicketTypeRepository;
import com.nbfc.itsm.domain.WorkflowRule;
import com.nbfc.itsm.domain.WorkflowRuleRepository;
import com.nbfc.itsm.identity.PortalUserService;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.seed.CatalogSeedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Raise Request: Serial Number for Hardware (instead of Confidentiality), optional attachment with PDFs up
 * to 5 MB; Admin &gt; Categories: System Administrator adds, renames and deletes ticket types, categories and
 * sub-categories.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RaiseFormAndCatalogTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CatalogSeedService catalogSeedService;
    @Autowired private PortalUserService portalUserService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private EmployeeRoleAssignmentRepository assignmentRepository;
    @Autowired private TicketTypeRepository ticketTypeRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SubCategoryRepository subCategoryRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private TicketAttachmentRepository attachmentRepository;
    @Autowired private WorkflowRuleRepository ruleRepository;

    private Employee requester;
    private Employee admin;

    @BeforeEach
    void setUp() {
        catalogSeedService.ensureSeeded();
        requester = employee("E-RF-REQ", "Form Requester", "EMPLOYEE");
        admin = employee("E-RF-SA", "Catalog Admin", "EMPLOYEE", "SYSTEM_ADMINISTRATOR");
    }

    // ------------------------------------------------------------------ serial number

    @Test
    void raisePageHasSerialNumberInsteadOfConfidentialityAndAnAttachmentField() throws Exception {
        mockMvc.perform(get("/tickets/raise").with(authentication(token(requester))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"serialGroup\"")))
                .andExpect(content().string(containsString("placeholder=\"Enter Serial number\"")))
                .andExpect(content().string(containsString("Not Available")))
                .andExpect(content().string(containsString("name=\"attachment\"")))
                .andExpect(content().string(containsString("enctype=\"multipart/form-data\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("name=\"confidentialityCode\""))));
    }

    @Test
    void hardwareNeedsASerialNumberOrNotAvailable() throws Exception {
        long before = ticketRepository.count();
        mockMvc.perform(raise("HARDWARE").with(csrf()).with(authentication(token(requester))))
                .andExpect(flash().attribute("errorMessage", containsString("Serial number is required")));
        mockMvc.perform(raise("HARDWARE").param("serialMode", "ENTER").param("serialNumber", " ")
                        .with(csrf()).with(authentication(token(requester))))
                .andExpect(flash().attribute("errorMessage", containsString("Serial number")));
        assertEquals(before, ticketRepository.count(), "nothing saved");

        mockMvc.perform(raise("HARDWARE").param("serialMode", "ENTER").param("serialNumber", "  PF-3XK9 21 ")
                        .with(csrf()).with(authentication(token(requester))))
                .andExpect(flash().attribute("message", containsString("submitted")));
        assertEquals("PF-3XK9 21", newest().getSerialNumber());

        mockMvc.perform(raise("HARDWARE").param("serialMode", "NA").with(csrf()).with(authentication(token(requester))))
                .andExpect(flash().attribute("message", containsString("submitted")));
        assertEquals("Not available", newest().getSerialNumber());
    }

    @Test
    void otherCategoriesNeverStoreASerialNumber() throws Exception {
        mockMvc.perform(raise("NETWORK").param("serialMode", "ENTER").param("serialNumber", "IGNORED-1")
                        .with(csrf()).with(authentication(token(requester))))
                .andExpect(flash().attribute("message", containsString("submitted")));
        Ticket t = newest();
        assertNull(t.getSerialNumber());
        assertEquals("Normal", t.getConfidentialityCode(), "confidentiality defaults when not on the form");
    }

    // ------------------------------------------------------------------ attachment

    @Test
    void pdfOverFiveMegabytesIsRefusedAndNothingIsSaved() throws Exception {
        long before = ticketRepository.count();
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        mockMvc.perform(raise("NETWORK").file(new MockMultipartFile("attachment", "scan.pdf", "application/pdf", big))
                        .with(csrf()).with(authentication(token(requester))))
                .andExpect(flash().attribute("errorMessage", containsString("PDF files must not exceed 5 MB")));
        assertEquals(before, ticketRepository.count());
    }

    @Test
    void smallPdfIsStoredWithTheNewTicket() throws Exception {
        mockMvc.perform(raise("NETWORK").file(new MockMultipartFile("attachment", "quote.pdf", "application/pdf",
                                "%PDF-1.4 test".getBytes("UTF-8")))
                        .with(csrf()).with(authentication(token(requester))))
                .andExpect(flash().attribute("message", containsString("submitted")));
        Ticket t = newest();
        assertEquals(1, attachmentRepository.findAll().stream()
                .filter(a -> a.getTicket().getTicketId().equals(t.getTicketId()) && "quote.pdf".equals(a.getOriginalName())).count());
    }

    @Test
    void aRenamedFileCalledPdfIsRefused() throws Exception {
        long before = ticketRepository.count();
        mockMvc.perform(raise("NETWORK").file(new MockMultipartFile("attachment", "invoice.pdf", "application/pdf",
                                "<html><script>alert(1)</script></html>".getBytes("UTF-8")))
                        .with(csrf()).with(authentication(token(requester))))
                .andExpect(flash().attribute("errorMessage", containsString("not a real PDF")));
        assertEquals(before, ticketRepository.count());
    }

    @Test
    void noFileIsFine() throws Exception {
        mockMvc.perform(raise("NETWORK").file(new MockMultipartFile("attachment", "", "application/octet-stream", new byte[0]))
                        .with(csrf()).with(authentication(token(requester))))
                .andExpect(flash().attribute("message", containsString("submitted")));
    }

    // ------------------------------------------------------------------ catalog admin

    @Test
    void systemAdministratorAddsRenamesAndDeletesCatalogEntries() throws Exception {
        mockMvc.perform(post("/admin/catalog/types").param("name", "Onboarding Request").with(csrf()).with(authentication(token(admin))))
                .andExpect(flash().attribute("message", containsString("added")));
        TicketType type = ticketTypeRepository.findByCode("ONBOARDING_REQUEST").orElseThrow(IllegalStateException::new);
        assertTrue(type.isActive());

        WorkflowRule rule = ruleRepository.findAllByOrderByPriorityAsc().get(0);
        rule.setConditionJson("{\"ticket_type\":[\"Onboarding Request\"]}");
        ruleRepository.save(rule);
        mockMvc.perform(post("/admin/catalog/types/{id}/rename", type.getTicketTypeId()).param("name", "Joiner Request")
                        .with(csrf()).with(authentication(token(admin))))
                .andExpect(flash().attribute("message", containsString("1 workflow rule(s) updated")));
        assertEquals("Joiner Request", ticketTypeRepository.findById(type.getTicketTypeId()).get().getName());
        assertTrue(ruleRepository.findById(rule.getWorkflowRuleId()).get().getConditionJson().contains("Joiner Request"));

        mockMvc.perform(post("/admin/catalog/types/{id}/delete", type.getTicketTypeId()).with(csrf()).with(authentication(token(admin))))
                .andExpect(flash().attribute("message", containsString("removed")));
        assertFalse(ticketTypeRepository.findById(type.getTicketTypeId()).get().isActive());

        mockMvc.perform(post("/admin/catalog/categories").param("name", "Printers").with(csrf()).with(authentication(token(admin))));
        Category printers = categoryRepository.findByCode("PRINTERS").orElseThrow(IllegalStateException::new);
        mockMvc.perform(post("/admin/catalog/sub-categories").param("categoryId", String.valueOf(printers.getCategoryId()))
                .param("name", "Paper jam").with(csrf()).with(authentication(token(admin))))
                .andExpect(flash().attribute("message", containsString("added")));
        List<SubCategory> subs = subCategoryRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(printers);
        assertEquals("Paper jam", subs.get(0).getName());

        mockMvc.perform(post("/admin/catalog/categories/{id}/delete", printers.getCategoryId()).with(csrf()).with(authentication(token(admin))));
        assertFalse(categoryRepository.findById(printers.getCategoryId()).get().isActive());
        assertTrue(subCategoryRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(printers).isEmpty(), "sub-categories go too");

        mockMvc.perform(get("/admin/categories").with(authentication(token(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Add ticket type")))
                .andExpect(content().string(containsString("Add sub-category")));
    }

    @Test
    void systemEntriesAreLockedAndOthersCannotEditTheCatalog() throws Exception {
        Category hw = categoryRepository.findByCode("HARDWARE").orElseThrow(IllegalStateException::new);
        mockMvc.perform(post("/admin/catalog/categories/{id}/delete", hw.getCategoryId()).with(csrf()).with(authentication(token(admin))))
                .andExpect(flash().attribute("errorMessage", containsString("cannot be renamed or deleted")));
        assertTrue(categoryRepository.findById(hw.getCategoryId()).get().isActive());

        mockMvc.perform(post("/admin/catalog/types").param("name", "Sneaky Type").with(csrf()).with(authentication(token(requester))))
                .andExpect(status().isForbidden());
        assertFalse(ticketTypeRepository.findByCode("SNEAKY_TYPE").isPresent());
    }

    // ------------------------------------------------------------------ helpers

    private MockMultipartHttpServletRequestBuilder raise(String categoryCode) {
        Category c = categoryRepository.findByCode(categoryCode).orElseThrow(IllegalStateException::new);
        SubCategory s = subCategoryRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(c).get(0);
        MockMultipartHttpServletRequestBuilder b = multipart("/tickets/raise");
        b.param("ticketTypeId", String.valueOf(ticketTypeRepository.findByCode("INCIDENT").get().getTicketTypeId()))
                .param("categoryId", String.valueOf(c.getCategoryId()))
                .param("subCategoryId", String.valueOf(s.getSubCategoryId()))
                .param("subject", "Device problem at my desk")
                .param("description", "It stopped working this morning after a restart.")
                .param("priorityCode", "Medium")
                .param("impactCode", "Individual")
                .param("urgencyCode", "Medium")
                .param("intent", "submit");
        return b;
    }

    private Ticket newest() {
        return ticketRepository.findAll().stream()
                .filter(t -> t.getRequester().getEmployeeId().equals(requester.getEmployeeId()))
                .max((a, b) -> a.getTicketId().compareTo(b.getTicketId()))
                .orElseThrow(IllegalStateException::new);
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
