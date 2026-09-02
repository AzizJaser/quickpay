package com.quickpay.bill.config;

import com.quickpay.bill.domain.BillOutboxEvent;
import com.quickpay.bill.repository.NotificationEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NotificationPublisherJob {

    private final NotificationEventRepository notificationEventRepository;

    private final RabbitTemplate rabbitTemplate;

    private final NotificationQueueConfig notificationQueueConfig;

    private static final String MDC_KEY = "correlationId";

    private final static Logger logger = LoggerFactory.getLogger(NotificationPublisherJob.class);


    @Scheduled(fixedDelayString = "${bill.notification-interval-ms:2000}")
    public void sendingNotification(){
        String correlationId = "bill-relay-" + UUID.randomUUID().toString().substring(0,8);
        MDC.put(MDC_KEY,correlationId);
        try {
            List<BillOutboxEvent> events = notificationEventRepository.findTop100BySentAtIsNullOrderByCreatedAtAsc();
            TopicExchange topic = notificationQueueConfig.getTopic();

            for(BillOutboxEvent event : events){
                try {

                    rabbitTemplate.convertAndSend(topic.getName(),event.getEventType(),event.getPayload(),
                            message -> {
                                message.getMessageProperties().setMessageId(event.getEventId().toString());
                                message.getMessageProperties().setContentType("application/json");
                                message.getMessageProperties().setCorrelationId(event.getCorrelationId());
                                return message;
                            });
                    event.setSentAt(LocalDateTime.now());
                    logger.info("event id {} of type {} is sent with correlation id {}",event.getEventId(),event.getEventType(),event.getCorrelationId());
                    notificationEventRepository.save(event);
                } catch (Exception e) {
                    logger.warn("error when sending notification for event id = {}",event.getEventId());
                    continue;
                }
            }
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
