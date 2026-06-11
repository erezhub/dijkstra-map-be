package com.eRez.map.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MapRabbitConfig {

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.queue.route-recalculation}")
    private String routeRecalculationQueue;

    @Value("${rabbitmq.queue.route-recalculated}")
    private String routeRecalculatedQueue;

    @Value("${rabbitmq.routing-key.route-recalculated}")
    private String routeRecalculatedRoutingKey;

    @Bean
    public TopicExchange dijkstraExchange() {
        return new TopicExchange(exchange);
    }

    @Bean
    public Queue routeRecalculationQueue() {
        return new Queue(routeRecalculationQueue, true);
    }

    @Bean
    public Binding routeRecalculationBinding() {
        return BindingBuilder.bind(routeRecalculationQueue())
                .to(dijkstraExchange())
                .with("map.node.*");
    }

    @Bean
    public Queue routeRecalculatedQueue() {
        return new Queue(routeRecalculatedQueue, true);
    }

    @Bean
    public Binding routeRecalculatedBinding() {
        return BindingBuilder.bind(routeRecalculatedQueue())
                .to(dijkstraExchange())
                .with(routeRecalculatedRoutingKey);
    }

    @Bean
    public JacksonJsonMessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        return factory;
    }
}
