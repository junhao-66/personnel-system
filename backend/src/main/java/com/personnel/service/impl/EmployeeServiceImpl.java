package com.personnel.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personnel.common.base.PageResult;
import com.personnel.common.constant.AuditOperation;
import com.personnel.common.exception.BusinessException;
import com.personnel.convert.EmployeeConvert;
import com.personnel.dto.req.EmployeeQueryReqDTO;
import com.personnel.dto.req.EmployeeUpdateReqDTO;
import com.personnel.entity.EmployeeProfileDO;
import com.personnel.entity.UserDO;
import com.personnel.mapper.EmployeeProfileMapper;
import com.personnel.mapper.UserMapper;
import com.personnel.service.EmployeeService;
import com.personnel.service.OperationAuditService;
import com.personnel.service.OrganizationService;
import com.personnel.vo.EmployeeVO;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    private final UserMapper userMapper;
    private final EmployeeProfileMapper profileMapper;
    private final OrganizationService organizationService;
    private final OperationAuditService audit;

    public EmployeeServiceImpl(
        UserMapper userMapper,
        EmployeeProfileMapper profileMapper,
        OrganizationService organizationService,
        OperationAuditService audit
    ) {
        this.userMapper = userMapper;
        this.profileMapper = profileMapper;
        this.organizationService = organizationService;
        this.audit = audit;
    }

    @Override
    public PageResult<EmployeeVO> listEmployees(EmployeeQueryReqDTO query) {
        String keyword = query.getKeyword() == null ? null : query.getKeyword().trim();
        Page<UserDO> page = userMapper.selectEmployeePage(
            new Page<>(query.getPageNum(), query.getPageSize()),
            keyword,
            query.getDepartmentId(),
            query.getEmploymentStatus()
        );
        if (page.getRecords().isEmpty()) {
            return new PageResult<>(List.of(), page.getTotal(), page.getCurrent(), page.getSize());
        }
        // 每页批量读取档案与组织字典，避免逐员工查询。
        var profiles = profileMapper
            .selectBatchIds(page.getRecords().stream().map(UserDO::getId).toList())
            .stream()
            .collect(Collectors.toMap(EmployeeProfileDO::getUserId, Function.identity()));
        var departments = organizationService
            .listDepartments()
            .stream()
            .collect(
                Collectors.toMap(
                    com.personnel.vo.DepartmentVO::id,
                    com.personnel.vo.DepartmentVO::departmentName
                )
            );
        var positions = organizationService
            .listPositions(null)
            .stream()
            .collect(
                Collectors.toMap(
                    com.personnel.vo.PositionVO::id,
                    com.personnel.vo.PositionVO::positionName
                )
            );
        var records = page
            .getRecords()
            .stream()
            .map(user ->
                EmployeeConvert.toVO(
                    user,
                    profiles.get(user.getId()),
                    departments.get(user.getDepartmentId()),
                    positions.get(user.getPositionId())
                )
            )
            .toList();
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEmployee(Long id, EmployeeUpdateReqDTO request) {
        UserDO user = userMapper.selectById(id);
        if (user == null || !"APPROVED".equals(user.getAuditStatus())) {
            throw new BusinessException("员工不存在或尚未通过审核");
        }
        boolean assignmentChanged =
            !Objects.equals(user.getDepartmentId(), request.departmentId()) ||
            !Objects.equals(user.getPositionId(), request.positionId());
        // 历史停用关联允许原样保留；新增或调整关联时必须有效。
        if (assignmentChanged) organizationService.validateAssignment(
            request.departmentId(),
            request.positionId()
        );
        var profile = profileMapper.selectById(id);
        var changed = new ArrayList<String>();
        if (!Objects.equals(user.getRealName(), request.realName().trim())) changed.add("realName");
        if (
            !Objects.equals(
                user.getPhone(),
                request.phone() == null ? null : request.phone().trim()
            )
        ) changed.add("phone");
        if (!Objects.equals(user.getDepartmentId(), request.departmentId())) changed.add(
            "departmentId"
        );
        if (!Objects.equals(user.getPositionId(), request.positionId())) changed.add("positionId");
        if (
            profile == null || !Objects.equals(profile.getHireDate(), request.hireDate())
        ) changed.add("hireDate");
        if (
            profile == null ||
            !Objects.equals(profile.getEmploymentStatus(), request.employmentStatus())
        ) changed.add("employmentStatus");
        userMapper.update(
            null,
            Wrappers.<UserDO>lambdaUpdate()
                .eq(UserDO::getId, id)
                .set(UserDO::getRealName, request.realName().trim())
                .set(UserDO::getPhone, request.phone() == null ? null : request.phone().trim())
                .set(UserDO::getDepartmentId, request.departmentId())
                .set(UserDO::getPositionId, request.positionId())
        );
        if (profile == null) {
            profileMapper.insert(EmployeeConvert.toProfile(id, request));
        } else {
            // 显式更新支持清空日期，不能使用忽略 null 的 updateById。
            profileMapper.update(
                null,
                Wrappers.<EmployeeProfileDO>lambdaUpdate()
                    .eq(EmployeeProfileDO::getUserId, id)
                    .set(EmployeeProfileDO::getHireDate, request.hireDate())
                    .set(EmployeeProfileDO::getEmploymentStatus, request.employmentStatus())
            );
        }
        audit.append(AuditOperation.EMPLOYEE_UPDATE, id, changed);
    }
}
