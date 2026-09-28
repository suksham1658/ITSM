package com.nbfc.itsm.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop8ByRecipientIdOrderByCreatedAtUtcDescNotificationIdDesc(Long recipientId);

    List<Notification> findTop200ByRecipientIdOrderByCreatedAtUtcDescNotificationIdDesc(Long recipientId);

    long countByRecipientIdAndReadFalse(Long recipientId);

    Optional<Notification> findByNotificationIdAndRecipientId(Long notificationId, Long recipientId);

    List<Notification> findByRecipientIdAndTicketId(Long recipientId, Long ticketId);

    @Modifying
    @Query("update Notification n set n.read = true where n.recipientId = :recipientId and n.read = false")
    int markAllRead(@Param("recipientId") Long recipientId);
}
