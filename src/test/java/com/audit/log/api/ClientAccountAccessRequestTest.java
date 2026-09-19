package com.audit.log.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Client account access request validation")
class ClientAccountAccessRequestTest {
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
    @DisplayName("accepts a complete access event")
    void acceptsCompleteEvent() {
        assertTrue(validator.validate(request()).isEmpty());
    }

    @Test
    @DisplayName("requires identity, account, decision, source, and request fields")
    void rejectsMissingRequiredFields() {
        ClientAccountAccessRequest request = new ClientAccountAccessRequest(
                " ", "", null, null, " ", "", List.of());

        Set<ConstraintViolation<ClientAccountAccessRequest>> violations = validator.validate(request);

        assertEquals(6, violations.size());
    }

    @Test
    @DisplayName("rejects blank data category names")
    void rejectsBlankDataCategory() {
        ClientAccountAccessRequest request = new ClientAccountAccessRequest(
                "employee-123", "account-456", ClientAccountAccessRequest.Action.READ,
                ClientAccountAccessRequest.Outcome.ALLOWED, "CSR_PORTAL", "request-789",
                List.of("CONTACT_DETAILS", " "));

        assertTrue(validator.validate(request).stream()
                .anyMatch(v -> v.getPropertyPath().toString().contains("dataCategories")));
    }

    @Test
    @DisplayName("treats omitted data categories as an empty list")
    void defaultsDataCategories() {
        ClientAccountAccessRequest request = new ClientAccountAccessRequest(
                "employee-123", "account-456", ClientAccountAccessRequest.Action.READ,
                ClientAccountAccessRequest.Outcome.DENIED, "CSR_PORTAL", "request-789", null);

        assertEquals(List.of(), request.dataCategories());
        assertTrue(validator.validate(request).isEmpty());
    }

    private ClientAccountAccessRequest request() {
        return new ClientAccountAccessRequest(
                "employee-123", "account-456", ClientAccountAccessRequest.Action.READ,
                ClientAccountAccessRequest.Outcome.ALLOWED, "CSR_PORTAL", "request-789",
                List.of("CONTACT_DETAILS"));
    }
}
