package com.audit.log.api;

import com.audit.log.service.ClientAccountAccessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** Scenario C API for recording and reviewing client account read attempts. */
@RestController
@RequestMapping("/api/v1/audit/client-account-access")
public class ClientAccountAccessController {
    private final ClientAccountAccessService service;

    public ClientAccountAccessController(ClientAccountAccessService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AuditEventResponse> record(
            @Valid @RequestBody ClientAccountAccessRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AuditEventResponse.from(service.record(request)));
    }

    @GetMapping
    public List<AuditEventResponse> find(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Long afterSequenceId,
            @RequestParam(required = false) Integer limit) {
        return service.find(actorId, accountId, from, to, afterSequenceId, limit).stream()
                .map(AuditEventResponse::from)
                .toList();
    }
}
