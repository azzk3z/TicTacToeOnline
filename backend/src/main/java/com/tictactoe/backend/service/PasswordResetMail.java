package com.tictactoe.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import java.net.URI;
import java.util.Set;

@Service
public class PasswordResetMail {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetMail.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;
    private final URI frontendUrl;

    public PasswordResetMail(ObjectProvider<JavaMailSender> mailSender,
                             @Value("${app.password-reset.from}") String from,
                             @Value("${app.password-reset.frontend-url}") URI frontendUrl) {
        if (frontendUrl.getScheme() == null || !Set.of("http", "https").contains(frontendUrl.getScheme())
                || frontendUrl.getHost() == null || frontendUrl.getFragment() != null) {
            throw new IllegalArgumentException("APP_FRONTEND_URL must be an absolute HTTP(S) URL without a fragment");
        }
        this.mailSender = mailSender;
        this.from = from;
        this.frontendUrl = frontendUrl;
    }

    public boolean isConfigured() {
        return !from.isBlank() && mailSender.getIfAvailable() != null;
    }

    @Async
    @TransactionalEventListener
    public void send(Request request) {
        String url = frontendUrl + "#reset=" + request.token();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(request.email());
        message.setSubject("Đặt lại mật khẩu Tic Tac Toe");
        message.setText("""
                Mở liên kết sau để đặt mật khẩu mới cho tài khoản Tic Tac Toe:

                %s

                Liên kết có hiệu lực trong %d phút và chỉ dùng được một lần.
                Nếu bạn không yêu cầu đổi mật khẩu, bạn có thể bỏ qua email này.
                """.formatted(url, AuthService.RESET_TOKEN_LIFETIME.toMinutes()));
        try {
            mailSender.getObject().send(message);
        } catch (MailException ex) {
            log.warn("Không gửi được email đặt lại mật khẩu ({})", ex.getClass().getSimpleName());
        }
    }

    record Request(String email, String token) {
    }
}
