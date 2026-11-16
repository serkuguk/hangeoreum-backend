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

    // ponytail: second auth mail lives here to reuse the sender wiring (and its test mocks);
    // rename the class to AuthMailSender when a third mail appears
    public void sendEmailVerification(String email, String token) {
        String link = UriComponentsBuilder.fromUriString(frontUrl)
                .path("/auth/verify-email")
                .fragment("token=" + token)
                .build()
                .toUriString();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Подтверждение email 한걸음");
        message.setText("Чтобы завершить регистрацию, перейдите по ссылке:\n\n" + link
                + "\n\nСсылка действует 24 часа. Если вы не регистрировались, просто проигнорируйте это письмо.");
        mailSender.send(message);
    }
}
