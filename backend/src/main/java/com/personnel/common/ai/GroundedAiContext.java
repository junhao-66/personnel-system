package com.personnel.common.ai;

import java.util.List;

/** Never includes names, IDs, contact information, raw questions or answer sheets. */
public record GroundedAiContext(String intent, List<Evidence> evidence) {
    public record Evidence(String id, String kind, String text, String basis) {}

    public record Selection(String evidenceId, String frame) {}
}
