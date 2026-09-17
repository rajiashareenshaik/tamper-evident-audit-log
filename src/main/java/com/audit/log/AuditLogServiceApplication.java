package com.audit.log;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the tamper-evident audit log service.
 */
@SpringBootApplication
public class AuditLogServiceApplication {

	/**
	 * Boots the Spring application context.
	 *
	 * @param args standard JVM command-line arguments, passed through to Spring Boot
	 */
	public static void main(String[] args) {
		SpringApplication.run(AuditLogServiceApplication.class, args);
	}

}
