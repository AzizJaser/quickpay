package com.quickpay.wallet.repository;

import com.quickpay.wallet.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry,String> {

    boolean existsByIdempotencyKey(String idempotencyKey);
    
    Optional<LedgerEntry> findByEntryId(String entryId);

    @Query(value = """
            SELECT e FROM LedgerEntry e WHERE coalesce(e.reversesEntryId,e.settlesEntryId) = :holdId
            """)
    Optional<LedgerEntry> findEntryByHoldId(@Param("holdId") String holdId);

}

