package com.hangeoreum.api.identity.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class PasswordResetMailSender {

    private final JavaMailSender mailSender;

    @Value("${app.front-url}")
    private String frontUrl;

    @Value("${app.mail.from}")
    private String from;

    public void send(String email, String rawToken) {
        String link = UriComponentsBuilder.fromUriString(frontUrl)
                .path("/auth/reset-password")
                .fragment("token=" + rawToken)
                .build()
                .toUriString();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Восстановление пароля 한걸음");
        message.setText("Чтобы задать новый пароль, перейдите по ссылке:\n\n" + link
                + "\n\nСсылка действует 30 минут.");
        mailSender.send(message);
    }
}
