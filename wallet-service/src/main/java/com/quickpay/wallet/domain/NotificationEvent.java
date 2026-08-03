package com.quickpay.wallet.domain;

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
@Table(name = "outbox_notification")
public class NotificationEvent {

    @Id
    private UUID eventId;

    private String eventType;

    private LocalDateTime sentAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public NotificationEvent(String payload, LocalDateTime sentAt, String eventType, UUID eventId) {
        this.payload = payload;
        this.sentAt = sentAt;
        this.eventType = eventType;
        this.eventId = eventId;
    }
}
