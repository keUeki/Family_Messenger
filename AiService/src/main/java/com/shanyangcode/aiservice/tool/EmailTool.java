package com.shanyangcode.aiservice.tool;

import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class EmailTool {

    @Resource
    private JavaMailSender mailSender;

    // Read the sender from configuration instead of hard-coding it
    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * Sends a plain-text email
     * The description tells the model what each parameter means:
     * targetEmail: the recipient's email address
     * subject: the email subject
     * content: the email body
     */
    @Tool("Send an email to a specific user.")
    public String sendEmail(String targetEmail, String subject, String content) {
        try {
            log.info("Tool invoked: sending email -> To: {}, Subject: {}", targetEmail, subject);
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(targetEmail);
            message.setSubject(subject);
            message.setText(content);

            mailSender.send(message);
            
            log.info("Email sent successfully");
            return "Email sent successfully to " + targetEmail;
        } catch (Exception e) {
            log.error("Failed to send the email", e);
            return "Failed to send the email: " + e.getMessage();
        }
    }
}