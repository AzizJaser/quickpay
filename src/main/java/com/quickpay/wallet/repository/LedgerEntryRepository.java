package com.quickpay.wallet.repository;

import com.quickpay.wallet.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry,String> {

    boolean existsByIdempotencyKey(String idempotencykey);
}
