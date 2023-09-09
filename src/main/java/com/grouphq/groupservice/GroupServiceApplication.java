package com.grouphq.groupservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;

/**
 * The entry point to the application setting up the Spring Context.
 */
@SpringBootApplication
@EnableScheduling
@EnableReactiveMethodSecurity(useAuthorizationManager = true)
@ConfigurationPropertiesScan
public class GroupServiceApplication {

    public static void main(final String[] args) {
        SpringApplication.run(GroupServiceApplication.class, args);
    }
}
