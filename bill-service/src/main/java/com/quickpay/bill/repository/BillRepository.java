package com.quickpay.bill.repository;

import com.quickpay.bill.domain.Bill;
import com.quickpay.bill.enums.BillStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillRepository extends JpaRepository<Bill, String> {

    Optional<Bill> findByIdempotencyKey(String idempotencyKey);
    Optional<Bill> findByPaymentId(String paymentId);
    List<Bill> findByStatus(BillStatus status);
}
