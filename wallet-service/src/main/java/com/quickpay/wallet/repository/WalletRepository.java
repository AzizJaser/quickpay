package com.quickpay.wallet.repository;

import com.quickpay.wallet.domain.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet,String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.wallet_number = :walletNumber")
    Optional<Wallet> findWithLockByWalletNumber(@Param("walletNumber") String walletNumber);

    @Query("select w from Wallet w where w.wallet_number = :walletNumber")
    Optional<Wallet> findByWalletNumber(@Param("walletNumber") String walletNumber);

//    @Query("select count(cif) from Wallet w where w.cif = :cif")
    int countByCif(@Param("cif") String cif);

    @Query(nativeQuery = true, value = """
      SELECT * FROM wallet w
      WHERE w.balance <>
            COALESCE((SELECT SUM(debited_amount)  FROM ledger WHERE debited_wallet_number  = w.wallet_number), 0)
          + COALESCE((SELECT SUM(credited_amount) FROM ledger WHERE credited_wallet_number = w.wallet_number), 0)
      """)
    List<Wallet> findDriftedWallets();



}
