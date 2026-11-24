package com.popman.arca.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.homepage")
public record HomepageProperties(boolean announcementsEnabled, boolean trendingEnabled) {}
