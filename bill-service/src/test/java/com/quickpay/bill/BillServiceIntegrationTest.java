package com.quickpay.bill;

import com.quickpay.bill.client.BillerClient;
import com.quickpay.bill.client.WalletClient;
import com.quickpay.bill.domain.Bill;
import com.quickpay.bill.dto.response.TransferResponse;
import com.quickpay.bill.enums.BillStatus;
import com.quickpay.bill.exception.ReserveDeclinedException;
import com.quickpay.bill.repository.BillRepository;
import com.quickpay.bill.service.BillService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@SpringBootTest
@Testcontainers
public class BillServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    BillService billService;

    @Autowired
    BillRepository billRepository;

    @MockBean
    WalletClient walletClient;

    @MockBean
    BillerClient billerClient;


    @Test
    public void createPayment_replaySameKey_returnsRecord(){
        // ACT — record the intent. This is the write-ahead step: it should persist a
        // Pending bill and commit, WITHOUT touching the wallet or the biller.
        Bill bill = billService.createPayment("BILL-REF-01", "000000000001", 100L, "IDMP-KEY-01");

        // --- ASSERT: the record the service handed back ---

        // The bill service generates the paymentId itself (UUID, no @GeneratedValue).
        // This is the id the biller will dedupe on and the seed for the r/c/v wallet keys.
        assertNotNull(bill.getPaymentId());

        // A freshly created bill must start at the head of the state machine.
        // (expected first, actual second — so failures read "expected <Pending> but was <X>")
        assertEquals(BillStatus.Pending, bill.getStatus());

        // THE KEY ASSERTION: entry_id is null because no reserve has happened.
        // A null entry_id IS the proof that no money has moved yet — that's write-ahead.
        // It's null, not "" — the column is deliberately nullable.
        assertNull(bill.getEntryId());

        // The client-supplied key is stored as given; it's the inbound dedup handle
        // (UNIQUE in the DB) that guarantees one customer request == one payment record.
        assertEquals("IDMP-KEY-01", bill.getIdempotencyKey());

        // --- ASSERT: it was actually PERSISTED ---
        // The service returns a detached snapshot, so asserting on it alone proves nothing
        // about the database. Reload and compare — the wallet-service lesson.
        Bill fromDb = billRepository.findByIdempotencyKey("IDMP-KEY-01").orElseThrow();
        assertEquals(bill.getPaymentId(), fromDb.getPaymentId());   // same record, not a second row
        assertEquals(BillStatus.Pending, fromDb.getStatus());       // committed as Pending

        // --- ASSERT: nothing external was touched ---
        // This is the write-ahead invariant expressed as a test: recording intent must NOT
        // move money. If a future change ever made createPayment call the wallet, this fails.
        verifyNoInteractions(walletClient, billerClient);
    }

    @Test
    public void createPayment_replaySameKey_returnsSameRecord() {
        // ARRANGE
        Bill firstBill = billService.createPayment("BILL-REF-03", "000000000001", 100L, "IDM-KEY-02");
        long countBefore = billRepository.count();

        // ACT
        Bill secondBill = billService.createPayment("BILL-REF-03", "000000000001", 999L, "IDM-KEY-02");

        //ASSERT
        assertEquals(secondBill.getPaymentId(),firstBill.getPaymentId());
        assertEquals(100L,secondBill.getAmount());
        assertEquals(billRepository.count(),countBefore);

        verifyNoInteractions(walletClient,billerClient);
    }

    @Test
    public void reserveFunds_walletAccepts_marksReserved(){

        // ARRANGE
        Bill bill = billService.createPayment("BILL-REF-02", "000000000001", 100L, "IDMP-KEY-03");
        when(walletClient.reserve(anyString(), anyLong(), anyString())).thenReturn(new TransferResponse("ENTRY-123","rABC"));

        // ACT
        Bill reserved = billService.reserveFunds(bill);

        // ASSERT
        assertEquals(BillStatus.Reserved,reserved.getStatus());
        assertEquals("ENTRY-123",reserved.getEntryId());
        Bill fromDB = billRepository.findByIdempotencyKey("IDMP-KEY-03").orElseThrow();
        assertEquals(reserved.getEntryId(),fromDB.getEntryId());
        assertEquals(BillStatus.Reserved,fromDB.getStatus());

        // ASSERT Interaction
        String reserveKey = "r" + bill.getPaymentId().replace("-","");
        verify(walletClient).reserve(eq(bill.getWalletNumber()),eq(bill.getAmount()),eq(reserveKey));
        verifyNoInteractions(billerClient);

    }

    @Test
    public void reserveFunds_walletDeclines_marksRejected(){
        // ARRANGE
        Bill bill = billService.createPayment("BILL-REF-04","000000000001",100L,"IDMP-KEY-04");
        assertEquals(BillStatus.Pending, bill.getStatus());
        String reserveKey = "r" + bill.getPaymentId().replace("-","");
        when(walletClient.reserve(bill.getWalletNumber(),bill.getAmount(),reserveKey)).thenThrow(new ReserveDeclinedException("No funds in the wallet"));


        // ACT
        bill = billService.reserveFunds(bill);

        // ASSERT
        Bill fromDB = billRepository.findByIdempotencyKey("IDMP-KEY-04").orElseThrow();
        assertEquals(BillStatus.Rejected,fromDB.getStatus());
        assertNull(fromDB.getEntryId());
        verifyNoInteractions(billerClient);
    }
}

