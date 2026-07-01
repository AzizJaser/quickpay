package com.quickpay.bill.service;

import com.quickpay.bill.client.BillerClient;
import com.quickpay.bill.client.WalletClient;
import com.quickpay.bill.domain.Bill;
import com.quickpay.bill.dto.request.BillerPayRequest;
import com.quickpay.bill.dto.response.BillerResult;
import com.quickpay.bill.dto.response.TransferResponse;
import com.quickpay.bill.enums.BillStatus;
import com.quickpay.bill.enums.BillerStatus;
import com.quickpay.bill.exception.BillNotFoundException;
import com.quickpay.bill.exception.ReserveDeclinedException;
import com.quickpay.bill.repository.BillRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BillService {

    private final BillRepository billRepository;

    private final WalletClient walletClient;

    private final BillerClient billerClient;

    private final Logger logger = LoggerFactory.getLogger(BillService.class);


    public Bill createPayment(java.lang.String billReference, java.lang.String walletNumber, Long amount, java.lang.String idempotencyKey){
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

    @Async
    public void payBiller(String paymentId){
        Bill bill = billRepository.findByPaymentId(paymentId).orElseThrow(() -> new BillNotFoundException(paymentId));

        if(bill.getStatus() != BillStatus.Reserved){
            return;
        }

        String captureKey = "c" + bill.getPaymentId().replace("-","");
        String reversKey = "v" + bill.getPaymentId().replace("-","");

        try {
            logger.warn("Calling biller gateway ...");
            BillerResult billerResult =
                    billerClient.pay(new BillerPayRequest(bill.getBillReference(),bill.getAmount(),bill.getPaymentId()));

            if (billerResult.status() == BillerStatus.FAILED){
                // call the wallet to reverse tbe the Trx
                walletClient.reverse(bill.getEntryId(), reversKey);
                bill.setStatus(BillStatus.Rejected);
            } else if (billerResult.status() == BillerStatus.PAID) {
                // call the wallet to move the fund to biller account
                walletClient.capture(bill.getAmount(),captureKey);
                bill.setStatus(BillStatus.Paid);
            }

            billRepository.save(bill);
        } catch (HttpServerErrorException e){
            logger.error("5xx from biller gateway, Payment ID {}",paymentId);
            // nothing EOD job will reconcile
        } catch (ResourceAccessException e){
            logger.error("timeout / unable to connect to biller gateway, Payment ID {}",paymentId);
        }
    }
}
