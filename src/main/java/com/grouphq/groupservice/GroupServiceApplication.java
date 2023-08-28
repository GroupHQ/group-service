package com.grouphq.groupservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Application Setup.
 *
 * @author makmn
 *
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GroupServiceApplication {

    /**
     * Entry point to application.
     *
     * @param args Command-line arguments
     */
    public static void main(final String[] args) {
        SpringApplication.run(GroupServiceApplication.class, args);
    }
}
