package com.eRez.notification.consumer;

import com.eRez.notification.dto.RouteRecalculatedEvent;
import com.eRez.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RouteRecalculatedConsumer {

    private final EmailService emailService;

    @RabbitListener(queues = "${rabbitmq.queue.route-recalculated}")
    public void onRouteRecalculated(RouteRecalculatedEvent event) throws Exception {
        log.info("Received route-recalculated event: {} ↔ {}, {} recipient(s)",
                event.getNodeA(), event.getNodeB(),
                event.getRecipients() != null ? event.getRecipients().size() : 0);
        emailService.sendRouteUpdateEmail(event);
    }
}