package com.popman.arca.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(HomepageProperties.class)
public class HomepageConfiguration {}
