package com.quickpay.notification.domain;

import com.quickpay.notification.enums.NotificationState;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
@Entity
@Table(name = "processed_events")
@Builder
public class ProcessedEvent {

    @Id
    private String messageId;

    private LocalDateTime smsSentAt;

    private LocalDateTime emailSentAt;

    @Column(insertable = false,updatable = false)
    private LocalDateTime createdAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    private int attempts;

    @Column(nullable = false)
    private LocalDateTime lastAttemptAt;

    private String routingKey;

    @Enumerated(EnumType.STRING)
    private NotificationState smsState;

    @Enumerated(EnumType.STRING)
    private NotificationState emailState;

    private String correlationId;

}
