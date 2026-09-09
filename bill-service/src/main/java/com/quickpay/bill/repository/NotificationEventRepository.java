package com.quickpay.bill.repository;

import com.quickpay.bill.domain.BillOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
@Repository
public interface NotificationEventRepository extends JpaRepository<BillOutboxEvent, UUID> {

    List<BillOutboxEvent> findTop100BySentAtIsNullOrderByCreatedAtAsc();

}
