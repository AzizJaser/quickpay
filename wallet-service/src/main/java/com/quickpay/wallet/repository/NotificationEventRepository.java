package com.quickpay.wallet.repository;

import com.quickpay.wallet.domain.NotificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationEventRepository extends JpaRepository<NotificationEvent, UUID> {


    List<NotificationEvent> findTop100BySentAtIsNullOrderByCreatedAtAsc();
}
