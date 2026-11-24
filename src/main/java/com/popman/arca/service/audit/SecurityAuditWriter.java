package com.popman.arca.service.audit;

import com.popman.arca.repository.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityAuditWriter {
    private final AuditEventRepository events;
    public SecurityAuditWriter(AuditEventRepository events) { this.events = events; }
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 2)
    public void record(AuditAction action, String outcome, Long actorId, String correlation) {
        events.insert(action, actorId, null, outcome, correlation);
    }
}
