package com.popman.arca.service;

import com.popman.arca.dto.v1.user.UserStatsResponse;
import com.popman.arca.dto.v1.subject.SubjectStatsResponse;
import com.popman.arca.repository.ProfileStatisticsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ProfileStatisticsService {
    private final ProfileStatisticsRepository repository;
    public ProfileStatisticsService(ProfileStatisticsRepository repository) { this.repository = repository; }

    public UserStatsResponse userStats(Long id) {
        requirePositive(id);
        if (!repository.activeUserExists(id)) throw missing();
        return repository.user(id);
    }
    public SubjectStatsResponse subjectStats(Long id) {
        requirePositive(id);
        if (!repository.subjectExists(id)) throw missing();
        return repository.subject(id);
    }
    private void requirePositive(Long id) { if (id == null || id <= 0) throw new IllegalArgumentException("Profile ID must be positive"); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"); }
}
