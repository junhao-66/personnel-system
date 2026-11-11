package com.personnel.common.util;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Derives attendance from raw punches and the union of approved half-open leave intervals. */
public final class AttendanceEvaluation {

    private AttendanceEvaluation() {}

    public record Period(LocalDateTime start, LocalDateTime end) {}

    public record Result(
        boolean late,
        boolean early,
        boolean missingIn,
        boolean missingOut,
        long leaveMinutes,
        LocalDateTime requiredStart,
        LocalDateTime requiredEnd,
        String status
    ) {}

    public static Result evaluate(
        LocalDate date,
        LocalTime start,
        LocalTime end,
        boolean rest,
        LocalDateTime clockIn,
        LocalDateTime clockOut,
        LocalDateTime now,
        List<Period> approved
    ) {
        if (rest) return new Result(false, false, false, false, 0, null, null, "REST_DAY");
        LocalDateTime windowStart = date.atTime(start),
            windowEnd = date.atTime(end);
        List<Period> clipped = approved
            .stream()
            .filter(p -> p.start().isBefore(windowEnd) && p.end().isAfter(windowStart))
            .map(p ->
                new Period(
                    p.start().isBefore(windowStart) ? windowStart : p.start(),
                    p.end().isAfter(windowEnd) ? windowEnd : p.end()
                )
            )
            .sorted(Comparator.comparing(Period::start))
            .toList();
        List<Period> required = new ArrayList<>();
        LocalDateTime cursor = windowStart;
        long leaveSeconds = 0;
        for (Period p : clipped) {
            if (!p.end().isAfter(cursor)) continue;
            if (p.start().isAfter(cursor)) required.add(new Period(cursor, p.start()));
            LocalDateTime coveredStart = p.start().isAfter(cursor) ? p.start() : cursor;
            leaveSeconds += ChronoUnit.SECONDS.between(coveredStart, p.end());
            cursor = p.end();
        }
        if (cursor.isBefore(windowEnd)) required.add(new Period(cursor, windowEnd));
        if (required.isEmpty()) return new Result(
            false,
            false,
            false,
            false,
            leaveSeconds / 60,
            null,
            null,
            "APPROVED_LEAVE"
        );
        LocalDateTime requiredStart = required.get(0).start(),
            requiredEnd = required.get(required.size() - 1).end();
        boolean late = clockIn != null && clockIn.isAfter(requiredStart);
        boolean early = clockOut != null && clockOut.isBefore(requiredEnd);
        // Never finalize missing punches during an unfinished required work interval (including exact endpoint).
        boolean closed = now.isAfter(requiredEnd);
        boolean missingIn = closed && clockIn == null;
        boolean missingOut = closed && clockOut == null;
        String status;
        if (missingIn && missingOut) status = "MISSING_BOTH";
        else if (missingIn) status = "MISSING_IN";
        else if (missingOut) status = "MISSING_OUT";
        else if (clockIn == null) status = now.isBefore(requiredStart)
            ? "NOT_STARTED"
            : "NOT_CLOCKED";
        else if (clockOut == null) status = late ? "LATE_IN_PROGRESS" : "IN_PROGRESS";
        else if (late && early) status = "LATE_AND_EARLY";
        else if (late) status = "LATE";
        else if (early) status = "EARLY";
        else status = leaveSeconds > 0 ? "PARTIAL_LEAVE" : "NORMAL";
        return new Result(
            late,
            early,
            missingIn,
            missingOut,
            leaveSeconds / 60,
            requiredStart,
            requiredEnd,
            status
        );
    }
}
