package com.audit.log.api;

import com.audit.log.service.ExportService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit")
public class ExportController {
    private final ExportService service;
    public ExportController(ExportService service) { this.service = service; }

    @GetMapping("/export")
    public ExportBundle export(@RequestParam(required = false) String actorId,
                               @RequestParam(required = false) String resourceId) {
        return service.export(actorId, resourceId);
    }
}
