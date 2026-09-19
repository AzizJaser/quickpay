package com.quickpay.customer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quickpay.customer.config.CustomPasswordEncoder;
import com.quickpay.customer.domain.Customer;
import com.quickpay.customer.domain.CustomerOutboxEvent;
import com.quickpay.customer.dto.event.CustomerRegistrationEvent;
import com.quickpay.customer.dto.request.CustomerActivateRequest;
import com.quickpay.customer.dto.request.CustomerRequest;
import com.quickpay.customer.dto.response.CustomerResponse;
import com.quickpay.customer.enums.CustomerStatus;
import com.quickpay.customer.exception.*;
import com.quickpay.customer.repository.CustomerRepository;
import com.quickpay.customer.repository.OutboxEventRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    private final OutboxEventRepository outboxEventRepository;

    private final BCryptPasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper;

    private static final Logger logger = LoggerFactory.getLogger(CustomerService.class);



    // create customer object -> hash password -> save customer -> save the event
    @Transactional
    public CustomerResponse registerCustomer(CustomerRequest request){

        if(customerRepository.existsByNationalIdAndStatusNot(request.nationalId(), CustomerStatus.CLOSED)){
            throw new DuplicateNationalIdException(request.nationalId());
        }
        String cif = String.format("%010d", customerRepository.getNextSeq());
        String hashedPassword = passwordEncoder.encode(request.password());

        Customer customer = Customer.builder()
                .nationalId(request.nationalId())
                .cif(cif)
                .customerName(request.customerName())
                .password(hashedPassword)
                .email(request.email())
                .phoneNumber(request.phoneNumber())
                .status(CustomerStatus.PENDING)
                .build();

        customerRepository.save(customer);

        CustomerRegistrationEvent event = new CustomerRegistrationEvent(cif, customer.getCustomerName(), customer.getPhoneNumber(),customer.getEmail());
        String payload ="";
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e){
            logger.error("error serializing customer event to outbox, {}",e.getMessage());
            throw new ParsingCustomerEventException(cif);

        }
        UUID eventId = UUID.randomUUID();
        CustomerOutboxEvent outboxEvent = new CustomerOutboxEvent(eventId,
                "customer.registered",null,payload, MDC.get("correlationId"),null);

        outboxEventRepository.save(outboxEvent);
        logger.info("customer created with cif {} and the event is written with id {} successfully",cif,eventId);

        return new CustomerResponse(cif,customer.getCustomerName(),customer.getStatus().toString());
    }

    @Transactional
    public CustomerResponse activateCustomer(CustomerActivateRequest request){
        Customer customer = customerRepository.findByCif(request.cif())
                .orElseThrow(CustomerNotFoundException::new);

        if(!customer.getStatus().equals(CustomerStatus.PENDING)){
            switch (customer.getStatus()){
                case ACTIVE -> {
                    return new CustomerResponse(customer.getCif(), customer.getCustomerName(), customer.getStatus().toString());
                }
                case CLOSED -> {
                    logger.warn("customer was not found with cif "+customer.getCif());
                    throw new CustomerClosedException();
                }
                case BLOCKED -> {
                    logger.warn("blocked customer request detected for customer with cif "+customer.getCif());
                    // sending not found, because we can't tell the blocked customer he/she is blocked.
                    throw new CustomerNotFoundException();
                }
                default -> {
                    logger.error("NEW STATUS DETECTED WHEN ACTIVATING CUSTOMER -> cif:{} , status:{}",customer.getCif(),customer.getStatus());
                    throw new RuntimeException("new status detected....");
                }
            }
        }

        if(passwordEncoder.matches(request.password(), customer.getPassword())){
            customer.setStatus(CustomerStatus.ACTIVE);
            customer.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
            customerRepository.save(customer);
        } else {
            throw new PasswordNotValidException();
        }

        return new CustomerResponse(customer.getCif(), customer.getCustomerName(), customer.getStatus().toString());
    }
}
