package com.quickpay.wallet.repository;

import com.quickpay.wallet.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry,String> {

    boolean existsByIdempotencyKey(String idempotencykey);
    
    Optional<LedgerEntry> findByEntryId(String entryId);
}

