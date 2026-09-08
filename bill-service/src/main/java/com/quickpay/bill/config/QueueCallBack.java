package com.quickpay.bill.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class QueueCallBack {

    private final RabbitTemplate rabbitTemplate;

    private static final Logger logger = LoggerFactory.getLogger(QueueCallBack.class);

    public QueueCallBack(RabbitTemplate rabbitTemplate){
        this.rabbitTemplate = rabbitTemplate;

        rabbitTemplate.setReturnsCallback(returnedMessage -> {
            logger.error("UNROUTABLE - event {} to exchange {} with key {} was returned: {}",
                    returnedMessage.getMessage().getMessageProperties().getMessageId(),
                    returnedMessage.getExchange(),
                    returnedMessage.getRoutingKey(),
                    returnedMessage.getReplyText());
        });
    }

}
