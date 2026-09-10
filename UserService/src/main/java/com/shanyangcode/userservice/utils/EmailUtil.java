package com.shanyangcode.userservice.utils;

import com.shanyangcode.userservice.constants.UserConstant;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class EmailUtil {

    @Resource
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendEmail(String targetEmail, String randomCode) {
        try {
            log.info("Sending email -> To: {}, Subject: {}", targetEmail, UserConstant.EMAIL_SUBJECT);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(targetEmail);
            message.setSubject(UserConstant.EMAIL_SUBJECT);
            message.setText("Your verification code is: " + randomCode + " (valid for five minutes)");
            mailSender.send(message);
            log.info("Email sent successfully");
        } catch (Exception e) {
            log.error("Failed to send the email", e);
        }
    }
}