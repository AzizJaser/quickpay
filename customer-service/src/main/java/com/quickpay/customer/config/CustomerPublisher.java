package com.quickpay.customer.config;

import com.quickpay.customer.domain.CustomerOutboxEvent;
import com.quickpay.customer.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CustomerPublisher {

    private final OutboxEventRepository outboxEventRepository;

    private final RabbitTemplate rabbitTemplate;

    private final CustomerQueueConfig customerQueueConfig;

    private static final String MDC_KEY = "correlationId";

    private final static Logger logger = LoggerFactory.getLogger(CustomerPublisher.class);

    @Scheduled(fixedDelayString = "${customer.notification-interval-ms:2000}")
    public void sendEvent(){
        String correlationId = "cust-relay-" + UUID.randomUUID().toString().substring(0,8);
        MDC.put(MDC_KEY,correlationId);
        try {
            List<CustomerOutboxEvent> events = outboxEventRepository.findTop100BySentAtIsNullOrderByCreatedAtAsc();
            TopicExchange topic = customerQueueConfig.getTopic();
            for (CustomerOutboxEvent event : events){
                try {
                    rabbitTemplate.convertAndSend(topic.getName(),event.getEventType(),event.getPayload(),message -> {
                        message.getMessageProperties().setMessageId(event.getEventId().toString());
                        message.getMessageProperties().setContentType("application/json");
                        message.getMessageProperties().setCorrelationId(event.getCorrelationId());
                        return message;
                    });
                    event.setSentAt(Timestamp.valueOf(LocalDateTime.now()));
                    logger.info("event id {} of type {} is sent with correlation id {}",event.getEventId(),event.getEventType(),event.getCorrelationId());
                    outboxEventRepository.save(event);
                }catch (Exception e){
                    logger.warn("error when sending notification for event id = {}",event.getEventId());
                    continue;
                }
            }
        } finally {
            MDC.remove(MDC_KEY);
        }

    }
}
