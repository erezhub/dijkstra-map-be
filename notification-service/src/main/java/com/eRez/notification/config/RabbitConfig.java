package com.eRez.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.queue.user-created}")
    private String userCreatedQueue;

    @Value("${rabbitmq.routing-key.user-created}")
    private String userCreatedRoutingKey;

    @Bean
    public TopicExchange dijkstraExchange() {
        return new TopicExchange(exchange);
    }

    @Bean
    public Queue userCreatedQueue() {
        return new Queue(userCreatedQueue, true);
    }

    @Bean
    public Binding userCreatedBinding() {
        return BindingBuilder.bind(userCreatedQueue()).to(dijkstraExchange()).with(userCreatedRoutingKey);
    }

    @Bean
    public JacksonJsonMessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        return factory;
    }
}
