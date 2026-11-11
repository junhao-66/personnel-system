package com.personnel.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personnel.common.base.PageResult;
import com.personnel.common.constant.AuditOperation;
import com.personnel.common.exception.BusinessException;
import com.personnel.convert.LeaveConvert;
import com.personnel.dto.req.*;
import com.personnel.mapper.LeaveRequestMapper;
import com.personnel.service.*;
import com.personnel.vo.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaveServiceImpl implements LeaveService {

    @Override
    public List<LeavePeriodVO> listApprovedPeriods(
        Collection<Long> userIds,
        LocalDateTime start,
        LocalDateTime end
    ) {
        if (userIds.isEmpty()) return List.of();
        return records
            .selectApprovedPeriods(userIds, start, end)
            .stream()
            .map(row -> new LeavePeriodVO(row.getUserId(), row.getStartTime(), row.getEndTime()))
            .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void configureSelfApprover(Long departmentId, Long managerId) {
        if (managerId != null) requireManager(managerId);
        organizations.updateDepartmentSelfApprover(departmentId, managerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void configureManager(Long departmentId, Long managerId) {
        requireManager(managerId);
        organizations.updateDepartmentManager(departmentId, managerId);
        log.info(
            "Leave department manager configured departmentId:{} managerId:{}",
            departmentId,
            managerId
        );
    }

    private static final Logger log = LoggerFactory.getLogger(LeaveServiceImpl.class);
    private final LeaveRequestMapper records;
    private final UserService users;
    private final OrganizationService organizations;
    private final Clock clock;
    private final List<String> types;
    private final int minimumMinutes;
    private final boolean crossDayAllowed;
    private final OperationAuditService audit;

    public LeaveServiceImpl(
        LeaveRequestMapper records,
        UserService users,
        OrganizationService organizations,
        OperationAuditService audit,
        Clock clock,
        @Value("${personnel.leave.types:事假,病假,年假}") List<String> types,
        @Value("${personnel.leave.minimum-minutes:30}") int minimumMinutes,
        @Value("${personnel.leave.cross-day-allowed:true}") boolean crossDayAllowed
    ) {
        this.audit = audit;
        this.records = records;
        this.users = users;
        this.organizations = organizations;
        this.clock = clock;
        this.types = List.copyOf(types);
        this.minimumMinutes = minimumMinutes;
        this.crossDayAllowed = crossDayAllowed;
        if (minimumMinutes < 1 || types.isEmpty()) throw new IllegalArgumentException(
            "请假规则配置不合法"
        );
    }

    private UserVO approvedUser(Long id) {
        UserVO user = users.getById(id);
        if (
            !"APPROVED".equals(user.auditStatus()) || !"ENABLED".equals(user.userStatus())
        ) throw new BusinessException("账号未审核通过或已停用");
        return user;
    }

    private UserVO getApprover(UserVO user) {
        DepartmentVO department = organizations
            .listDepartments()
            .stream()
            .filter(
                d -> d.id().equals(user.departmentId()) && Integer.valueOf(1).equals(d.isEnabled())
            )
            .findFirst()
            .orElseThrow(() -> new BusinessException("请先联系管理员配置有效的所属部门"));
        if (department.managerUserId() == null) throw new BusinessException(
            "所属部门尚未配置请假审批负责人，请联系管理员"
        );
        Long approverId = department.managerUserId().equals(user.id())
            ? department.selfApproverUserId()
            : department.managerUserId();
        if (approverId == null || approverId.equals(user.id())) throw new BusinessException(
            "负责人本人不能自批请假，请联系管理员配置本人请假审批人"
        );
        UserVO approver = approvedUser(approverId);
        if (!"DEPARTMENT_MANAGER".equals(approver.roleCode())) throw new BusinessException(
            "部门审批负责人不可用，请联系管理员"
        );
        return approver;
    }

    @Override
    public LeaveOptionsVO getOptions(Long userId) {
        UserVO user = approvedUser(userId);
        try {
            return new LeaveOptionsVO(
                types,
                minimumMinutes,
                crossDayAllowed,
                "Asia/Shanghai",
                getApprover(user).realName(),
                null
            );
        } catch (BusinessException exception) {
            return new LeaveOptionsVO(
                types,
                minimumMinutes,
                crossDayAllowed,
                "Asia/Shanghai",
                null,
                exception.getMessage()
            );
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeaveVO addLeave(Long userId, LeaveCreateReqDTO request) {
        // 同一员工串行提交，避免两个并发事务同时通过重叠检查；锁由用户模块提供。
        users.lockUser(userId);
        UserVO user = approvedUser(userId);
        getApprover(user);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        if (!types.contains(request.leaveType())) throw new BusinessException("请假类型不合法");
        if (
            request.startTime() == null ||
            request.endTime() == null ||
            !request.startTime().isBefore(request.endTime())
        ) throw new BusinessException("结束时间必须晚于开始时间");
        if (request.startTime().isBefore(now)) throw new BusinessException(
            "请假开始时间不能早于当前服务器时间"
        );
        if (
            request.startTime().getSecond() != 0 ||
            request.startTime().getNano() != 0 ||
            request.endTime().getSecond() != 0 ||
            request.endTime().getNano() != 0
        ) throw new BusinessException("请假时间须精确到分钟");
        long minutes = ChronoUnit.MINUTES.between(request.startTime(), request.endTime());
        if (minutes < minimumMinutes || minutes % minimumMinutes != 0) throw new BusinessException(
            "请假时长须为" + minimumMinutes + "分钟的整数倍"
        );
        if (
            !crossDayAllowed &&
            !request.startTime().toLocalDate().equals(request.endTime().toLocalDate())
        ) throw new BusinessException("当前规则不允许跨日请假");
        if (
            request.reason() == null ||
            request.reason().isBlank() ||
            request.reason().length() > 500
        ) throw new BusinessException("请填写500字以内的请假原因");
        if (
            records.countOverlapping(userId, request.startTime(), request.endTime()) > 0
        ) throw new BusinessException("与待审批或已批准的请假时间重叠，请勿重复申请");
        var value = LeaveConvert.toDO(userId, user.departmentId(), request, now);
        records.insert(value);
        audit.append(
            AuditOperation.LEAVE_CREATE,
            value.getId(),
            List.of("leaveType", "startTime", "endTime", "reason", "status")
        );
        log.info("Leave created id:{} userId:{}", value.getId(), userId);
        return LeaveConvert.toVO(value, user.realName());
    }

    @Override
    public PageResult<LeaveVO> listMine(Long userId, LeaveQueryReqDTO query) {
        approvedUser(userId);
        return listRecords(userId, null, query);
    }

    private void requireManager(Long userId) {
        if (
            !"DEPARTMENT_MANAGER".equals(approvedUser(userId).roleCode())
        ) throw new BusinessException("仅部门负责人可审批请假");
    }

    @Override
    public PageResult<LeaveVO> listForReview(Long userId, LeaveQueryReqDTO query) {
        requireManager(userId);
        return listRecords(null, userId, query);
    }

    private PageResult<LeaveVO> listRecords(Long userId, Long managerId, LeaveQueryReqDTO query) {
        var page = records.selectLeavePage(
            new Page<>(query.getPageNum(), query.getPageSize()),
            userId,
            managerId,
            query.getStatus()
        );
        var names = users
            .listByIds(
                page
                    .getRecords()
                    .stream()
                    .map(r -> r.getUserId())
                    .distinct()
                    .toList()
            )
            .stream()
            .collect(Collectors.toMap(UserVO::id, Function.identity()));
        var values = page
            .getRecords()
            .stream()
            .map(r ->
                LeaveConvert.toVO(
                    r,
                    names.containsKey(r.getUserId()) ? names.get(r.getUserId()).realName() : null
                )
            )
            .toList();
        return new PageResult<>(values, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewLeave(Long userId, Long id, LeaveReviewReqDTO request) {
        requireManager(userId);
        if (
            !List.of("APPROVED", "REJECTED").contains(request.status())
        ) throw new BusinessException("审批状态不合法");
        String comment = request.reviewComment() == null ? "" : request.reviewComment().trim();
        if (
            comment.length() > 500 || ("REJECTED".equals(request.status()) && comment.isBlank())
        ) throw new BusinessException("拒绝必须填写原因，审批意见不能超过500字");
        // 权限与待审批状态在同一次条件更新中校验，避免重复审批与归属变化越权。
        if (
            records.reviewPending(
                id,
                userId,
                request.status(),
                comment,
                LocalDateTime.now(clock)
            ) !=
            1
        ) throw new BusinessException("申请不存在、已处理或不属于您的审批范围，请刷新列表");
        audit.append(
            AuditOperation.LEAVE_REVIEW,
            id,
            List.of("status", "reviewComment", "reviewerId", "reviewedAt")
        );
        log.info("Leave reviewed id:{} reviewerId:{} status:{}", id, userId, request.status());
    }
}
