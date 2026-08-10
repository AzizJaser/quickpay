package com.quickpay.notification.config;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.quickpay.notification.domain.Customer;
import com.quickpay.notification.domain.ProcessedEvent;
import com.quickpay.notification.enums.NotificationState;
import com.quickpay.notification.exception.CustomerNotFoundException;
import com.quickpay.notification.repository.ProcessedEventRepository;
import com.quickpay.notification.service.NotificationService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@AllArgsConstructor
public class ResendingJob {

    private final ProcessedEventRepository processedEventRepository;

    private final NotificationService notificationService;

    private static final Logger logger = LoggerFactory.getLogger(ResendingJob.class);


    @Scheduled(fixedDelayString = "${notification.resend-interval-ms:60000}")
    @Transactional
    public void resending(){
        List<ProcessedEvent> sms_events = processedEventRepository.findTop100BySmsStateOrderByCreatedAtAsc(NotificationState.PENDING);
        List<ProcessedEvent> email_events = processedEventRepository.findTop100ByEmailStateOrderByCreatedAtAsc(NotificationState.PENDING);
        Map<String,ProcessedEvent> events = new LinkedHashMap<>();
        for(ProcessedEvent event : sms_events){
            events.put(event.getMessageId(),event);
        }
        for(ProcessedEvent event: email_events){
            events.put(event.getMessageId(),event);
        }
        logger.info("list is ready to resending...");
        for(ProcessedEvent eventMap : events.values()){
            try {
                Customer customer = notificationService.extractCustomerFromMessage(eventMap.getPayload());
                String routingKey = eventMap.getRoutingKey();
                notificationService.deliver(eventMap,customer,routingKey);
            }catch (CustomerNotFoundException e){
                eventMap.setSmsState(eventMap.getSmsState().equals(NotificationState.SENT) ? NotificationState.SENT : NotificationState.FAILED);
                eventMap.setEmailState(eventMap.getEmailState().equals(NotificationState.SENT) ? NotificationState.SENT : NotificationState.FAILED);
                processedEventRepository.save(eventMap);
                logger.error("Customer with cif ={}, was not found during resending job",e.getCif());
            }
            catch (JsonProcessingException e) {
                eventMap.setSmsState(eventMap.getSmsState().equals(NotificationState.SENT) ? NotificationState.SENT : NotificationState.FAILED);
                eventMap.setEmailState(eventMap.getEmailState().equals(NotificationState.SENT) ? NotificationState.SENT : NotificationState.FAILED);
                processedEventRepository.save(eventMap);
                logger.error("ERROR IN PARSING PAYLOAD! {}",eventMap.getMessageId());
            }catch (Exception e){
                logger.error("Unexpected error happened during the resending job,{}",e.getMessage());
            }
        }
        logger.info("RESENDING IS DO ...");
    }
}
