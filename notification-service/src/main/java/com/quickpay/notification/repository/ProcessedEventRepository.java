package com.quickpay.notification.repository;

import com.quickpay.notification.domain.ProcessedEvent;
import com.quickpay.notification.enums.NotificationState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent,String> {
    Optional<ProcessedEvent> findByMessageId(String messageId);

    List<ProcessedEvent> findTop100BySmsStateOrderByCreatedAtAsc(NotificationState state);

    List<ProcessedEvent> findTop100ByEmailStateOrderByCreatedAtAsc(NotificationState state);
}
