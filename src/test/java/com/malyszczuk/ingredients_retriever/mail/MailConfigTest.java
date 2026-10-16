package com.malyszczuk.ingredients_retriever.mail;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MailConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MailConfig.class)
            .withPropertyValues("pantrypal.mail.from=pantrypal@example.com");

    @Test
    void withoutAnSmtpServer_emailsAreOnlyLogged() {
        JavaMailSender smtp = mock(JavaMailSender.class);

        runner.withBean(JavaMailSender.class, () -> smtp).run(context -> {
            assertEquals(1, context.getBeansOfType(Mailer.class).size());
            context.getBean(Mailer.class).send("me@example.com", "Hello", "Body");
            verify(smtp, never()).send(forClass(SimpleMailMessage.class).capture());
        });
    }

    @Test
    void withAnSmtpServer_emailsGoThroughIt_fromTheConfiguredAddress() {
        JavaMailSender smtp = mock(JavaMailSender.class);

        runner.withPropertyValues("spring.mail.host=smtp.example.com").withBean(JavaMailSender.class, () -> smtp).run(context -> {
            assertEquals(1, context.getBeansOfType(Mailer.class).size());
            context.getBean(Mailer.class).send("me@example.com", "Hello", "Body");

            var message = forClass(SimpleMailMessage.class);
            verify(smtp).send(message.capture());
            assertEquals("pantrypal@example.com", message.getValue().getFrom());
            assertEquals("me@example.com", message.getValue().getTo()[0]);
            assertEquals("Hello", message.getValue().getSubject());
            assertEquals("Body", message.getValue().getText());
        });
    }
}
