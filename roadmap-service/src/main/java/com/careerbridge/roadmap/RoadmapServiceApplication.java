package com.careerbridge.roadmap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// No Spring Security on classpath by design -- identity arrives as gateway-injected headers.
// Port 8088 must never be publicly reachable.
@SpringBootApplication
public class RoadmapServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoadmapServiceApplication.class, args);
    }
}
