package com.popman.arca.service.recovery;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.net.URI;
import java.util.concurrent.RejectedExecutionException;

@Service
public class RecoveryMailDispatcher {
    private final RecoveryEmailSender sender;
    private final ThreadPoolTaskExecutor executor;
    private final MeterRegistry meters;
    public RecoveryMailDispatcher(RecoveryEmailSender sender, @Qualifier("recoveryMailExecutor") ThreadPoolTaskExecutor executor, MeterRegistry meters) {
        this.sender = sender; this.executor = executor; this.meters = meters;
    }

    public void afterCommit(String email, URI link) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { enqueue(email, link); }
        });
    }

    private void enqueue(String email, URI link) {
        try {
            executor.execute(() -> {
                String outcome = "success";
                try { sender.sendResetLink(email, link); }
                catch (RuntimeException ex) {
                    outcome = "failed";
                    // Provider exception messages/stack traces can contain the reset URL or credentials.
                    LoggerFactory.getLogger(RecoveryMailDispatcher.class).warn("password_recovery_delivery outcome=failed");
                } finally { meters.counter("arca.auth.password.delivery", "outcome", outcome).increment(); }
            });
        } catch (RejectedExecutionException ex) {
            LoggerFactory.getLogger(RecoveryMailDispatcher.class).warn("password_recovery_delivery outcome=queue_full");
            meters.counter("arca.auth.password.delivery", "outcome", "queue_full").increment();
        }
    }
}
