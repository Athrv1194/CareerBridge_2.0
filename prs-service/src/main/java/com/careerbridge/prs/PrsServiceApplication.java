package com.careerbridge.prs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// No Spring Security on classpath by design -- identity arrives as gateway-injected headers.
// Port 8089 must never be publicly reachable.
@SpringBootApplication
public class PrsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrsServiceApplication.class, args);
    }
}
