package com.personnel.service;

public interface AssessmentPdfService {
    byte[] exportReport(Long actorId, Long assessmentId);
}
