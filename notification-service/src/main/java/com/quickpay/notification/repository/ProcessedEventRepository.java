package com.quickpay.notification.repository;

import com.quickpay.notification.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent,String> {
    Optional<ProcessedEvent> findByMessageId(String messageId);
}
