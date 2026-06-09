package com.eRez.notification.consumer;

import com.eRez.notification.dto.UserCreatedEvent;
import com.eRez.notification.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserCreatedConsumerTest {

    @Mock private EmailService emailService;
    @InjectMocks private UserCreatedConsumer consumer;

    @Test
    void onUserCreated_delegatesToEmailService() throws Exception {
        UserCreatedEvent event = new UserCreatedEvent();
        event.setEmail("alice@example.com");

        consumer.onUserCreated(event);

        verify(emailService).sendWelcomeEmail(event);
    }
}
