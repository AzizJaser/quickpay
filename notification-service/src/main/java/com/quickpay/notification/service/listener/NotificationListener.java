package com.quickpay.notification.service.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quickpay.notification.client.NotificationProviderClient;
import com.quickpay.notification.domain.Customer;
import com.quickpay.notification.domain.ProcessedEvent;
import com.quickpay.notification.dto.event.NotificationEvent;
import com.quickpay.notification.dto.response.ProviderResponse;
import com.quickpay.notification.enums.NotificationStatus;
import com.quickpay.notification.exception.CustomerNotFoundException;
import com.quickpay.notification.repository.CustomerRepository;
import com.quickpay.notification.repository.ProcessedEventRepository;
import com.quickpay.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class NotificationListener {

    private final ProcessedEventRepository processedEventRepository;

    private final CustomerRepository customerRepository;

    private final ObjectMapper objectMapper;

    private final NotificationProviderClient notificationProviderClient;

    private final NotificationService notificationService;

    private static final Logger logger = LoggerFactory.getLogger(NotificationListener.class);


    public NotificationListener(ProcessedEventRepository processedEventRepository, CustomerRepository customerRepository, ObjectMapper objectMapper, NotificationProviderClient notificationProviderClient, NotificationService notificationService){
        this.processedEventRepository = processedEventRepository;
        this.customerRepository = customerRepository;
        this.objectMapper = objectMapper;
        this.notificationProviderClient = notificationProviderClient;
        this.notificationService = notificationService;
    }


    @RabbitListener(queues = "${notification.queue-name}")
    public void NotificationListening(String payload, @Header(AmqpHeaders.MESSAGE_ID) String messageId,@Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        try {
            NotificationEvent notificationEvent = parsingNotificationMessage(payload);
            Optional<ProcessedEvent> event_present = processedEventRepository.findByMessageId(messageId);
            if(event_present.isPresent()){ // event found
                ProcessedEvent event = event_present.get();
                Customer customer = notificationService.extractCustomerFromMessage(payload);
                ProviderResponse smsResponse;
                ProviderResponse emailResponse;
                if(event.isEmailStatus() && event.isSmsStatus()){ // in case both notification is done
                    return;
                }else{ // in case one of the notification or both is not sent yet
                    notificationService.deliver(event,customer,routingKey);
                }
            }else { // event wasn't found
                NotificationEvent receivedEvent = objectMapper.readValue(payload, NotificationEvent.class);
                String cif = receivedEvent.cif();
                Customer customer = customerRepository.findCustomerByCif(cif).orElseThrow(() -> new CustomerNotFoundException(cif));
                ProcessedEvent event = new ProcessedEvent(messageId, false, null,false,null,LocalDateTime.now(),payload,0,LocalDateTime.now(),routingKey);
                processedEventRepository.save(event);
                notificationService.deliver(event,customer,routingKey);
            }
        } catch (JsonProcessingException e) {
            logger.error("malformed payload for message {} — dropping", messageId, e);
        } catch (CustomerNotFoundException e) {
            logger.warn("no customer for cif {} (message {}) — dropping", e.getCif(), messageId);
        } catch (Exception e) {
            logger.error("unexpected failure handling message {} — dropping", messageId, e);
        }
    }

    public NotificationEvent parsingNotificationMessage(String payload) throws JsonProcessingException {
        return  objectMapper.readValue(payload, NotificationEvent.class);
    }


}
