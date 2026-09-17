package com.audit.log.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CreateAuditEventRequest validation")
class CreateAuditEventRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("has no violations when all required fields are present")
    void shouldHaveNoViolationsForCompleteRequest() {

        CreateAuditEventRequest request = new CreateAuditEventRequest(
                "USER_LOGIN",
                "user-1",
                "ACCOUNT",
                "A1",
                Map.of("k", "v")
        );

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("rejects a blank eventType")
    void shouldRejectBlankEventType() {

        CreateAuditEventRequest request = new CreateAuditEventRequest(
                " ",
                "user-1",
                "ACCOUNT",
                "A1",
                Map.of("k", "v")
        );

        Set<ConstraintViolation<CreateAuditEventRequest>> violations =
                validator.validate(request);

        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("eventType")));
    }

    @Test
    @DisplayName("rejects a blank actorId")
    void shouldRejectBlankActorId() {

        CreateAuditEventRequest request = new CreateAuditEventRequest(
                "USER_LOGIN",
                "",
                "ACCOUNT",
                "A1",
                Map.of("k", "v")
        );

        Set<ConstraintViolation<CreateAuditEventRequest>> violations =
                validator.validate(request);

        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("actorId")));
    }

    @Test
    @DisplayName("rejects a null payload")
    void shouldRejectNullPayload() {

        CreateAuditEventRequest request = new CreateAuditEventRequest(
                "USER_LOGIN",
                "user-1",
                "ACCOUNT",
                "A1",
                null
        );

        Set<ConstraintViolation<CreateAuditEventRequest>> violations =
                validator.validate(request);

        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("payload")));
    }
}
