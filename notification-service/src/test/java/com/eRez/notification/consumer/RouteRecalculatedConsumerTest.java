package com.eRez.notification.consumer;

import com.eRez.notification.dto.RouteRecalculatedEvent;
import com.eRez.notification.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RouteRecalculatedConsumerTest {

    @Mock private EmailService emailService;
    @InjectMocks private RouteRecalculatedConsumer consumer;

    @Test
    void onRouteRecalculated_delegatesToEmailService() throws Exception {
        RouteRecalculatedEvent event = new RouteRecalculatedEvent();
        event.setNodeA("A");
        event.setNodeB("C");
        event.setDistance(10);
        event.setRecipients(List.of("user@x.com", "mgr@x.com"));

        consumer.onRouteRecalculated(event);

        verify(emailService).sendRouteUpdateEmail(event);
    }
}