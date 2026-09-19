package com.audit.log;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditLogServiceApplicationTests {

	@Test
	void contextLoads() {
		assertTrue(AuditLogServiceApplication.class.isAnnotationPresent(SpringBootApplication.class));
	}

}
