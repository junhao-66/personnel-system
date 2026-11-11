package com.personnel.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personnel.common.base.PageResult;
import com.personnel.common.exception.BusinessException;
import com.personnel.convert.TalentConvert;
import com.personnel.dto.req.TalentQueryReqDTO;
import com.personnel.entity.TalentEmployeeDO;
import com.personnel.entity.TalentGroupDO;
import com.personnel.mapper.TalentDashboardMapper;
import com.personnel.service.TalentDashboardService;
import com.personnel.service.UserService;
import com.personnel.vo.TalentDashboardVO;
import com.personnel.vo.TalentEmployeeVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TalentDashboardServiceImpl implements TalentDashboardService {

    private final TalentDashboardMapper mapper;
    private final UserService users;
    private final Clock clock;
    private static final List<String> TYPES = List.of(
        "INTJ",
        "INTP",
        "ENTJ",
        "ENTP",
        "INFJ",
        "INFP",
        "ENFJ",
        "ENFP",
        "ISTJ",
        "ISFJ",
        "ESTJ",
        "ESFJ",
        "ISTP",
        "ISFP",
        "ESTP",
        "ESFP"
    );

    public TalentDashboardServiceImpl(
        TalentDashboardMapper mapper,
        UserService users,
        Clock clock
    ) {
        this.mapper = mapper;
        this.users = users;
        this.clock = clock;
    }

    private void requireAdmin(Long actorId) {
        var user = users.getById(actorId);
        if (
            !"ADMIN".equals(user.roleCode()) ||
            !"APPROVED".equals(user.auditStatus()) ||
            !"ENABLED".equals(user.userStatus())
        ) {
            throw new BusinessException("仅有效管理员可查看人才看板");
        }
    }

    private BigDecimal percent(long part, long total) {
        return total == 0
            ? null
            : BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    @Override
    public TalentDashboardVO getDashboard(Long actorId, TalentQueryReqDTO query) {
        requireAdmin(actorId);
        // One aggregate query supplies all totals and distributions from the same database statement snapshot.
        var groups = mapper.selectGroups(query);
        long employeeCount = groups.stream().mapToLong(TalentGroupDO::getEmployeeCount).sum();
        long completedCount = groups
            .stream()
            .filter(g -> g.getTypeCode() != null)
            .mapToLong(TalentGroupDO::getEmployeeCount)
            .sum();
        long assessmentCount = groups.stream().mapToLong(TalentGroupDO::getAssessmentCount).sum();
        var typeCounts = new LinkedHashMap<String, Long>();
        TYPES.forEach(type -> typeCounts.put(type, 0L));
        Map<Long, List<TalentGroupDO>> departments = new LinkedHashMap<>();
        for (var group : groups) {
            if (group.getTypeCode() != null) typeCounts.merge(
                group.getTypeCode(),
                group.getEmployeeCount(),
                Long::sum
            );
            departments
                .computeIfAbsent(group.getDepartmentId(), ignored -> new ArrayList<>())
                .add(group);
        }
        var types = typeCounts
            .entrySet()
            .stream()
            .map(e ->
                new TalentDashboardVO.TypeDistribution(
                    e.getKey(),
                    e.getValue(),
                    percent(e.getValue(), completedCount)
                )
            )
            .toList();
        var departmentRows = departments
            .entrySet()
            .stream()
            .map(e -> departmentDistribution(e.getKey(), e.getValue()))
            .toList();
        return new TalentDashboardVO(
            employeeCount,
            completedCount,
            employeeCount - completedCount,
            assessmentCount,
            percent(completedCount, employeeCount),
            types,
            departmentRows,
            LocalDateTime.now(clock),
            "仅统计审核通过的人员；默认在职，包含管理员、负责人及停用账号。当前全部筛选同时作用于分母和人数。" +
            "人数按员工去重，类型使用最新有效提交；草稿不计入，量表停用不删除历史。类型仅表示偏好，不代表能力或岗位适配。"
        );
    }

    private TalentDashboardVO.DepartmentDistribution departmentDistribution(
        Long id,
        List<TalentGroupDO> groups
    ) {
        long total = groups.stream().mapToLong(TalentGroupDO::getEmployeeCount).sum();
        long completed = groups
            .stream()
            .filter(g -> g.getTypeCode() != null)
            .mapToLong(TalentGroupDO::getEmployeeCount)
            .sum();
        long count = groups.stream().mapToLong(TalentGroupDO::getAssessmentCount).sum();
        return new TalentDashboardVO.DepartmentDistribution(
            id,
            groups.get(0).getDepartmentName(),
            total,
            completed,
            total - completed,
            count,
            percent(completed, total)
        );
    }

    @Override
    public PageResult<TalentEmployeeVO> listEmployees(Long actorId, TalentQueryReqDTO query) {
        requireAdmin(actorId);
        // Explicit count wrapper avoids optimizer join-removal changing optional-completion filters.
        var requestPage = new Page<TalentEmployeeDO>(query.getPageNum(), query.getPageSize());
        requestPage.setOptimizeCountSql(false);
        var page = mapper.selectEmployeePage(requestPage, query);
        return new PageResult<>(
            page.getRecords().stream().map(TalentConvert::toVO).toList(),
            page.getTotal(),
            page.getCurrent(),
            page.getSize()
        );
    }

    @Override
    public TalentEmployeeVO getEmployee(Long actorId, Long userId) {
        requireAdmin(actorId);
        var value = mapper.selectEmployee(userId);
        if (value == null) throw new BusinessException("员工不存在或尚未通过审核");
        return TalentConvert.toVO(value);
    }
}
