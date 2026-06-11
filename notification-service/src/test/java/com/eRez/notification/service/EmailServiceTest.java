package com.eRez.notification.service;

import com.eRez.notification.dto.RouteRecalculatedEvent;
import com.eRez.notification.dto.UserCreatedEvent;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private JavaMailSender mailSender;
    @InjectMocks private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "from", "noreply@dijkstra.local");
        ReflectionTestUtils.setField(emailService, "fromName", "Dijkstra Service");
    }

    private UserCreatedEvent event() {
        UserCreatedEvent e = new UserCreatedEvent();
        e.setId("id-1");
        e.setUsername("Alice");
        e.setEmail("alice@example.com");
        e.setRole("MANAGER");
        return e;
    }

    @Test
    void sendWelcomeEmail_sendsToRecipient() throws Exception {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendWelcomeEmail(event());

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getAllRecipients()[0].toString()).isEqualTo("alice@example.com");
    }

    @Test
    void sendWelcomeEmail_setsFromWithDisplayName() throws Exception {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendWelcomeEmail(event());

        String from = mimeMessage.getFrom()[0].toString();
        assertThat(from).contains("Dijkstra Service");
        assertThat(from).contains("noreply@dijkstra.local");
    }

    @Test
    void sendWelcomeEmail_setsSubject() throws Exception {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendWelcomeEmail(event());

        assertThat(mimeMessage.getSubject()).isEqualTo("Welcome to Dijkstra Map");
    }

    @Test
    void sendWelcomeEmail_bodyContainsUsernameAndRole() throws Exception {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendWelcomeEmail(event());

        String body = mimeMessage.getContent().toString();
        assertThat(body).contains("Alice");
        assertThat(body).contains("MANAGER");
    }

    private RouteRecalculatedEvent routeEvent() {
        RouteRecalculatedEvent e = new RouteRecalculatedEvent();
        e.setNodeA("A");
        e.setNodeB("C");
        e.setDistance(15);
        e.setRecipients(List.of("user1@x.com", "user2@x.com"));
        return e;
    }

    @Test
    void sendRouteUpdateEmail_sendsToEachRecipient() throws Exception {
        MimeMessage m1 = new MimeMessage((Session) null);
        MimeMessage m2 = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(m1, m2);

        emailService.sendRouteUpdateEmail(routeEvent());

        verify(mailSender, times(2)).send(any(MimeMessage.class));
        assertThat(m1.getAllRecipients()[0].toString()).isEqualTo("user1@x.com");
        assertThat(m2.getAllRecipients()[0].toString()).isEqualTo("user2@x.com");
    }

    @Test
    void sendRouteUpdateEmail_setsSubjectWithNodeNames() throws Exception {
        MimeMessage msg = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(msg);
        RouteRecalculatedEvent event = new RouteRecalculatedEvent();
        event.setNodeA("X");
        event.setNodeB("Y");
        event.setDistance(5);
        event.setRecipients(List.of("r@x.com"));

        emailService.sendRouteUpdateEmail(event);

        assertThat(msg.getSubject()).contains("X").contains("Y");
    }

    @Test
    void sendRouteUpdateEmail_bodyContainsDistance() throws Exception {
        MimeMessage msg = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(msg);

        emailService.sendRouteUpdateEmail(routeEvent());

        String body = msg.getContent().toString();
        assertThat(body).contains("15");
    }
}
