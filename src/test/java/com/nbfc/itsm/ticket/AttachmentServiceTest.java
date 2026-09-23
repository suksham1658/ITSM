package com.nbfc.itsm.ticket;

import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.Ticket;
import com.nbfc.itsm.seed.CatalogSeedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AttachmentServiceTest {

    @Autowired
    private CatalogSeedService catalogSeedService;
    @Autowired
    private AttachmentService attachmentService;

    @BeforeEach
    void seed() {
        catalogSeedService.ensureSeeded();
    }

    @Test
    void executableExtensionIsRejected() {
        Ticket ticket = new Ticket();
        ticket.setTicketId(1L);
        Employee uploader = new Employee();
        MockMultipartFile file = new MockMultipartFile("file", "payload.exe",
                "application/octet-stream", new byte[]{1, 2, 3});
        com.nbfc.itsm.exception.ItsmException ex = assertThrows(com.nbfc.itsm.exception.ItsmException.class,
                () -> attachmentService.store(ticket, uploader, file));
        assertEquals("ATTACHMENT_EXECUTABLE", ex.getCode());
    }

    @Test
    void emptyFileIsRejected() {
        Ticket ticket = new Ticket();
        ticket.setTicketId(1L);
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", new byte[0]);
        com.nbfc.itsm.exception.ItsmException ex = assertThrows(com.nbfc.itsm.exception.ItsmException.class,
                () -> attachmentService.store(ticket, new Employee(), file));
        assertEquals("ATTACHMENT_EMPTY", ex.getCode());
    }
}
