package com.popman.arca.service.impl;

import com.popman.arca.dto.v1.school.SchoolRequest;
import com.popman.arca.dto.v1.school.SchoolResponse;
import com.popman.arca.dto.v1.department.DepartmentResponse;
import com.popman.arca.entity.School;
import com.popman.arca.repository.SchoolRepository;
import com.popman.arca.service.SchoolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SchoolServiceImplementation implements SchoolService {

    private static final Logger logger = LoggerFactory.getLogger(SchoolServiceImplementation.class);
    private static final String SCHOOL_BY_ID_CACHE = "schools.byId";
    private static final String SCHOOL_LIST_CACHE = "schools.all";

    private final SchoolRepository schoolRepository;
    private final CacheManager cacheManager;

    public SchoolServiceImplementation(SchoolRepository schoolRepository, CacheManager cacheManager) {
        this.schoolRepository = schoolRepository;
        this.cacheManager = cacheManager;
    }

    @Override
    public SchoolResponse addSchoolV1(SchoolRequest request) {
        School school = new School();
        school.setName(request.getName());
        School saved = schoolRepository.save(school);
        evictCaches();
        return mapToResponse(saved);
    }

    @Override
    public SchoolResponse getSchoolV1(Long id) {
        String cacheKey = cacheKey(id);
        SchoolResponse cached = getCachedValue(SCHOOL_BY_ID_CACHE, cacheKey, SchoolResponse.class);
        if (cached != null) {
            logger.debug("Cache hit: {}", cacheKey);
            return cached;
        }

        logger.debug("Cache miss: {}", cacheKey);
        School school = schoolRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("School not found with ID: " + id));
        SchoolResponse response = mapToResponse(school);
        putCacheValue(SCHOOL_BY_ID_CACHE, cacheKey, response);
        return response;
    }

    @Override
    public List<SchoolResponse> getAllSchoolV1() {
        List<SchoolResponse> cached = getCachedSchoolList();
        if (cached != null) {
            logger.debug("Cache hit: {}", SCHOOL_LIST_CACHE);
            return cached;
        }

        logger.debug("Cache miss: {}", SCHOOL_LIST_CACHE);
        List<SchoolResponse> schools = schoolRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        putCacheValue(SCHOOL_LIST_CACHE, "all", List.copyOf(schools));
        return schools;
    }

    @Override
    public SchoolResponse editSchoolV1(Long id, SchoolRequest request) {
        School school = schoolRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("School not found with ID: " + id));
        school.setName(request.getName());
        SchoolResponse response = mapToResponse(schoolRepository.save(school));
        evictCaches();
        return response;
    }

    @Override
    public void deleteSchoolV1(Long id) {
        schoolRepository.deleteById(id);
        evictCaches();
    }

    private SchoolResponse mapToResponse(School school) {
        List<DepartmentResponse> deptResponses = school.getDepartments() != null
                ? school.getDepartments().stream()
                .map(dept -> new DepartmentResponse(dept.getId(), dept.getName()))
                .collect(Collectors.toList())
                : null;

        return new SchoolResponse(school.getId(), school.getName(), deptResponses);
    }

    private String cacheKey(Long id) {
        return String.valueOf(id);
    }

    private <T> T getCachedValue(String cacheName, String key, Class<T> type) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return null;
        }
        return cache.get(key, type);
    }

    @SuppressWarnings("unchecked")
    private List<SchoolResponse> getCachedSchoolList() {
        Cache cache = cacheManager.getCache(SCHOOL_LIST_CACHE);
        if (cache == null) {
            return null;
        }
        Cache.ValueWrapper value = cache.get("all");
        return value == null ? null : (List<SchoolResponse>) value.get();
    }

    private void putCacheValue(String cacheName, String key, Object value) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.put(key, value);
        }
    }

    private void evictCaches() {
        Optional.ofNullable(cacheManager.getCache(SCHOOL_BY_ID_CACHE)).ifPresent(Cache::clear);
        Optional.ofNullable(cacheManager.getCache(SCHOOL_LIST_CACHE)).ifPresent(Cache::clear);
        logger.debug("Evicted school caches after write operation");
    }
}
