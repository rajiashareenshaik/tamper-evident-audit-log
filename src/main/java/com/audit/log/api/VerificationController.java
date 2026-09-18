package com.audit.log.api;

import com.audit.log.service.ChainVerificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
public class VerificationController {
    private final ChainVerificationService service;
    public VerificationController(ChainVerificationService service) { this.service = service; }

    @GetMapping("/verify")
    public VerificationResponse verify() { return service.verify(); }
}
