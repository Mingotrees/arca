package com.popman.arca.exceptions;

public class RecoveryRateLimitException extends RuntimeException {
    private final long retryAfter;
    public RecoveryRateLimitException(long retryAfter) {
        super("Too many password recovery attempts; retry later"); this.retryAfter = retryAfter;
    }
    public long retryAfter() { return retryAfter; }
}
