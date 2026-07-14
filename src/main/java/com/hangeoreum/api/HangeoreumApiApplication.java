package com.hangeoreum.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HangeoreumApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(HangeoreumApiApplication.class, args);
    }
}
