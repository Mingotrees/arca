package com.popman.arca;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class ArcaApplication {
    public static void main(String[] args) {
        SpringApplication.run(ArcaApplication.class, args);
    }
}
