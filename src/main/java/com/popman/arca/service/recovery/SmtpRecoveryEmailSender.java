package com.popman.arca.service.recovery;

import com.popman.arca.config.PasswordRecoveryProperties;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import java.net.URI;

public class SmtpRecoveryEmailSender implements RecoveryEmailSender {
    private final ObjectProvider<JavaMailSender> sender;
    private final PasswordRecoveryProperties settings;
    private final Environment environment;
    public SmtpRecoveryEmailSender(ObjectProvider<JavaMailSender> sender, PasswordRecoveryProperties settings, Environment environment) {
        this.sender = sender; this.settings = settings; this.environment = environment;
    }

    @Override public void validateConfiguration() {
        if (sender.getIfAvailable() == null) throw new IllegalArgumentException("Configure SMTP before enabling password recovery");
        try { new InternetAddress(settings.from(), true).validate(); }
        catch (Exception ex) { throw new IllegalArgumentException("Invalid password recovery sender address"); }
        if (sender.getObject() instanceof JavaMailSenderImpl smtp) {
            String protocol = smtp.getProtocol();
            if (protocol == null) protocol = smtp.getJavaMailProperties().getProperty("mail.transport.protocol", "smtp");
            if (!"smtp".equals(protocol) && !"smtps".equals(protocol)) {
                throw new IllegalArgumentException("Recovery email transport must be SMTP or SMTPS");
            }
            if (smtp.getHost() == null || smtp.getHost().isBlank()) throw new IllegalArgumentException("Configure a nonblank SMTP host");
            if (Boolean.parseBoolean(smtp.getJavaMailProperties().getProperty("mail.debug"))) {
                throw new IllegalArgumentException("Mail protocol debug must be disabled for password recovery");
            }
            if (!PasswordRecoveryProperties.development(environment)) {
                var properties = smtp.getJavaMailProperties();
                boolean tls = "smtps".equals(protocol) || Boolean.parseBoolean(properties.getProperty("mail.smtp.ssl.enable"))
                        || Boolean.parseBoolean(properties.getProperty("mail.smtp.starttls.enable")) && Boolean.parseBoolean(properties.getProperty("mail.smtp.starttls.required"));
                if (!tls) throw new IllegalArgumentException("Production recovery SMTP requires TLS");
                if (!Boolean.parseBoolean(properties.getProperty("mail." + protocol + ".ssl.checkserveridentity"))) {
                    throw new IllegalArgumentException("Production recovery SMTP requires server hostname verification");
                }
                if (smtp.getUsername() == null || smtp.getUsername().isBlank() || smtp.getPassword() == null || smtp.getPassword().isBlank()) {
                    throw new IllegalArgumentException("Configure production SMTP credentials");
                }
            }
            String prefix = "mail." + protocol + ".";
            for (String property : new String[]{prefix + "connectiontimeout", prefix + "timeout", prefix + "writetimeout"}) {
                try { if (Integer.parseInt(smtp.getJavaMailProperties().getProperty(property, "0")) <= 0) throw new NumberFormatException(); }
                catch (NumberFormatException ex) { throw new IllegalArgumentException("Recovery SMTP requires finite positive timeouts"); }
            }
        }
    }

    @Override public void sendResetLink(String email, URI link) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(settings.from()); message.setTo(email); message.setSubject("Reset your Arca password");
        message.setText("Use this link to reset your Arca password within " + settings.tokenTtl().toMinutes() + " minutes:\n\n"
                + link + "\n\nIf you did not request this, ignore this email. A successful reset signs out refresh sessions.");
        sender.getObject().send(message);
    }
}
