package com.quickpay.bill.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quickpay.bill.domain.Bill;
import com.quickpay.bill.domain.BillOutboxEvent;
import com.quickpay.bill.dto.event.PaymentEvent;
import com.quickpay.bill.enums.BillStatus;
import com.quickpay.bill.exception.ParsingNotificationEventException;
import com.quickpay.bill.repository.BillRepository;
import com.quickpay.bill.repository.NotificationEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;


@Component
@RequiredArgsConstructor
public class BillOutcomeService {

    private final ObjectMapper objectMapper;

    private final BillRepository billRepository;

    private final NotificationEventRepository notificationEventRepository;

    @Transactional
    public void recordOutcome(Bill bill){
        PaymentEvent event = PaymentEvent.builder()
                .paymentId(bill.getPaymentId())
                .cif(bill.getCif())
                .billReference(bill.getBillReference())
                .walletNumber(bill.getWalletNumber())
                .amount(bill.getAmount())
                .occurredAt(LocalDateTime.now())
                .build();
        String payload;

        if(bill.getStatus().requireNotification()){
            try {
                payload = objectMapper.writeValueAsString(event);
            } catch (JsonProcessingException e){
                throw new ParsingNotificationEventException(event.paymentId());
            }
            String eventType = bill.getStatus() == BillStatus.Paid ? "bill.payment.paid" : "bill.payment.rejected";
            BillOutboxEvent notification = new BillOutboxEvent(payload, null,eventType, UUID.randomUUID(), MDC.get("correlationId"));
            notificationEventRepository.save(notification);
        }
        billRepository.save(bill);
    }
}
