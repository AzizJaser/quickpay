package com.quickpay.wallet.domain;

import com.quickpay.wallet.enums.WalletStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;


@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Wallet {

    @Id
    private String wallet_number;

    private String cif;

    private String wallet_name;

    private Long balance;

    @Enumerated(EnumType.STRING)
    private WalletStatus status;

//    private boolean isSystem; changed to internal and allows_negative

    @Column(name = "is_internal")
    private boolean internal;

    private boolean allowsNegative;

    @Column(insertable = false, updatable = false)
    private LocalDateTime created_at;


}
