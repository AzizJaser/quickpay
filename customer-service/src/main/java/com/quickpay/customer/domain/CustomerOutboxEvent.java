package com.quickpay.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "customer_outbox")
@AllArgsConstructor @NoArgsConstructor
@Getter @Setter
public class CustomerOutboxEvent {

    @Id
    private UUID eventId;

    private String eventType;

    private Timestamp sentAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    private String correlationId;

    @Column(insertable = false, updatable = false)
    private Timestamp createdAt;
}
