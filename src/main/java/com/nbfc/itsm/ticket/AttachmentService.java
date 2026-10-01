package com.nbfc.itsm.ticket;

import com.nbfc.itsm.config.ItsmProperties;
import com.nbfc.itsm.domain.AttachmentPolicy;
import com.nbfc.itsm.domain.AttachmentPolicyRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.Ticket;
import com.nbfc.itsm.domain.TicketAttachment;
import com.nbfc.itsm.domain.TicketAttachmentRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.util.TimeUtc;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AttachmentService {

    private static final Set<String> BLOCKED_EXT = new HashSet<String>(Arrays.asList(
            "exe", "bat", "cmd", "com", "scr", "pif", "js", "jar", "sh", "msi", "dll", "vbs", "ps1",
            "wsf", "cpl", "msc", "hta", "apk"));

    private final AttachmentPolicyRepository policyRepository;
    private final TicketAttachmentRepository attachmentRepository;
    private final ItsmProperties properties;

    public AttachmentService(AttachmentPolicyRepository policyRepository,
                             TicketAttachmentRepository attachmentRepository,
                             ItsmProperties properties) {
        this.policyRepository = policyRepository;
        this.attachmentRepository = attachmentRepository;
        this.properties = properties;
    }

    /** PDFs may not be larger than this (other types follow the attachment policy's limit). */
    public static final long PDF_MAX_BYTES = 5L * 1024 * 1024;

    /** True when a file was chosen (an empty file input still sends an empty part). */
    public static boolean present(MultipartFile file) {
        return file != null && !file.isEmpty() && StringUtils.hasText(file.getOriginalFilename());
    }

    /** Checks size and type without storing, so a form can be rejected before anything is saved. */
    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ItsmException("ATTACHMENT_EMPTY", "Choose a file to attach.");
        }
        AttachmentPolicy policy = policyRepository.findFirstByOrderByAttachmentPolicyIdAsc().orElse(null);
        int max = policy != null ? policy.getMaxBytes() : 10 * 1024 * 1024;
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = extension(original);
        if ("pdf".equals(ext) && file.getSize() > PDF_MAX_BYTES) {
            throw new ItsmException("ATTACHMENT_TOO_LARGE", "PDF files must not exceed 5 MB (this one is "
                    + megabytes(file.getSize()) + ").");
        }
        if (file.getSize() > max) {
            throw new ItsmException("ATTACHMENT_TOO_LARGE", "File exceeds the allowed size of " + megabytes(max) + ".");
        }
        if (BLOCKED_EXT.contains(ext)) {
            throw new ItsmException("ATTACHMENT_EXECUTABLE", "Executable and script files are not allowed.");
        }
        if (policy != null && StringUtils.hasText(policy.getAllowedExtensions())) {
            if (!containsToken(policy.getAllowedExtensions(), "." + ext)) {
                throw new ItsmException("ATTACHMENT_TYPE", "File type ." + ext + " is not in the allowed list.");
            }
        }
        String contentType = file.getContentType();
        if (policy != null && StringUtils.hasText(policy.getAllowedMimeTypes()) && StringUtils.hasText(contentType)) {
            if (!containsToken(policy.getAllowedMimeTypes(), contentType)) {
                throw new ItsmException("ATTACHMENT_MIME", "Content type is not allowed.");
            }
        }
    }

    private static String megabytes(long bytes) {
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    @Transactional
    public TicketAttachment store(Ticket ticket, Employee uploader, MultipartFile file) {
        validate(file);
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = extension(original);
        String contentType = file.getContentType();
        Path root = storageRoot();
        String key = ticket.getTicketId() + "/" + UUID.randomUUID().toString() + (ext.length() > 0 ? "." + ext : "");
        Path dest = root.resolve(key).normalize();
        if (!dest.startsWith(root)) {
            throw new ItsmException("ATTACHMENT_PATH", "Invalid storage path.");
        }
        try {
            Files.createDirectories(dest.getParent());
            file.transferTo(dest.toFile());
        } catch (IOException ex) {
            throw new ItsmException("ATTACHMENT_IO", "Could not store the file.");
        }
        TicketAttachment row = new TicketAttachment();
        row.setTicket(ticket);
        row.setOriginalName(original);
        row.setStorageKey(key);
        row.setContentType(contentType);
        row.setByteLength((int) file.getSize());
        row.setUploadedBy(uploader);
        row.setUploadedAtUtc(TimeUtc.now());
        return attachmentRepository.save(row);
    }

    public Path resolveFile(TicketAttachment attachment) {
        Path root = storageRoot();
        Path dest = root.resolve(attachment.getStorageKey()).normalize();
        if (!dest.startsWith(root) || !Files.isRegularFile(dest)) {
            throw new ItsmException("ATTACHMENT_MISSING", "File is not available.");
        }
        return dest;
    }

    private Path storageRoot() {
        String configured = properties.getStorage().getRoot();
        if (!StringUtils.hasText(configured)) {
            configured = System.getProperty("java.io.tmpdir") + "/itsm-files";
        }
        Path root = Paths.get(configured).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new ItsmException("ATTACHMENT_ROOT", "Storage root is not writable.");
        }
        return root;
    }

    private static String extension(String name) {
        int i = name.lastIndexOf('.');
        if (i < 0 || i == name.length() - 1) {
            return "";
        }
        return name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean containsToken(String csv, String token) {
        String[] parts = csv.split(",");
        for (String p : parts) {
            if (p.trim().equalsIgnoreCase(token)) {
                return true;
            }
        }
        return false;
    }
}
