package com.quickpay.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
    private String entry_id;

    private String debited_wallet_number;

    private String credited_wallet_number;

    private Long debited_amount;

    private Long credited_amount;

    private String idempotencyKey;

    @Column(insertable = false, updatable = false)
    private LocalDateTime created_at;

    public LedgerEntry(String debited_wallet_number,String credited_wallet_number, Long debited_amount, Long credited_amount,String idempotencyKey){
        this.entry_id = UUID.randomUUID().toString();
        this.debited_wallet_number = debited_wallet_number;
        this.credited_wallet_number = credited_wallet_number;
        this.debited_amount = debited_amount;
        this.credited_amount = credited_amount;
        this.idempotencyKey = idempotencyKey;
    }
}
