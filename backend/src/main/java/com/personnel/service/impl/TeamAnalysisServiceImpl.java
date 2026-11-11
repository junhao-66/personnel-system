package com.personnel.service.impl;

import com.personnel.common.exception.BusinessException;
import com.personnel.dto.req.TalentQueryReqDTO;
import com.personnel.service.*;
import com.personnel.vo.*;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TeamAnalysisServiceImpl implements TeamAnalysisService {

    private final UserService users;
    private final OrganizationService organizations;
    private final TalentDashboardService dashboard;

    public TeamAnalysisServiceImpl(
        UserService users,
        OrganizationService organizations,
        TalentDashboardService dashboard
    ) {
        this.users = users;
        this.organizations = organizations;
        this.dashboard = dashboard;
    }

    @Override
    public TeamAnalysisVO analyzeDepartment(Long actor, Long departmentId) {
        var user = users.getById(actor);
        if (
            !"ADMIN".equals(user.roleCode()) ||
            !"APPROVED".equals(user.auditStatus()) ||
            !"ENABLED".equals(user.userStatus())
        ) throw new BusinessException("仅管理员可查看团队偏好分析");
        if (departmentId == null || departmentId < 0) throw new BusinessException("请选择有效部门");
        String name = departmentId == 0
            ? "未分配部门"
            : organizations
                .listDepartments()
                .stream()
                .filter(d -> d.id().equals(departmentId))
                .map(DepartmentVO::departmentName)
                .findFirst()
                .orElseThrow(() -> new BusinessException("部门不存在"));
        var query = new TalentQueryReqDTO();
        query.setDepartmentId(departmentId);
        var data = dashboard.getDashboard(actor, query);
        var dimensions = new ArrayList<TeamAnalysisVO.DimensionCount>();
        for (int index = 0; index < 4; index++) {
            final int dimension = index;
            String key = List.of("EI", "SN", "TF", "JP").get(index);
            long first = data
                .typeDistribution()
                .stream()
                .filter(t -> t.typeCode().charAt(dimension) == key.charAt(0))
                .mapToLong(TalentDashboardVO.TypeDistribution::employeeCount)
                .sum();
            dimensions.add(
                new TeamAnalysisVO.DimensionCount(key, first, data.completedCount() - first)
            );
        }
        var tips = new ArrayList<String>();
        if (data.completedCount() == 0) tips.add(
            "暂无已完成测评的数据。可先提供多种自愿沟通方式，不对成员作偏好判断。"
        );
        else {
            tips.add(
                "讨论前提供可选的书面准备渠道，讨论中保留自愿交流与会后补充入口，不按类型分派发言任务。"
            );
            tips.add(
                "方案同时呈现具体案例与整体目标，邀请成员用真实任务反馈哪种说明方式更有帮助。"
            );
            tips.add(
                "决策记录事实、判断标准及相关人的体验；分歧应结合具体情况理解，不归因于人格类型。"
            );
            tips.add(
                "明确必要的里程碑并保留调整窗口，协商工作约定后观察实际效果，不据此评价执行能力。"
            );
            if (
                dimensions.stream().anyMatch(d -> d.firstCount() > 0 && d.secondCount() > 0)
            ) tips.add(
                "本部门的类型编码包含同维度的不同端点，可尝试并行提供不同表达渠道；是否有帮助仍以成员反馈为准。"
            );
        }
        return new TeamAnalysisVO(
            departmentId,
            name,
            data,
            dimensions,
            tips,
            "TEAM_COLLABORATION_DEMO_V1",
            "仅管理员可见；统计本部门审核通过且在职人员，每人采用最新有效提交，包含停用账号，草稿不计。四维人数由类型编码拆分，均衡或弱倾向的编码不等于明确偏好，不混算不同量表的平均得分。协作提示为项目通用演示规则，不是个人事实、能力评价或团队优劣结论。"
        );
    }
}
