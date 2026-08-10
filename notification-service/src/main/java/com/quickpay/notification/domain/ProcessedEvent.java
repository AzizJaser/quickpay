package com.quickpay.notification.domain;

import com.quickpay.notification.enums.NotificationState;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    private String messageId;

    private boolean smsStatus;

    private LocalDateTime smsSentAt;

    private boolean emailStatus;

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

}
