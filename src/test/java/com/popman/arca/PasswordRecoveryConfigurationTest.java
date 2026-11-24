package com.popman.arca;

import com.popman.arca.config.PasswordRecoveryConfiguration;
import com.popman.arca.service.recovery.RecoveryEmailSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import static org.junit.jupiter.api.Assertions.*;

class PasswordRecoveryConfigurationTest {
    private final ApplicationContextRunner configuration = new ApplicationContextRunner()
            .withUserConfiguration(PasswordRecoveryConfiguration.class, com.popman.arca.service.recovery.RecoveryClientIpResolver.class)
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"));
    private final ApplicationContextRunner enabled = configuration.withPropertyValues("app.password-recovery.enabled=true",
            "app.password-recovery.reset-link-base-url=https://client.example.test/reset-password", "app.password-recovery.from=arca@example.test",
            "app.password-recovery.rate-key-secret=dGVzdC1vbmx5LXJhdGUta2V5LXdpdGgtYXQtbGVhc3QtMzItYnl0ZXM=");

    @Test
    void disabledRecoveryNeedsNoMailProvider() { configuration.run(context -> assertNull(context.getStartupFailure())); }

    @Test
    void enabledRecoveryWithoutProviderFailsStartup() { enabled.run(context -> assertNotNull(context.getStartupFailure())); }

    @ParameterizedTest
    @ValueSource(strings = {"app.password-recovery.reset-link-base-url=http://client.example.test/reset",
            "app.password-recovery.reset-link-base-url=https://user@client.example.test/reset",
            "app.password-recovery.reset-link-base-url=https://client.example.test/reset?token=bad",
            "app.password-recovery.reset-link-base-url=https://client.example.test/reset#bad",
            "app.password-recovery.rate-key-secret=c2hvcnQ=", "app.password-recovery.token-ttl=PT0S", "app.password-recovery.email-limit=0",
            "server.forward-headers-strategy=native", "server.forward-headers-strategy=framework",
            "app.password-recovery.trusted-proxies=0.0.0.0/00", "app.password-recovery.trusted-proxies=::/000"})
    void rejectsUnsafeEnabledSettings(String setting) {
        enabled.withBean(RecoveryEmailSender.class, () -> (email, link) -> {}).withPropertyValues(setting)
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void customProviderCanEnableRecoveryWithoutSmtp() {
        enabled.withBean(RecoveryEmailSender.class, () -> (email, link) -> {}).run(context -> assertNull(context.getStartupFailure()));
    }

    @Test
    void smtpRequiresTlsAndFiniteTimeoutsWithoutConnectingAtStartup() {
        var valid = smtp();
        enabled.withBean(JavaMailSender.class, () -> valid).run(context -> assertNull(context.getStartupFailure()));
        var insecure = smtp(); insecure.getJavaMailProperties().setProperty("mail.smtp.starttls.required", "false");
        enabled.withBean(JavaMailSender.class, () -> insecure).run(context -> assertNotNull(context.getStartupFailure()));
        var unbounded = smtp(); unbounded.getJavaMailProperties().remove("mail.smtp.timeout");
        enabled.withBean(JavaMailSender.class, () -> unbounded).run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void smtpsMustValidateItsOwnTimeoutsRatherThanSmtpDefaults() {
        var sender = smtp(); sender.setProtocol("smtps");
        sender.getJavaMailProperties().setProperty("mail.smtps.ssl.checkserveridentity", "true");
        enabled.withBean(JavaMailSender.class, () -> sender).run(context -> assertNotNull(context.getStartupFailure()));
        for (String suffix : new String[]{"connectiontimeout", "timeout", "writetimeout"}) sender.getJavaMailProperties().setProperty("mail.smtps." + suffix, "5000");
        enabled.withBean(JavaMailSender.class, () -> sender).run(context -> assertNull(context.getStartupFailure()));
    }

    @Test
    void productionSmtpRequiresServerHostnameVerification() {
        var sender = smtp(); sender.getJavaMailProperties().setProperty("mail.smtp.ssl.checkserveridentity", "false");
        enabled.withBean(JavaMailSender.class, () -> sender).run(context -> assertNotNull(context.getStartupFailure()));
    }

    private JavaMailSenderImpl smtp() {
        var sender = new JavaMailSenderImpl(); sender.setHost("relay.example.test"); sender.setUsername("fixture"); sender.setPassword("fixture-password");
        var properties = sender.getJavaMailProperties(); properties.setProperty("mail.smtp.starttls.enable", "true");
        properties.setProperty("mail.smtp.starttls.required", "true");
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        for (String suffix : new String[]{"connectiontimeout", "timeout", "writetimeout"}) properties.setProperty("mail.smtp." + suffix, "5000");
        return sender;
    }
}
