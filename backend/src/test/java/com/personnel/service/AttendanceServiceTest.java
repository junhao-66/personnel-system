package com.personnel.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personnel.common.exception.BusinessException;
import com.personnel.dto.req.*;
import com.personnel.entity.AttendanceRecordDO;
import com.personnel.entity.AttendanceRuleDO;
import com.personnel.mapper.AttendanceRecordMapper;
import com.personnel.mapper.AttendanceRuleMapper;
import com.personnel.service.impl.AttendanceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {
    @Mock AttendanceRecordMapper records;
    @Mock AttendanceRuleMapper rules;
    @Mock UserService users;
    @Mock OrganizationService organizations;
    @Mock LeaveService leaves;
    @Mock OperationAuditService audit;
    AttendanceService service;
    final LocalDate day = LocalDate.of(2026, 10, 28);

    void time(String time) {
        service = new AttendanceServiceImpl(records, rules, users, organizations,
                Clock.fixed(LocalDateTime.parse(time).atZone(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneId.of("Asia/Shanghai")), leaves, audit);
    }
    @BeforeEach void setUp() { time("2026-10-28T09:00:00"); }
    void rule() {
        var rule = new AttendanceRuleDO(); rule.setId(1L); rule.setWorkStartTime(LocalTime.of(9, 0)); rule.setWorkEndTime(LocalTime.of(18, 0));
        when(rules.selectById(1L)).thenReturn(rule);
    }
    AttendanceRecordDO existing() {
        var row = new AttendanceRecordDO(); row.setId(1L); row.setUserId(7L); row.setAttendanceDate(day);
        row.setClockInTime(day.atTime(9, 0)); row.setWorkStartTime(LocalTime.of(9, 0)); row.setWorkEndTime(LocalTime.of(18, 0));
        row.setIsRestDay(0); row.setIsLate(0); row.setIsEarly(0);
        when(records.selectToday(7L, day)).thenReturn(row);
        return row;
    }
    @Test void clockInAtBoundaryIsOnTimeAndServerGenerated() {
        rule(); var result = service.clockIn(7L);
        assertThat(result.clockInTime()).isEqualTo(day.atTime(9, 0)); assertThat(result.late()).isFalse();
        assertThat(result.userId()).isEqualTo(7L); assertThat(result.status()).isEqualTo("IN_PROGRESS");
    }
    @Test void clockInAfterBoundaryIsLate() {
        time("2026-10-28T09:00:01"); rule(); assertThat(service.clockIn(7L).status()).isEqualTo("LATE_IN_PROGRESS");
    }
    @Test void weekendClockInDoesNotCountLate() {
        time("2026-10-31T12:00:00"); rule(); var result = service.clockIn(7L);
        assertThat(result.restDay()).isTrue(); assertThat(result.late()).isFalse(); assertThat(result.status()).isEqualTo("REST_DAY");
    }
    @Test void duplicateUniqueKeyIsBusinessFailure() {
        rule(); when(records.insert(any(AttendanceRecordDO.class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertThatThrownBy(() -> service.clockIn(7L)).isInstanceOf(BusinessException.class).hasMessageContaining("重复");
    }
    @Test void cannotClockOutWithoutClockIn() {
        assertThatThrownBy(() -> service.clockOut(7L)).hasMessageContaining("先完成");
        verify(records, never()).clockOut(any(), any(), any(), anyInt());
    }
    @Test void repeatedClockOutDoesNotOverwriteTime() {
        var row = existing(); row.setClockOutTime(day.atTime(18, 0));
        assertThatThrownBy(() -> service.clockOut(7L)).hasMessageContaining("重复");
        verify(records, never()).clockOut(any(), any(), any(), anyInt());
    }
    @Test void earlyClockOutUsesSnapshottedSchedule() {
        time("2026-10-28T17:59:59"); existing(); when(records.clockOut(eq(7L), eq(day), any(), eq(1))).thenReturn(1);
        assertThat(service.clockOut(7L).status()).isEqualTo("EARLY"); verifyNoInteractions(rules);
    }
    @Test void clockOutAtBoundaryIsNormal() {
        time("2026-10-28T18:00:00"); existing(); when(records.clockOut(eq(7L), eq(day), any(), eq(0))).thenReturn(1);
        assertThat(service.clockOut(7L).status()).isEqualTo("NORMAL");
    }
    @Test void lateAndEarlyCombinationIsPreserved() {
        time("2026-10-28T17:00:00"); var row = existing(); row.setIsLate(1);
        row.setClockInTime(day.atTime(10, 0));
        when(records.clockOut(eq(7L), eq(day), any(), eq(1))).thenReturn(1);
        assertThat(service.clockOut(7L).status()).isEqualTo("LATE_AND_EARLY");
    }
    @Test void weekendClockOutDoesNotCountEarly() {
        time("2026-10-28T10:00:00"); var row = existing(); row.setIsRestDay(1);
        when(records.clockOut(eq(7L), eq(day), any(), eq(0))).thenReturn(1);
        assertThat(service.clockOut(7L).early()).isFalse();
    }
    @Test void clockOutLostRaceFailsWithoutOverwriting() {
        time("2026-10-28T18:00:00"); existing();
        assertThatThrownBy(() -> service.clockOut(7L)).hasMessageContaining("刷新");
    }
    @Test void backwardsServerClockIsRejected() {
        time("2026-10-28T08:00:00"); existing();
        assertThatThrownBy(() -> service.clockOut(7L)).hasMessageContaining("服务器时间异常");
    }
    @Test void todayPreservesScheduleFromExistingRow() {
        existing(); var result = service.getToday(7L);
        assertThat(result.workStartTime()).isEqualTo(LocalTime.of(9, 0)); verifyNoInteractions(rules);
    }
    @Test void todayWithoutRecordUsesCurrentRuleAndShanghaiDate() {
        time("2026-10-29T00:01:00"); rule(); var result = service.getToday(7L);
        assertThat(result.attendanceDate()).isEqualTo(LocalDate.of(2026, 10, 29)); assertThat(result.record()).isNull();
    }
    @Test void crossDayRuleIsRejected() {
        assertThatThrownBy(() -> service.updateRule(new AttendanceRuleUpdateReqDTO(LocalTime.of(18, 0), LocalTime.of(9, 0)))).hasMessageContaining("跨日");
        verifyNoInteractions(rules);
    }
    @Test void secondPrecisionRuleIsRejected() {
        assertThatThrownBy(() -> service.updateRule(new AttendanceRuleUpdateReqDTO(LocalTime.of(9, 0, 1), LocalTime.of(18, 0)))).hasMessageContaining("分钟");
    }
    @Test void validRuleIsSaved() {
        var value = new AttendanceRuleDO(); value.setId(1L); value.setWorkStartTime(LocalTime.of(9,0)); value.setWorkEndTime(LocalTime.of(18,0));
        when(rules.selectForUpdate()).thenReturn(value); when(rules.updateById(any(AttendanceRuleDO.class))).thenReturn(1);
        service.updateRule(new AttendanceRuleUpdateReqDTO(LocalTime.of(8, 30), LocalTime.of(17, 30)));
        verify(rules).updateById(argThat((AttendanceRuleDO row) -> row.getWorkStartTime().equals(LocalTime.of(8, 30))));
    }
    @Test void unchangedRuleDoesNotWriteOrAudit() {
        var value = new AttendanceRuleDO(); value.setId(1L); value.setWorkStartTime(LocalTime.of(9,0)); value.setWorkEndTime(LocalTime.of(18,0));
        when(rules.selectForUpdate()).thenReturn(value);
        service.updateRule(new AttendanceRuleUpdateReqDTO(LocalTime.of(9,0), LocalTime.of(18,0)));
        verify(rules, never()).updateById(any(AttendanceRuleDO.class)); verifyNoInteractions(audit);
    }
    @Test void failedRuleUpdateCannotCreateAudit() {
        var value = new AttendanceRuleDO(); value.setId(1L); value.setWorkStartTime(LocalTime.of(9,0)); value.setWorkEndTime(LocalTime.of(18,0));
        when(rules.selectForUpdate()).thenReturn(value);
        assertThatThrownBy(() -> service.updateRule(new AttendanceRuleUpdateReqDTO(LocalTime.of(8,0), LocalTime.of(18,0)))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(audit);
    }
    @Test void missingRuleIsExplicitFailure() { assertThatThrownBy(service::getRule).hasMessageContaining("尚未初始化"); }
    @Test void personalListAlwaysPassesOwnerAndDefaultsCurrentMonth() {
        when(records.selectRecords(any(), eq(7L), eq(day.withDayOfMonth(1)), eq(day.withDayOfMonth(31)), isNull(), isNull()))
                .thenReturn(new Page<>(1, 10, 0));
        assertThat(service.listMine(7L, new AttendanceQueryReqDTO()).records()).isEmpty();
        verifyNoInteractions(users, organizations);
    }
    @Test void invertedRangeIsRejected() {
        var query = new AttendanceQueryReqDTO(); query.setStartDate(day); query.setEndDate(day.minusDays(1));
        assertThatThrownBy(() -> service.listMine(7L, query)).hasMessageContaining("起止日期"); verifyNoInteractions(records);
    }
    @Test void oversizedRangeIsRejected() {
        var query = new AttendanceQueryReqDTO(); query.setStartDate(day); query.setEndDate(day.plusDays(367));
        assertThatThrownBy(() -> service.listMine(7L, query)).hasMessageContaining("366");
    }
}
