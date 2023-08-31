package com.grouphq.groupservice.cucumber;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Configuration for linking Cucumber with the Spring context.
 */
@CucumberContextConfiguration
@SpringBootTest
@AutoConfigureWebTestClient
public class CucumberSpringConfiguration {
}
