package com.popman.arca.service.audit;

import com.popman.arca.repository.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class AuditService {
    private final AuditEventRepository events;
    public AuditService(AuditEventRepository events) { this.events = events; }
    public void success(AuditAction action, Long target) {
        success(action, target, AuditContext.actorId());
    }
    public void success(AuditAction action, Long target, Long actor) {
        events.insert(action, actor, target, "SUCCESS", AuditContext.correlation());
    }
    public void roleChanged(AuditAction action, Long target, String role) {
        if (action != AuditAction.ROLE_ADDED && action != AuditAction.ROLE_REMOVED) throw new IllegalArgumentException("Role action required");
        events.insertRole(action, AuditContext.actorId(), target, AuditContext.correlation(), role);
    }
}
