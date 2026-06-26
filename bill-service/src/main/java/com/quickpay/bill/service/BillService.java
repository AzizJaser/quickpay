package com.quickpay.bill.service;

import com.quickpay.bill.domain.Bill;
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
}
