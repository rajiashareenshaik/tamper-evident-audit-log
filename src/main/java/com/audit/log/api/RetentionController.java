package com.audit.log.api;

import com.audit.log.service.RetentionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit/retention")
public class RetentionController {
    private final RetentionService service;
    public RetentionController(RetentionService service) { this.service = service; }

    @PostMapping("/archive-expired")
    public RetentionService.ArchiveResult archiveExpired() { return service.archiveExpired(); }
}
