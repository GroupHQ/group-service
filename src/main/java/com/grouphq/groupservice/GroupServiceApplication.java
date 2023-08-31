package com.grouphq.groupservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * The entry point to the application setting up the Spring Context.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GroupServiceApplication {

    public static void main(final String[] args) {
        SpringApplication.run(GroupServiceApplication.class, args);
    }
}
