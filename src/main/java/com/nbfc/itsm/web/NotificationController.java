package com.nbfc.itsm.web;

import com.nbfc.itsm.domain.Notification;
import com.nbfc.itsm.domain.NotificationRepository;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The signed-in user's own notifications: list, open (marks read), mark all read. */
@Controller
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping("/notifications")
    public String list(@AuthenticationPrincipal ItsmUserPrincipal user, Model model) {
        model.addAttribute("nav", "notifications");
        model.addAttribute("pageTitle", "Notifications");
        model.addAttribute("items",
                notificationRepository.findTop200ByRecipientIdOrderByCreatedAtUtcDescNotificationIdDesc(user.getEmployeeId()));
        return "notifications";
    }

    /** Marks the notification read and goes to its ticket. Only the recipient can open it. */
    @GetMapping("/notifications/{id}/open")
    @Transactional
    public String open(@AuthenticationPrincipal ItsmUserPrincipal user, @PathVariable("id") Long id) {
        Notification n = notificationRepository.findByNotificationIdAndRecipientId(id, user.getEmployeeId()).orElse(null);
        if (n == null) {
            return "redirect:/notifications";
        }
        n.setRead(true);
        return n.getTicketId() == null ? "redirect:/notifications" : "redirect:/tickets/" + n.getTicketId();
    }

    @PostMapping("/notifications/read-all")
    @Transactional
    public String readAll(@AuthenticationPrincipal ItsmUserPrincipal user, RedirectAttributes ra) {
        int n = notificationRepository.markAllRead(user.getEmployeeId());
        ra.addFlashAttribute("message", n == 0 ? "Nothing new." : n + " notification(s) marked as read.");
        return "redirect:/notifications";
    }
}
