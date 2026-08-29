package com.careerbridge.organization;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// No Spring Security on classpath by design -- identity arrives as gateway-injected headers.
// Port 8087 must never be publicly reachable.
@SpringBootApplication
public class OrganizationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrganizationServiceApplication.class, args);
    }
}
