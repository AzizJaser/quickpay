package com.quickpay.customer.repository;

import com.quickpay.customer.domain.CustomerOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<CustomerOutboxEvent, UUID> {

    List<CustomerOutboxEvent> findTop100BySentAtIsNullOrderByCreatedAtAsc();

}
