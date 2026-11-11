package com.personnel.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personnel.common.base.PageResult;
import com.personnel.common.constant.AuditOperation;
import com.personnel.common.exception.BusinessException;
import com.personnel.common.util.AttendanceEvaluation;
import com.personnel.convert.AttendanceConvert;
import com.personnel.dto.req.*;
import com.personnel.entity.AttendanceRecordDO;
import com.personnel.entity.AttendanceRuleDO;
import com.personnel.mapper.AttendanceRecordMapper;
import com.personnel.mapper.AttendanceRuleMapper;
import com.personnel.service.*;
import com.personnel.vo.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class  AttendanceServiceImpl implements AttendanceService {

    private final AttendanceRecordMapper records;
    private final AttendanceRuleMapper rules;
    private final UserService users;
    private final OrganizationService organizations;
    private final Clock clock;
    private final LeaveService leaves;
    private final OperationAuditService audit;

    public AttendanceServiceImpl(
        AttendanceRecordMapper records,
        AttendanceRuleMapper rules,
        UserService users,
        OrganizationService organizations,
        Clock clock,
        LeaveService leaves,
        OperationAuditService audit
    ) {
        this.audit = audit;
        this.records = records;
        this.rules = rules;
        this.users = users;
        this.organizations = organizations;
        this.clock = clock;
        this.leaves = leaves;
    }

    private AttendanceRecordVO evaluate(
        AttendanceRecordDO record,
        List<LeavePeriodVO> periods,
        LocalDateTime now
    ) {
        var approved = periods
            .stream()
            .filter(p -> record.getUserId().equals(p.userId()))
            .map(p -> new AttendanceEvaluation.Period(p.startTime(), p.endTime()))
            .toList();
        var result = AttendanceEvaluation.evaluate(
            record.getAttendanceDate(),
            record.getWorkStartTime(),
            record.getWorkEndTime(),
            Integer.valueOf(1).equals(record.getIsRestDay()),
            record.getClockInTime(),
            record.getClockOutTime(),
            now,
            approved
        );
        return AttendanceConvert.toVO(record, result);
    }

    private AttendanceRecordVO evaluate(AttendanceRecordDO record, LocalDateTime now) {
        return evaluate(
            record,
            leaves.listApprovedPeriods(
                List.of(record.getUserId()),
                record.getAttendanceDate().atStartOfDay(),
                record.getAttendanceDate().plusDays(1).atStartOfDay()
            ),
            now
        );
    }

    private AttendanceRuleDO loadRule() {
        AttendanceRuleDO rule = rules.selectById(1L);
        if (rule == null) throw new BusinessException("考勤规则尚未初始化，请联系管理员");
        return rule;
    }

    @Override
    public AttendanceRuleVO getRule() {
        return AttendanceConvert.toRuleVO(loadRule());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRule(AttendanceRuleUpdateReqDTO request) {
        if (!request.workStartTime().isBefore(request.workEndTime())) throw new BusinessException(
            "上班时间必须早于下班时间，首版不支持跨日班次"
        );
        if (
            request.workStartTime().getSecond() != 0 ||
            request.workEndTime().getSecond() != 0 ||
            request.workStartTime().getNano() != 0 ||
            request.workEndTime().getNano() != 0
        ) {
            throw new BusinessException("考勤规则精确到分钟，请勿提交秒数");
        }
        AttendanceRuleDO rule = rules.selectForUpdate();
        if (rule == null) throw new BusinessException("考勤规则尚未初始化，请联系管理员");
        var changed = new java.util.ArrayList<String>();
        if (
            !java.util.Objects.equals(rule.getWorkStartTime(), request.workStartTime())
        ) changed.add("workStartTime");
        if (!java.util.Objects.equals(rule.getWorkEndTime(), request.workEndTime())) changed.add(
            "workEndTime"
        );
        if (changed.isEmpty()) return;
        rule.setWorkStartTime(request.workStartTime());
        rule.setWorkEndTime(request.workEndTime());
        if (rules.updateById(rule) != 1) throw new BusinessException(
            "考勤规则保存失败，请刷新重试"
        );
        audit.append(AuditOperation.ATTENDANCE_RULE_UPDATE, rule.getId(), changed);
    }

    private boolean restDay(LocalDate date) {
        return date.getDayOfWeek().getValue() >= 6;
    }

    @Override
    public TodayAttendanceVO getToday(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        AttendanceRecordDO record = records.selectToday(userId, now.toLocalDate());
        AttendanceRuleDO rule = record == null ? loadRule() : null;
        AttendanceRecordVO evaluated = evaluate(
            record == null ? AttendanceConvert.emptyDay(userId, now.toLocalDate(), rule) : record,
            now
        );
        return new TodayAttendanceVO(
            now.toLocalDate(),
            now,
            record == null ? rule.getWorkStartTime() : record.getWorkStartTime(),
            record == null ? rule.getWorkEndTime() : record.getWorkEndTime(),
            "Asia/Shanghai",
            record == null
                ? restDay(now.toLocalDate())
                : Integer.valueOf(1).equals(record.getIsRestDay()),
            record == null ? null : evaluated,
            evaluated
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceRecordVO clockIn(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        AttendanceRuleDO rule = loadRule();
        AttendanceRecordDO record = new AttendanceRecordDO();
        record.setEnterpriseId(1L);
        record.setUserId(userId);
        record.setAttendanceDate(now.toLocalDate());
        record.setClockInTime(now);
        record.setWorkStartTime(rule.getWorkStartTime());
        record.setWorkEndTime(rule.getWorkEndTime());
        boolean rest = restDay(now.toLocalDate());
        record.setIsRestDay(rest ? 1 : 0);
        record.setIsEarly(0);
        record.setIsLate(!rest && now.toLocalTime().isAfter(rule.getWorkStartTime()) ? 1 : 0);
        try {
            records.insert(record);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("今天已经完成上班打卡，请勿重复提交");
        }
        return evaluate(record, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceRecordVO clockOut(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        AttendanceRecordDO record = records.selectToday(userId, now.toLocalDate());
        if (record == null) throw new BusinessException("请先完成今天的上班打卡");
        if (record.getClockOutTime() != null) throw new BusinessException(
            "今天已经完成下班打卡，请勿重复提交"
        );
        if (now.isBefore(record.getClockInTime())) throw new BusinessException(
            "服务器时间异常，请联系管理员"
        );
        boolean early =
            !Integer.valueOf(1).equals(record.getIsRestDay()) &&
            now.toLocalTime().isBefore(record.getWorkEndTime());
        // 条件更新确保并发请求仅有一个能写入原始下班时间。
        if (records.clockOut(userId, now.toLocalDate(), now, early ? 1 : 0) != 1) {
            throw new BusinessException("打卡记录已变化，请刷新后重试");
        }
        record.setClockOutTime(now);
        record.setIsEarly(early ? 1 : 0);
        return evaluate(record, now);
    }

    @Override
    public PageResult<AttendanceEmployeeVO> listMine(Long userId, AttendanceQueryReqDTO query) {
        return listRecords(userId, query, null, null);
    }

    @Override
    public PageResult<AttendanceEmployeeVO> listAll(AdminAttendanceQueryReqDTO query) {
        String keyword = query.getKeyword() == null ? null : query.getKeyword().trim();
        return listRecords(null, query, keyword, query.getDepartmentId());
    }

    private PageResult<AttendanceEmployeeVO> listRecords(
        Long userId,
        AttendanceQueryReqDTO query,
        String keyword,
        Long departmentId
    ) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = query.getStartDate() == null
            ? today.withDayOfMonth(1)
            : query.getStartDate();
        LocalDate end = query.getEndDate() == null
            ? today.withDayOfMonth(today.lengthOfMonth())
            : query.getEndDate();
        if (
            end.isBefore(start) || ChronoUnit.DAYS.between(start, end) > 366
        ) throw new BusinessException("查询起止日期不合法，单次跨度不能超过366天");
        Page<AttendanceRecordDO> page;
        if (query.isIncludeMissing()) {
            loadRule();
            if (end.isAfter(today)) end = today;
            if (start.isAfter(end)) return new PageResult<>(
                List.of(),
                0,
                query.getPageNum(),
                query.getPageSize()
            );
            var requestPage = new Page<AttendanceRecordDO>(query.getPageNum(), query.getPageSize());
            requestPage.setOptimizeCountSql(false);
            page = records.selectDailyRecords(
                requestPage,
                userId,
                start,
                end,
                keyword,
                departmentId
            );
        } else {
            page = records.selectRecords(
                new Page<>(query.getPageNum(), query.getPageSize()),
                userId,
                start,
                end,
                keyword,
                departmentId
            );
        }
        if (page.getRecords().isEmpty()) return new PageResult<>(
            List.of(),
            page.getTotal(),
            page.getCurrent(),
            page.getSize()
        );
        var periods = leaves.listApprovedPeriods(
            page.getRecords().stream().map(AttendanceRecordDO::getUserId).distinct().toList(),
            start.atStartOfDay(),
            end.plusDays(1).atStartOfDay()
        );
        LocalDateTime now = LocalDateTime.now(clock);
        var userMap = users
            .listByIds(
                page.getRecords().stream().map(AttendanceRecordDO::getUserId).distinct().toList()
            )
            .stream()
            .collect(Collectors.toMap(UserVO::id, Function.identity()));
        var departmentMap = organizations
            .listDepartments()
            .stream()
            .collect(Collectors.toMap(DepartmentVO::id, DepartmentVO::departmentName));
        var result = page
            .getRecords()
            .stream()
            .map(record -> {
                UserVO user = userMap.get(record.getUserId());
                return new AttendanceEmployeeVO(
                    evaluate(record, periods, now),
                    user == null ? null : user.userName(),
                    user == null ? null : user.realName(),
                    user == null ? null : departmentMap.get(user.departmentId())
                );
            })
            .toList();
        return new PageResult<>(result, page.getTotal(), page.getCurrent(), page.getSize());
    }
}
