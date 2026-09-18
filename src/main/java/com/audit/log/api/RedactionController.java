package com.audit.log.api;

import com.audit.log.service.RedactionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit/events")
public class RedactionController {
    private final RedactionService service;
    public RedactionController(RedactionService service) { this.service = service; }

    @PostMapping("/{eventId}/redactions")
    public RedactionResult redact(@PathVariable UUID eventId, @Valid @RequestBody RedactionRequest request) {
        return new RedactionResult(service.redact(eventId, request.fields()));
    }

    public record RedactionRequest(@NotEmpty List<String> fields) { }
    public record RedactionResult(int redactedFields) { }
}
