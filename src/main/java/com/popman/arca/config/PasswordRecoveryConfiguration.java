package com.popman.arca.config;

import com.popman.arca.service.recovery.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(PasswordRecoveryProperties.class)
@EnableScheduling
public class PasswordRecoveryConfiguration {
    @Bean @ConditionalOnMissingBean(Clock.class)
    Clock recoveryClock() { return Clock.systemUTC(); }

    @Bean @ConditionalOnMissingBean(RecoveryEmailSender.class)
    RecoveryEmailSender recoveryEmailSender(ObjectProvider<JavaMailSender> mail, PasswordRecoveryProperties settings, Environment environment) {
        return new SmtpRecoveryEmailSender(mail, settings, environment);
    }

    @Bean
    SmartInitializingSingleton recoveryReadiness(PasswordRecoveryProperties settings, Environment environment, ObjectProvider<RecoveryEmailSender> sender) {
        return () -> { settings.validate(environment); if (settings.enabled()) sender.getObject().validateConfiguration(); };
    }

    @Bean
    ThreadPoolTaskExecutor recoveryMailExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2); executor.setMaxPoolSize(2); executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("recovery-mail-");
        executor.setWaitForTasksToCompleteOnShutdown(true); executor.setAwaitTerminationSeconds(5);
        return executor;
    }
}
