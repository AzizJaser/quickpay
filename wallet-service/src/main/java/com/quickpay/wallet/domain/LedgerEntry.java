package com.quickpay.wallet.domain;

import com.quickpay.wallet.enums.TransactionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ledger")
public class LedgerEntry {

    @Id
    private String entryId;

    private String debited_wallet_number;

    private String credited_wallet_number;

    private Long debited_amount;

    private Long credited_amount;

    private String idempotencyKey;

    private String reversesEntryId;

    private String settlesEntryId;

    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    @Column(insertable = false, updatable = false)
    private LocalDateTime created_at;

    public LedgerEntry(String debited_wallet_number,String credited_wallet_number, Long debited_amount, Long credited_amount,String idempotencyKey, String reversesEntryId){
        this.entryId = UUID.randomUUID().toString();
        this.debited_wallet_number = debited_wallet_number;
        this.credited_wallet_number = credited_wallet_number;
        this.debited_amount = debited_amount;
        this.credited_amount = credited_amount;
        this.idempotencyKey = idempotencyKey;
        this.reversesEntryId = reversesEntryId;
    }
}
