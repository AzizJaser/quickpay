package com.quickpay.bill.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "outbox_bill_notification")
public class BillOutboxEvent {

    @Id
    private UUID eventId;

    private String eventType;

    private LocalDateTime sentAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    private String correlationId;

    public BillOutboxEvent(String payload, LocalDateTime sentAt, String eventType, UUID eventId, String correlationId) {
        this.payload = payload;
        this.sentAt = sentAt;
        this.eventType = eventType;
        this.eventId = eventId;
        this.correlationId = correlationId;
    }
}
