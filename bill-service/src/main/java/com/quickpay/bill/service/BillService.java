package com.quickpay.bill.service;

import com.quickpay.bill.client.WalletClient;
import com.quickpay.bill.domain.Bill;
import com.quickpay.bill.dto.response.TransferResponse;
import com.quickpay.bill.enums.BillStatus;
import com.quickpay.bill.exception.ReserveDeclinedException;
import com.quickpay.bill.repository.BillRepository;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BillService {

    private final BillRepository billRepository;

    private final WalletClient walletClient;

    private final Logger logger = LoggerFactory.getLogger(BillService.class);


    public Bill createPayment(String billReference, String walletNumber, Long amount, String idempotencyKey){
        Optional<Bill> existing = billRepository.findByIdempotencyKey(idempotencyKey);
        if(existing.isPresent()){
            return existing.get();
        }

        Bill bill = new Bill(billReference, walletNumber, amount, idempotencyKey);
        try {
            billRepository.save(bill);
            return bill;
        } catch (DataIntegrityViolationException e){
            return billRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        }
    }

    public Bill reserveFunds(Bill bill){
        if(bill.getStatus() != BillStatus.Pending){
            return bill;
        }

        String reserveKey = "r" + bill.getPaymentId().replace("-","");
        try {
            logger.info("Calling wallet service to reserve funds for bill number "+bill.getBillReference());
            TransferResponse response = walletClient.reserve(bill.getWalletNumber(),bill.getAmount(),reserveKey);
            logger.info("Response received from wallet service");
            bill.setEntryId(response.entryId());

            bill.setStatus(BillStatus.Reserved);

            billRepository.save(bill);
            return bill;
        } catch (ReserveDeclinedException e){
            logger.warn("funds reserve was rejected from wallet service bill number{}", bill.getBillReference());
            bill.setStatus(BillStatus.Rejected);
            billRepository.save(bill);
            return bill;
        }
    }
}
