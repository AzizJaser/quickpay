package com.quickpay.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Topology {

    @Value("${notification.queue-name}")
    private String queueName;
    @Value("${notification.events-exchange}")
    private String exchangeName;
    @Value("${notification.routing-key}")
    private String routingKey;
    @Value("${notification.bill-routing-key}")
    private String billRoutingKey;

    @Bean
    public Queue notificationQueue(){
        return new Queue(queueName,true);
    }

    @Bean
    public TopicExchange eventsExchange(){
        return new TopicExchange(exchangeName);
    }

    @Bean
    public Binding notificationBinding(Queue notificationQueue,TopicExchange eventsExchange){
        return BindingBuilder.bind(notificationQueue).to(eventsExchange).with(routingKey);
    }

    @Bean
    public Binding billNotificationBinding(Queue notificationQueue,TopicExchange eventsExchange){
        return BindingBuilder.bind(notificationQueue).to(eventsExchange).with(billRoutingKey);
    }
}
