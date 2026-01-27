package com.popman.arca.service.impl;

import com.popman.arca.entity.BannedEmail;
import com.popman.arca.repository.BannedEmailRepository;
import com.popman.arca.service.BannedEmailService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BannedEmailServiceImplementation implements BannedEmailService {

    private static final Logger logger = LoggerFactory.getLogger(BannedEmailServiceImplementation.class);
    private final BannedEmailRepository bannedEmailRepository;

    public BannedEmailServiceImplementation(BannedEmailRepository bannedEmailRepository) {
        this.bannedEmailRepository = bannedEmailRepository;
    }

    @Override
    public boolean isBannedV1(String email) {
        if (email == null || email.trim().isEmpty()) return false;
        return bannedEmailRepository.existsByEmailIgnoreCase(email.trim());
    }

    @Override
    @Transactional
    public String banV1(String email, String reason) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        String normalized = email.trim();
        if (bannedEmailRepository.existsByEmailIgnoreCase(normalized)) {
            throw new IllegalArgumentException("Email is already banned");
        }
        BannedEmail entry = new BannedEmail(normalized, reason);
        bannedEmailRepository.save(entry);
        logger.info("Banned email: {}", normalized);
        return "Email banned successfully";
    }

    @Override
    @Transactional
    public String unbanV1(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        String normalized = email.trim();
        if (!bannedEmailRepository.existsByEmailIgnoreCase(normalized)) {
            throw new IllegalArgumentException("Email is not banned");
        }
        bannedEmailRepository.deleteByEmailIgnoreCase(normalized);
        logger.info("Unbanned email: {}", normalized);
        return "Email unbanned successfully";
    }

    @Override
    public List<BannedEmail> listAllV1() {
        return bannedEmailRepository.findAll();
    }
}
