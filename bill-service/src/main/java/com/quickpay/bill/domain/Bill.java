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
    private String paymentId;
    @Column(name = "bill_reference")
    private String billReference;
    @Column(name = "wallet_number")
    private String walletNumber;

    private Long amount;
    @Enumerated(EnumType.STRING)
    private BillStatus status;
    @Column(name = "entry_id")
    private String entryId;
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(insertable = false, updatable = false,name = "created_at")
    private LocalDateTime created_at;

    public Bill(String billReference, String walletNumber, Long amount, String idempotencyKey) {
        this.paymentId = UUID.randomUUID().toString();
        this.billReference = billReference;
        this.walletNumber = walletNumber;
        this.amount = amount;
        this.status = BillStatus.Pending;
        this.idempotencyKey = idempotencyKey;
    }
}
