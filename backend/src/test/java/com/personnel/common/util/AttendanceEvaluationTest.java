package com.personnel.common.util;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class AttendanceEvaluationTest {
    final LocalDate day = LocalDate.of(2026, 10, 28);
    LocalDateTime at(int hour, int minute) { return day.atTime(hour, minute); }
    AttendanceEvaluation.Period leave(int start, int end) { return new AttendanceEvaluation.Period(at(start, 0), at(end, 0)); }
    AttendanceEvaluation.Result evaluate(LocalDateTime in, LocalDateTime out, LocalDateTime now, AttendanceEvaluation.Period... leave) {
        return AttendanceEvaluation.evaluate(day, LocalTime.of(9,0), LocalTime.of(18,0), false, in, out, now, List.of(leave));
    }
    @Test void morningLeaveMovesRequiredArrival() {
        var result = evaluate(at(13,0), at(18,0), at(19,0), leave(9,13));
        assertThat(result.late()).isFalse(); assertThat(result.requiredStart()).isEqualTo(at(13,0)); assertThat(result.leaveMinutes()).isEqualTo(240);
    }
    @Test void exactArrivalAfterLeaveIsOnTimeButOneSecondLaterIsLate() {
        assertThat(evaluate(at(13,0), null, at(13,0), leave(9,13)).late()).isFalse();
        assertThat(evaluate(at(13,0).plusSeconds(1), null, at(13,1), leave(9,13)).late()).isTrue();
    }
    @Test void afternoonLeaveMovesRequiredDeparture() {
        var result = evaluate(at(9,0), at(15,0), at(19,0), leave(15,18));
        assertThat(result.early()).isFalse(); assertThat(result.requiredEnd()).isEqualTo(at(15,0)); assertThat(result.status()).isEqualTo("PARTIAL_LEAVE");
    }
    @Test void fullDayLeaveNeedsNoPunches() {
        var result = evaluate(null, null, at(19,0), leave(8,20));
        assertThat(result.status()).isEqualTo("APPROVED_LEAVE"); assertThat(result.leaveMinutes()).isEqualTo(540);
        assertThat(result.missingIn()).isFalse(); assertThat(result.missingOut()).isFalse();
    }
    @Test void overlappingAndAdjacentIntervalsCountUnionOnly() {
        var result = evaluate(null, null, at(19,0), leave(8,12), leave(10,14), leave(14,18));
        assertThat(result.leaveMinutes()).isEqualTo(540); assertThat(result.status()).isEqualTo("APPROVED_LEAVE");
    }
    @Test void interiorLeaveDoesNotExcuseLateArrivalOrEarlyDeparture() {
        var result = evaluate(at(10,0), at(17,0), at(19,0), leave(12,14));
        assertThat(result.late()).isTrue(); assertThat(result.early()).isTrue(); assertThat(result.status()).isEqualTo("LATE_AND_EARLY");
    }
    @Test void noMissingPunchesBeforeOrExactlyAtRequiredEnd() {
        assertThat(evaluate(null,null,at(17,59)).missingIn()).isFalse();
        assertThat(evaluate(null,null,at(18,0)).missingOut()).isFalse();
        assertThat(evaluate(null,null,at(18,0).plusSeconds(1)).status()).isEqualTo("MISSING_BOTH");
    }
    @Test void singleMissingOutDetectedAfterEnd() {
        assertThat(evaluate(at(9,0),null,at(19,0)).status()).isEqualTo("MISSING_OUT");
    }
    @Test void partialLeaveUsesRemainingWorkEndForMissingBoundary() {
        assertThat(evaluate(at(9,0),null,at(15,0),leave(15,18)).missingOut()).isFalse();
        assertThat(evaluate(at(9,0),null,at(15,0).plusSeconds(1),leave(15,18)).missingOut()).isTrue();
    }
    @Test void crossDayLeaveClipsToWorkWindow() {
        var period = new AttendanceEvaluation.Period(day.minusDays(1).atTime(16,0), at(13,0));
        assertThat(evaluate(at(13,0),at(18,0),at(19,0),period).leaveMinutes()).isEqualTo(240);
    }
    @Test void leaveOutsideWorkWindowHasNoEffect() {
        assertThat(evaluate(at(9,0),at(18,0),at(19,0),leave(0,9),leave(18,23)).leaveMinutes()).isZero();
    }
    @Test void restDayNeverRequiresPunches() {
        var result = AttendanceEvaluation.evaluate(day,LocalTime.of(9,0),LocalTime.of(18,0),true,null,null,at(19,0),List.of());
        assertThat(result.status()).isEqualTo("REST_DAY"); assertThat(result.missingIn()).isFalse();
    }
}
