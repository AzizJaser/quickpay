package com.quickpay.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quickpay.notification.client.NotificationProviderClient;
import com.quickpay.notification.domain.Customer;
import com.quickpay.notification.domain.ProcessedEvent;
import com.quickpay.notification.dto.event.NotificationEvent;
import com.quickpay.notification.dto.response.ProviderResponse;
import com.quickpay.notification.enums.NotificationState;
import com.quickpay.notification.enums.NotificationStatus;
import com.quickpay.notification.exception.CustomerNotFoundException;
import com.quickpay.notification.repository.CustomerRepository;
import com.quickpay.notification.repository.ProcessedEventRepository;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final ProcessedEventRepository processedEventRepository;

    private final NotificationProviderClient notificationProviderClient;

    private final CustomerRepository customerRepository;

    private final ObjectMapper objectMapper;

    @Value("${notification.maximum-retries}")
    private int MAXIMUM_RETRIES;


    public void deliver(ProcessedEvent event, Customer customer, String routingKey) {

        String message = routingKey.equals("wallet.money.sent")
                ? "your transaction has been sent!"
                : "you received a transaction!";



        if (event.getSmsState().equals(NotificationState.PENDING) && event.getAttempts() < MAXIMUM_RETRIES) {
            ProviderResponse response = notificationProviderClient
                    .smsProvider(customer.getPhoneNumber(), message, event.getMessageId());
            //event.setSmsStatus(response.status() == NotificationStatus.SENT); --- deprecated
            event.setSmsState(response.status() == NotificationStatus.SENT ? NotificationState.SENT : NotificationState.PENDING);
            if (event.getSmsState().equals(NotificationState.SENT)) {
                event.setSmsSentAt(LocalDateTime.now());
            } else {
                event.setSmsSentAt(null);
                if(event.getAttempts() + 1 >= MAXIMUM_RETRIES){
                    event.setSmsState(NotificationState.FAILED);
                }else {
                    event.setSmsState(NotificationState.PENDING);
                }
            }
        }

        if (event.getEmailState().equals(NotificationState.PENDING) && event.getAttempts() < MAXIMUM_RETRIES) {
            ProviderResponse response = notificationProviderClient
                    .emailProvider(customer.getEmail(), message, event.getMessageId());
            //event.setEmailStatus(response.status() == NotificationStatus.SENT); --- deprecated
            event.setEmailState(response.status() == NotificationStatus.SENT ? NotificationState.SENT : NotificationState.PENDING);
            if (event.getEmailState().equals(NotificationState.SENT)) {
                event.setEmailSentAt(LocalDateTime.now());
            } else {
                event.setEmailSentAt(null);
                if(event.getAttempts() + 1 >= MAXIMUM_RETRIES){
                    event.setEmailState(NotificationState.FAILED);
                }else {
                    event.setEmailState(NotificationState.PENDING);
                }
            }
        }

        event.setAttempts(event.getAttempts() + 1);
        event.setLastAttemptAt(LocalDateTime.now());
        processedEventRepository.save(event);
    }

    public Customer extractCustomerFromMessage(String payload) throws JsonProcessingException {
        NotificationEvent receivedEvent = objectMapper.readValue(payload, NotificationEvent.class);
        return customerRepository.findCustomerByCif(receivedEvent.cif())
                .orElseThrow(()-> new CustomerNotFoundException(receivedEvent.cif()));
    }
}
