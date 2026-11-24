package com.tictactoe.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetMailUnitTest {
    private static final String FROM = "no-reply@example.test";
    private static final String RECIPIENT = "player@example.test";
    private static final String FRONTEND = "https://game.example.test/";
    private static final String TOKEN = "t".repeat(43);

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;
    @Mock
    private JavaMailSender mailSender;

    private PasswordResetMail resetMail;

    @BeforeEach
    void setUp() {
        resetMail = new PasswordResetMail(mailSenderProvider, FROM, URI.create(FRONTEND));
        when(mailSenderProvider.getObject()).thenReturn(mailSender);
    }

    @Test
    void sendsTheOneTimeLinkToTheRequestedEmailAddress() {
        resetMail.send(new PasswordResetMail.Request(RECIPIENT, TOKEN));

        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly(RECIPIENT);
        assertThat(message.getValue().getFrom()).isEqualTo(FROM);
        assertThat(message.getValue().getText()).contains(FRONTEND + "#reset=" + TOKEN, "30 phút", "một lần");
    }

    @Test
    void handlesSmtpDeliveryFailureWithoutThrowingFromTheListener() {
        doThrow(new MailSendException("SMTP unavailable")).when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> resetMail.send(new PasswordResetMail.Request(RECIPIENT, TOKEN)));

        verify(mailSender).send(any(SimpleMailMessage.class));
    }
}
