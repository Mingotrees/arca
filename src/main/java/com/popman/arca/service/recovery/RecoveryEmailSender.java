package com.popman.arca.service.recovery;

import java.net.URI;

public interface RecoveryEmailSender {
    void sendResetLink(String email, URI resetLink);
    default void validateConfiguration() {}
}
