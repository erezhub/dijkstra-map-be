package com.eRez.notification.service;

import com.eRez.notification.dto.RouteRecalculatedEvent;
import com.eRez.notification.dto.UserCreatedEvent;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${notification.mail.from}")
    private String from;

    @Value("${notification.mail.from-name}")
    private String fromName;

    public void sendWelcomeEmail(UserCreatedEvent event) throws Exception {
        sendEmail(event.getEmail(), "Welcome to Dijkstra Map",
                "Hi " + event.getUsername() + ",\n\n" +
                "Your account has been created.\n\n" +
                "Role: " + event.getRole() + "\n" +
                "Email: " + event.getEmail() + "\n\n" +
                "Your temporary password is: " + event.getTempPassword() + "\n" +
                "It can be used once and expires in 10 minutes.\n" +
                "After logging in you will be asked to set a permanent password.");
        log.info("Welcome email sent to '{}'", event.getEmail());
    }

    public void sendRouteUpdateEmail(RouteRecalculatedEvent event) throws Exception {
        for (String recipient : event.getRecipients()) {
            sendEmail(recipient,
                    "Route update: " + event.getNodeA() + " → " + event.getNodeB(),
                    "The route from " + event.getNodeA() + " to " + event.getNodeB() +
                    " has been updated.\n\nNew distance: " + event.getDistance());
            log.info("Route update email sent to '{}' for route {} ↔ {}",
                    recipient, event.getNodeA(), event.getNodeB());
        }
    }

    private void sendEmail(String to, String subject, String body) throws Exception {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage);
        helper.setFrom(new InternetAddress(from, fromName));
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body);
        mailSender.send(mimeMessage);
    }
}
