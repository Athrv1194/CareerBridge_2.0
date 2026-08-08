package com.careerbridge.gateway;

import com.careerbridge.gateway.config.GatewayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * The single entry point to CareerBridge, and the only component that validates a JWT. Every
 * backend service behind it trusts the X-User-Id header this gateway injects, so ports 8081-8085
 * must not be publicly reachable at deploy time.
 */
@SpringBootApplication
@EnableConfigurationProperties(GatewayProperties.class)
public class ApiGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiGatewayApplication.class, args);
	}

}
