package com.quickpay.customer.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.quickpay.customer.enums.CustomerStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.*;

import java.sql.Timestamp;

@Entity
@AllArgsConstructor @NoArgsConstructor
@Table(name = "customers")
@Getter @Setter
@Builder
public class Customer {

    @Id
    private String cif;

    private String nationalId;

    private String customerName;

    private String phoneNumber;

    @Email
    private String email;

    @Column(name = "password_hash")
    @JsonIgnore
    private String password;

    @Column(insertable = false, updatable = false)
    private Timestamp createdAt;

    private Timestamp updatedAt;

    @Enumerated(EnumType.STRING)
    private CustomerStatus status;


}
