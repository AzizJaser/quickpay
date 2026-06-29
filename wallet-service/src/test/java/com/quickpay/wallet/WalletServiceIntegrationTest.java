package com.quickpay.wallet;

import com.quickpay.wallet.domain.LedgerEntry;
import com.quickpay.wallet.domain.Wallet;
import com.quickpay.wallet.exception.DuplicatedEntryException;
import com.quickpay.wallet.exception.EntryNotFoundException;
import com.quickpay.wallet.exception.InsufficientBalanceException;
import com.quickpay.wallet.repository.LedgerEntryRepository;
import com.quickpay.wallet.repository.WalletRepository;
import com.quickpay.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
public class WalletServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    WalletService walletService;
    @Autowired
    WalletRepository walletRepository;
    @Autowired
    LedgerEntryRepository ledgerEntryRepository;



    @Test
    public void transfer_movesMoney_andConservesTotal(){
        // -- ARRANGE --
        Wallet A = walletService.createWallet("1000000000","A");
        Wallet B = walletService.createWallet("2000000000","B");
        walletService.activateWallet(A.getWallet_number());
        walletService.activateWallet(B.getWallet_number());
        walletService.topUp(A.getWallet_number(), 5000L, "Trx-conserves-01");

        A = walletRepository.findByWalletNumber(A.getWallet_number()).orElseThrow();
        B = walletRepository.findByWalletNumber(B.getWallet_number()).orElseThrow();
        long totalBefore = A.getBalance() + B.getBalance();

        // -- ACT --
        walletService.transfer(A.getWallet_number(),B.getWallet_number(),2000L, "Trx-conserves-02",null);

        // -- ASSERT --
        A = walletRepository.findByWalletNumber(A.getWallet_number()).orElseThrow();
        B = walletRepository.findByWalletNumber(B.getWallet_number()).orElseThrow();
        assertEquals(3000, A.getBalance());
        assertEquals(2000, B.getBalance());
        assertEquals(totalBefore, A.getBalance() + B.getBalance());
    }

    @Test
    public void transfer_insufficientBalance_throws_andNothingMoves(){
        // -- ARRANGE --
        Wallet A = walletService.createWallet("1100000000","A");
        Wallet B = walletService.createWallet("2100000000","B");
        walletService.activateWallet(A.getWallet_number());
        walletService.activateWallet(B.getWallet_number());
        walletService.topUp(A.getWallet_number(), 1000L, "Trx-insufficient-01");
        long ledgerCount = ledgerEntryRepository.count();

        // -- ACT + ASSERT EXCEPTION --
        String aNumber = A.getWallet_number();
        String bNumber = B.getWallet_number();
        assertThrows(InsufficientBalanceException.class,
                () ->walletService.transfer(aNumber,bNumber,2000L, "Trx-insufficient-02",null));

        // -- ASSERT --
        A = walletRepository.findByWalletNumber(A.getWallet_number()).orElseThrow();
        B = walletRepository.findByWalletNumber(B.getWallet_number()).orElseThrow();
        assertEquals(1000L, A.getBalance());
        assertEquals(0L, B.getBalance());
        assertEquals(ledgerEntryRepository.count(), ledgerCount);
    }

    @Test
    public void transfer_with_duplicated_idempotencyKey(){
        // -- ARRANGE --
        Wallet A = walletService.createWallet("1110000000","A");
        Wallet B = walletService.createWallet("2110000000","B");
        walletService.activateWallet(A.getWallet_number());
        walletService.activateWallet(B.getWallet_number());
        walletService.topUp(A.getWallet_number(), 4000L, "Trx-duplicated-01");

        // -- ACT + EXCEPTION--
        A = walletRepository.findByWalletNumber(A.getWallet_number()).orElseThrow();
        B = walletRepository.findByWalletNumber(B.getWallet_number()).orElseThrow();
        String aNumber = A.getWallet_number();
        String bNumber = B.getWallet_number();
        walletService.transfer(aNumber,bNumber,2000L, "Trx-duplicated-02",null);
        long ledgerCount = ledgerEntryRepository.count();
        assertThrows(DuplicatedEntryException.class,
                () ->walletService.transfer(aNumber,bNumber,2000L, "Trx-duplicated-02",null));

        // ASSERT
        A = walletRepository.findByWalletNumber(A.getWallet_number()).orElseThrow();
        B = walletRepository.findByWalletNumber(B.getWallet_number()).orElseThrow();
        assertEquals(2000L, A.getBalance());
        assertEquals(2000L, B.getBalance());
        assertEquals(ledgerEntryRepository.count(), ledgerCount);
    }

    @Test
    public void reconciliation_detectsDrift(){
        // -- ARRANGE --
        Wallet A = walletService.createWallet("1111000000","A");
        walletService.activateWallet(A.getWallet_number());
        walletService.topUp(A.getWallet_number(), 5000L, "Trx-recon-01");

        // -- SABOTAGE --
        Wallet a = walletRepository.findByWalletNumber(A.getWallet_number()).orElseThrow();
        a.setBalance(a.getBalance()+1);
        walletRepository.save(a);


        // -- ASSERT --
        List<Wallet> drifted = walletRepository.findDriftedWallets();
        boolean aDrifted = drifted.stream()
                .anyMatch(w -> w.getWallet_number().equals(A.getWallet_number()));
        assertTrue(aDrifted);
    }

    @Test
    public void reverse_movesMoneyBack_andConserves(){
        // -- ARRANGE A-> CUSTOMER, B-> TEMP --
        Wallet A = walletService.createWallet("1111000000","A");
        Wallet B = walletService.createWallet("2111000000","B");
        walletService.activateWallet(A.getWallet_number());
        walletService.activateWallet(B.getWallet_number());
        walletService.topUp(A.getWallet_number(), 4000L, "Trx-ToUp-B-01");

        // -- ACT (HOLD AND REVERS) --
        LedgerEntry entry = walletService.transfer(A.getWallet_number(),B.getWallet_number(),2000L, "Trx-HOLD-02",null);
        walletService.revers(entry.getEntryId(),"Trx-REVERS-03");


        // -- ASSERT --
        Wallet a = walletService.fetchWallet(A.getWallet_number());
        Wallet b = walletService.fetchWallet(B.getWallet_number());
        assertEquals(4000L, a.getBalance());
        assertEquals(0L,b.getBalance());
    }

    @Test
    public void reverse_nonExistentEntry_throws(){
        // -- ARRANGE A-> CUSTOMER, B-> TEMP --
        Wallet A = walletService.createWallet("1111100000","A");
        Wallet B = walletService.createWallet("2111100000","B");
        walletService.activateWallet(A.getWallet_number());
        walletService.activateWallet(B.getWallet_number());
        LedgerEntry entryId = walletService.topUp(A.getWallet_number(), 4000L, "Trx-ToUp-B-02");

        // -- ACT + THROW--
        assertThrows(EntryNotFoundException.class,
                () -> walletService.revers("DOES NOT EXIST","THIS WILL THROW"));


        // -- ASSERT --
        Wallet a = walletService.fetchWallet(A.getWallet_number());
        assertEquals(4000L,a.getBalance());
    }

    @Test
    public void reverse_replaySameKey_movesOnce(){
        // -- ARRANGE A-> CUSTOMER, B-> TEMP --
        Wallet A = walletService.createWallet("1111000000","A");
        Wallet B = walletService.createWallet("2111000000","B");
        walletService.activateWallet(A.getWallet_number());
        walletService.activateWallet(B.getWallet_number());
        LedgerEntry entry = walletService.topUp(A.getWallet_number(), 4000L, "Trx-ToUp-B-03");

        // -- ACT (HOLD AND REVERS twice) --
        LedgerEntry entry1 = walletService.transfer(A.getWallet_number(),B.getWallet_number(),2000L, "Trx-HOLD-03",null);
        LedgerEntry entry2 = walletService.revers(entry1.getEntryId(),"Trx-REVERS-04");
        assertThrows(DuplicatedEntryException.class,
                () -> walletService.revers(entry1.getEntryId(),"Trx-REVERS-04"));


        // -- ASSERT --
        Wallet a = walletService.fetchWallet(A.getWallet_number());
        Wallet b = walletService.fetchWallet(B.getWallet_number());
        assertEquals(4000L, a.getBalance());
        assertEquals(0L,b.getBalance());
    }

    @Test
    public void reverse_afterTempEmpty_blockedByFloor(){
        // -- ARRANGE A-> CUSTOMER, B-> TEMP --
        Wallet A = walletService.createWallet("1111000000","A");
        Wallet B = walletService.createWallet("2111000000","B");
        walletService.activateWallet(A.getWallet_number());
        walletService.activateWallet(B.getWallet_number());
        walletService.topUp(A.getWallet_number(), 4000L, "Trx-ToUp-B-04");

        // -- ACT (HOLD AND REVERS twice) --
        LedgerEntry entry = walletService.transfer(A.getWallet_number(),B.getWallet_number(),2000L, "Trx-HOLD-04",null);
        LedgerEntry entry2 = walletService.revers(entry.getEntryId(),"Trx-REVERS-05");
        assertThrows(InsufficientBalanceException.class,
                () -> walletService.revers(entry.getEntryId(),"Trx-REVERS-06"));


        // -- ASSERT --
        Wallet a = walletService.fetchWallet(A.getWallet_number());
        Wallet b = walletService.fetchWallet(B.getWallet_number());
        assertEquals(4000L, a.getBalance());
        assertEquals(0L,b.getBalance());
    }
}
