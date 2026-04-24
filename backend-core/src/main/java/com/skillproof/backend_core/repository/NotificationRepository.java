package com.skillproof.backend_core.repository;

import com.skillproof.backend_core.model.Notification;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
        select n
        from Notification n
        join fetch n.recipientUser recipient
        left join fetch n.senderUser sender
        where recipient.id = :recipientId
        order by n.createdAt desc
        """)
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(@Param("recipientId") Long recipientId);

    Long countByRecipientUserIdAndIsReadFalse(Long recipientId);

    Optional<Notification> findByIdAndRecipientUserId(Long id, Long recipientId);

    @Modifying
    @Query("""
        update Notification n
        set n.isRead = true,
            n.readAt = :readAt
        where n.recipientUser.id = :recipientId
          and n.isRead = false
        """)
    int markAllAsReadForRecipient(@Param("recipientId") Long recipientId, @Param("readAt") LocalDateTime readAt);
}
