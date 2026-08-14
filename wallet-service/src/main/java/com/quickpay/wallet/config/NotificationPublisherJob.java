package com.quickpay.wallet.config;

import com.quickpay.wallet.domain.NotificationEvent;
import com.quickpay.wallet.repository.NotificationEventRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.random.RandomGenerator;

@Component
@AllArgsConstructor
public class NotificationPublisherJob {

    private NotificationEventRepository notificationEventRepository;

    private RabbitTemplate rabbitTemplate;

    private NotificationQueueConfig notificationQueueConfig;

    private static final String MDC_KEY = "correlationId";



    private static final Logger logger = LoggerFactory.getLogger(NotificationPublisherJob.class);


    @Scheduled(fixedDelayString = "${wallet.notification-interval-ms:60000}")
    public void sendingNotification(){
        String correlationId = "relay-" + UUID.randomUUID().toString().substring(0,8);
        MDC.put(MDC_KEY,correlationId);
        try {

            List<NotificationEvent> events = notificationEventRepository.findTop100BySentAtIsNullOrderByCreatedAtAsc();
            TopicExchange topic = notificationQueueConfig.getTopic();
            for(NotificationEvent event : events){
                try {
                    // sending message
                    rabbitTemplate.convertAndSend(topic.getName(),event.getEventType(),event.getPayload(),
                            message -> {
                                message.getMessageProperties().setMessageId(event.getEventId().toString());
                                message.getMessageProperties().setContentType("application/json");
                                return message;
                            });
                    event.setSentAt(LocalDateTime.now());
                    notificationEventRepository.save(event);
                }catch(Exception e){
                    logger.warn("error when sending notification for event id = {}",event.getEventId());
                    continue;
                }
            }
        } finally {
            MDC.remove(MDC_KEY);
        }
    }


}
