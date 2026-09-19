package com.quickpay.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

@Entity
@Table(name = "sessions")
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
public class Session {

    @Id
    private String sessionId;

    private String tokenHash;

    private String cif;

    @Column(insertable = false, updatable = false)
    private Timestamp createdAt;

    private Timestamp expiresAt;
}
