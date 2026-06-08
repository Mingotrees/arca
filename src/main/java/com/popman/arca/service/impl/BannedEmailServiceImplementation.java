package com.popman.arca.service.impl;

import com.popman.arca.entity.BannedEmail;
import com.popman.arca.repository.BannedEmailRepository;
import com.popman.arca.service.BannedEmailService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

@Service
public class BannedEmailServiceImplementation implements BannedEmailService {

    private static final Logger logger = LoggerFactory.getLogger(BannedEmailServiceImplementation.class);
    private static final String BAN_CACHE = "bannedEmails.byAddress";
    private final BannedEmailRepository bannedEmailRepository;
    private final CacheManager cacheManager;

    public BannedEmailServiceImplementation(BannedEmailRepository bannedEmailRepository, CacheManager cacheManager) {
        this.bannedEmailRepository = bannedEmailRepository;
        this.cacheManager = cacheManager;
    }

    @Override
    public boolean isBannedV1(String email) {
        if (email == null || email.trim().isEmpty()) return false;

        String normalized = email.trim().toLowerCase();
        Boolean cached = getCachedValue(normalized);
        if (cached != null) {
            logger.debug("Cache hit: banned email check for {}", normalized);
            return cached;
        }

        boolean banned = bannedEmailRepository.existsByEmailIgnoreCase(normalized);
        logger.debug("Cache miss: banned email check for {}", normalized);
        putCachedValue(normalized, banned);
        return banned;
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
        try {
            bannedEmailRepository.saveAndFlush(entry);
            evictCachedValue(normalized);
            logger.info("Banned email: {}", normalized);
            return "Email banned successfully";
        } catch (DataIntegrityViolationException ex) {
            // Already banned by concurrent request
            evictCachedValue(normalized);
            logger.warn("Attempted to ban already-banned email: {}", normalized);
            throw new IllegalArgumentException("Email is already banned");
        }
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
        evictCachedValue(normalized);
        logger.info("Unbanned email: {}", normalized);
        return "Email unbanned successfully";
    }

    @Override
    public List<BannedEmail> listAllV1() {
        return bannedEmailRepository.findAll();
    }

    private Boolean getCachedValue(String normalizedEmail) {
        Cache cache = cacheManager.getCache(BAN_CACHE);
        return cache == null ? null : cache.get(normalizedEmail, Boolean.class);
    }

    private void putCachedValue(String normalizedEmail, boolean banned) {
        Cache cache = cacheManager.getCache(BAN_CACHE);
        if (cache != null) {
            cache.put(normalizedEmail, banned);
        }
    }

    private void evictCachedValue(String normalizedEmail) {
        Optional.ofNullable(cacheManager.getCache(BAN_CACHE))
                .ifPresent(cache -> cache.evict(normalizedEmail));
    }
}
