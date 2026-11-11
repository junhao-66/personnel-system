package com.personnel.controller.assessment;

import com.personnel.service.AssessmentPdfService;
import com.personnel.vo.UserVO;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AssessmentPdfController {

    private final AssessmentPdfService service;

    public AssessmentPdfController(AssessmentPdfService service) {
        this.service = service;
    }

    @GetMapping("/api/assessment/results/{id}/pdf")
    public ResponseEntity<byte[]> exportReport(
        @RequestAttribute("currentUser") UserVO actor,
        @PathVariable Long id
    ) {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .cacheControl(CacheControl.noStore())
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                    .filename("assessment-report-" + id + ".pdf")
                    .build()
                    .toString()
            )
            .header("X-Content-Type-Options", "nosniff")
            .body(service.exportReport(actor.id(), id));
    }
}
