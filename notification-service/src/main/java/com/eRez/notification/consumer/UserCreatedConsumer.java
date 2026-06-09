package com.eRez.notification.consumer;

import com.eRez.notification.dto.UserCreatedEvent;
import com.eRez.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserCreatedConsumer {

    private final EmailService emailService;

    @RabbitListener(queues = "${rabbitmq.queue.user-created}")
    public void onUserCreated(UserCreatedEvent event) throws Exception {
        log.info("Received user-created event for '{}'", event.getEmail());
        emailService.sendWelcomeEmail(event);
    }
}
