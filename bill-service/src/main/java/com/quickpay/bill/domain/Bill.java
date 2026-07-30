package com.quickpay.bill.domain;

import com.quickpay.bill.enums.BillStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
public class Bill {

    @Id
    @Column(name = "payment_id")
    private java.lang.String paymentId;
    @Column(name = "bill_reference")
    private java.lang.String billReference;
    @Column(name = "wallet_number")
    private java.lang.String walletNumber;

    private Long amount;
    @Enumerated(EnumType.STRING)
    private BillStatus status;
    @Column(name = "entry_id")
    private java.lang.String entryId;
    @Column(name = "idempotency_key")
    private java.lang.String idempotencyKey;

    @Column(insertable = false, updatable = false,name = "created_at")
    private LocalDateTime created_at;

    public Bill(java.lang.String billReference, java.lang.String walletNumber, Long amount, java.lang.String idempotencyKey) {
        this.paymentId = UUID.randomUUID().toString();
        this.billReference = billReference;
        this.walletNumber = walletNumber;
        this.amount = amount;
        this.status = BillStatus.Pending;
        this.idempotencyKey = idempotencyKey;
    }
}
