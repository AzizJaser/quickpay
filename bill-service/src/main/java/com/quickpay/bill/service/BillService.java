package com.quickpay.bill.service;

import com.quickpay.bill.client.BillerClient;
import com.quickpay.bill.client.WalletClient;
import com.quickpay.bill.domain.Bill;
import com.quickpay.bill.dto.request.BillerPayRequest;
import com.quickpay.bill.dto.response.BillerResult;
import com.quickpay.bill.dto.response.HoldResponse;
import com.quickpay.bill.dto.response.TransferResponse;
import com.quickpay.bill.enums.BillStatus;
import com.quickpay.bill.enums.BillerStatus;
import com.quickpay.bill.exception.BillNotFoundException;
import com.quickpay.bill.exception.FundIsReleasedException;
import com.quickpay.bill.exception.ReserveDeclinedException;
import com.quickpay.bill.exception.SettleRejectedException;
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

    private final BillOutcomeService billOutcomeService;

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
            HoldResponse response = walletClient.reserve(bill.getWalletNumber(),bill.getAmount(),reserveKey);
            logger.info("Response received from wallet service");
            bill.setEntryId(response.entryId());
            bill.setCif(response.cif());

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

            bill = resolve(bill,billerResult);

//            billRepository.save(bill);
        } catch (HttpServerErrorException e){
            logger.error("5xx from biller gateway, Payment ID {}",paymentId);
            // nothing EOD job will reconcile
        } catch (ResourceAccessException e){
            logger.error("timeout / unable to connect to biller gateway, Payment ID {}",paymentId);
        }
    }

    public Bill resolve(Bill bill, BillerResult result){
        if(bill.getStatus() != BillStatus.Reserved){
            return bill;
        }
        switch (result.status()){
            case PAID: {
                try {
                    walletClient.capture(bill.getEntryId(), "c" + bill.getPaymentId().replace("-",""));
                    bill.setStatus(BillStatus.Paid);
                } catch (FundIsReleasedException e) {
                    logger.warn("hold for payment {} was already released — marking Rejected", bill.getPaymentId());
                    bill.setStatus(BillStatus.Rejected);
                } catch (SettleRejectedException e) {
                    logger.error("wallet refused to settle entry {} for payment {} — needs investigation",
                            bill.getEntryId(), bill.getPaymentId());
                    bill.setStatus(BillStatus.Failed);
                }
                billOutcomeService.recordOutcome(bill);
            } break;
            case FAILED: {
                walletClient.reverse(bill.getEntryId(), "v" + bill.getPaymentId().replace("-",""));
                bill.setStatus(BillStatus.Rejected);
                billOutcomeService.recordOutcome(bill);
            } break;
            case NOT_FOUND: // nothing
                break;
        }

        return bill;
    }
}
