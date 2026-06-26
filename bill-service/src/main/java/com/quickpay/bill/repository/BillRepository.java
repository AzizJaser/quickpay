package com.quickpay.bill.repository;

import com.quickpay.bill.domain.Bill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BillRepository extends JpaRepository<Bill, String> {

    Optional<Bill> findByIdempotencyKey(String idempotencyKey);
}
