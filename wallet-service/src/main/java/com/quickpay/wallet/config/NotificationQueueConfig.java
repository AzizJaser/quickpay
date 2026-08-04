package com.quickpay.wallet.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationQueueConfig {

    @Value("${wallet.events-exchange}")
    private String topicName;

    @Bean
    public TopicExchange getTopic(){
        return new TopicExchange(topicName);
    }


}
