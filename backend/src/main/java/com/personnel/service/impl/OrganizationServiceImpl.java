package com.personnel.service.impl;

import static com.personnel.convert.OrganizationConvert.*;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personnel.common.constant.AuditOperation;
import com.personnel.common.exception.BusinessException;
import com.personnel.dto.req.DepartmentSaveReqDTO;
import com.personnel.dto.req.PositionSaveReqDTO;
import com.personnel.entity.DepartmentDO;
import com.personnel.entity.PositionDO;
import com.personnel.mapper.DepartmentMapper;
import com.personnel.mapper.PositionMapper;
import com.personnel.service.OperationAuditService;
import com.personnel.service.OrganizationService;
import com.personnel.vo.DepartmentVO;
import com.personnel.vo.PositionVO;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationServiceImpl implements OrganizationService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDepartmentSelfApprover(Long departmentId, Long managerId) {
        DepartmentDO department = departmentMapper.selectById(departmentId);
        if (department == null) throw new BusinessException("部门不存在");
        if (
            managerId != null && managerId.equals(department.getManagerUserId())
        ) throw new BusinessException("本人请假审批人不能与部门负责人相同");
        if (Objects.equals(department.getSelfApproverUserId(), managerId)) return;
        departmentMapper.update(
            null,
            Wrappers.<DepartmentDO>lambdaUpdate()
                .eq(DepartmentDO::getId, departmentId)
                .set(DepartmentDO::getSelfApproverUserId, managerId)
        );
        audit.append(
            AuditOperation.DEPARTMENT_SELF_APPROVER,
            departmentId,
            List.of("selfApproverUserId")
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DepartmentVO updateDepartment(Long id, DepartmentSaveReqDTO request) {
        DepartmentDO value = departmentMapper.selectById(id);
        if (value == null) throw new BusinessException("部门不存在");
        String name = request.departmentName().trim();
        String code = request.departmentCode().trim();
        int sort = request.sortOrder() == null ? 0 : request.sortOrder();
        if (sort < 0) throw new BusinessException("排序不能小于0");
        if (
            departmentMapper.selectCount(
                Wrappers.<DepartmentDO>lambdaQuery()
                    .eq(DepartmentDO::getDepartmentCode, code)
                    .ne(DepartmentDO::getId, id)
            ) >
            0
        ) {
            throw new BusinessException("部门编码已存在");
        }
        var fields = new java.util.ArrayList<String>();
        if (!Objects.equals(value.getDepartmentName(), name)) fields.add("departmentName");
        if (!Objects.equals(value.getDepartmentCode(), code)) fields.add("departmentCode");
        if (!Objects.equals(value.getSortOrder(), sort)) fields.add("sortOrder");
        if (!fields.isEmpty()) {
            departmentMapper.update(
                null,
                Wrappers.<DepartmentDO>lambdaUpdate()
                    .eq(DepartmentDO::getId, id)
                    .set(DepartmentDO::getDepartmentName, name)
                    .set(DepartmentDO::getDepartmentCode, code)
                    .set(DepartmentDO::getSortOrder, sort)
            );
            audit.append(AuditOperation.DEPARTMENT_UPDATE, id, fields);
        }
        value.setDepartmentName(name);
        value.setDepartmentCode(code);
        value.setSortOrder(sort);
        return toDepartmentVO(value);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PositionVO updatePosition(Long id, PositionSaveReqDTO request) {
        PositionDO value = positionMapper.selectById(id);
        if (value == null) throw new BusinessException("岗位不存在");
        // 岗位归属变更涉及员工调岗，应通过人事流程处理，避免破坏既有关联。
        if (!Objects.equals(value.getDepartmentId(), request.departmentId())) {
            throw new BusinessException(
                "编辑资料不能变更岗位所属部门，请新增岗位并通过员工档案调整关联"
            );
        }
        String name = request.positionName().trim();
        String code = request.positionCode().trim();
        String description = request.description() == null ? "" : request.description().trim();
        if (
            positionMapper.selectCount(
                Wrappers.<PositionDO>lambdaQuery()
                    .eq(PositionDO::getPositionCode, code)
                    .ne(PositionDO::getId, id)
            ) >
            0
        ) {
            throw new BusinessException("岗位编码已存在");
        }
        var fields = new java.util.ArrayList<String>();
        if (!Objects.equals(value.getPositionName(), name)) fields.add("positionName");
        if (!Objects.equals(value.getPositionCode(), code)) fields.add("positionCode");
        if (!Objects.equals(value.getDescription(), description)) fields.add("description");
        if (!fields.isEmpty()) {
            positionMapper.update(
                null,
                Wrappers.<PositionDO>lambdaUpdate()
                    .eq(PositionDO::getId, id)
                    .set(PositionDO::getPositionName, name)
                    .set(PositionDO::getPositionCode, code)
                    .set(PositionDO::getDescription, description)
            );
            audit.append(AuditOperation.POSITION_UPDATE, id, fields);
        }
        value.setPositionName(name);
        value.setPositionCode(code);
        value.setDescription(description);
        return toPositionVO(value);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDepartmentManager(Long departmentId, Long managerId) {
        DepartmentDO department = departmentMapper.selectById(departmentId);
        if (department == null) throw new BusinessException("部门不存在");
        boolean changed = !Objects.equals(department.getManagerUserId(), managerId);
        if (
            managerId != null && managerId.equals(department.getSelfApproverUserId())
        ) throw new BusinessException("部门负责人与本人请假审批人不能相同，请先调整审批配置");
        departmentMapper.update(
            null,
            Wrappers.<DepartmentDO>lambdaUpdate()
                .eq(DepartmentDO::getId, departmentId)
                .set(DepartmentDO::getManagerUserId, managerId)
        );
        if (changed) audit.append(
            AuditOperation.DEPARTMENT_MANAGER,
            departmentId,
            List.of("managerUserId")
        );
    }

    private final DepartmentMapper departmentMapper;
    private final PositionMapper positionMapper;
    private final OperationAuditService audit;

    public OrganizationServiceImpl(
        DepartmentMapper departmentMapper,
        PositionMapper positionMapper,
        OperationAuditService audit
    ) {
        this.departmentMapper = departmentMapper;
        this.positionMapper = positionMapper;
        this.audit = audit;
    }

    @Override
    public void validateAssignment(Long departmentId, Long positionId) {
        if (departmentId != null) {
            DepartmentDO department = departmentMapper.selectById(departmentId);
            if (department == null || !Integer.valueOf(1).equals(department.getIsEnabled())) {
                throw new BusinessException("所属部门不存在或已停用");
            }
        }
        if (positionId == null) return;
        PositionDO position = positionMapper.selectById(positionId);
        if (position == null || !Integer.valueOf(1).equals(position.getIsEnabled())) {
            throw new BusinessException("岗位不存在或已停用");
        }
        if (
            position.getDepartmentId() != null && !position.getDepartmentId().equals(departmentId)
        ) {
            throw new BusinessException("岗位不属于所选部门");
        }
    }

    @Override
    public List<DepartmentVO> listDepartments() {
        return departmentMapper
            .selectList(
                Wrappers.<DepartmentDO>lambdaQuery()
                    .orderByAsc(DepartmentDO::getSortOrder)
                    .orderByAsc(DepartmentDO::getId)
            )
            .stream()
            .map(com.personnel.convert.OrganizationConvert::toDepartmentVO)
            .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DepartmentVO addDepartment(DepartmentSaveReqDTO request) {
        if (
            departmentMapper.selectCount(
                Wrappers.<DepartmentDO>lambdaQuery().eq(
                    DepartmentDO::getDepartmentCode,
                    request.departmentCode()
                )
            ) >
            0
        ) {
            throw new BusinessException("部门编码已存在");
        }
        DepartmentDO department = new DepartmentDO();
        department.setDepartmentName(request.departmentName());
        department.setDepartmentCode(request.departmentCode());
        department.setManagerUserId(request.managerUserId());
        department.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        department.setIsEnabled(1);
        departmentMapper.insert(department);
        audit.append(
            AuditOperation.DEPARTMENT_CREATE,
            department.getId(),
            List.of("departmentName", "departmentCode", "managerUserId", "sortOrder", "isEnabled")
        );
        return toDepartmentVO(department);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDepartmentEnabled(Long id, boolean enabled) {
        DepartmentDO department = departmentMapper.selectById(id);
        if (department == null) throw new BusinessException("部门不存在");
        boolean changed = !Integer.valueOf(enabled ? 1 : 0).equals(department.getIsEnabled());
        department.setIsEnabled(enabled ? 1 : 0);
        departmentMapper.updateById(department);
        if (changed) audit.append(AuditOperation.DEPARTMENT_STATUS, id, List.of("isEnabled"));
    }

    @Override
    public List<PositionVO> listPositions(Long departmentId) {
        return positionMapper
            .selectList(
                Wrappers.<PositionDO>lambdaQuery()
                    .eq(departmentId != null, PositionDO::getDepartmentId, departmentId)
                    .orderByAsc(PositionDO::getId)
            )
            .stream()
            .map(com.personnel.convert.OrganizationConvert::toPositionVO)
            .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PositionVO addPosition(PositionSaveReqDTO request) {
        if (
            positionMapper.selectCount(
                Wrappers.<PositionDO>lambdaQuery().eq(
                    PositionDO::getPositionCode,
                    request.positionCode()
                )
            ) >
            0
        ) {
            throw new BusinessException("岗位编码已存在");
        }
        if (
            request.departmentId() != null &&
            departmentMapper.selectById(request.departmentId()) == null
        ) {
            throw new BusinessException("所属部门不存在");
        }
        PositionDO position = new PositionDO();
        position.setPositionName(request.positionName());
        position.setPositionCode(request.positionCode());
        position.setDepartmentId(request.departmentId());
        position.setDescription(request.description());
        position.setIsEnabled(1);
        positionMapper.insert(position);
        audit.append(
            AuditOperation.POSITION_CREATE,
            position.getId(),
            List.of("positionName", "positionCode", "departmentId", "description", "isEnabled")
        );
        return toPositionVO(position);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setPositionEnabled(Long id, boolean enabled) {
        PositionDO position = positionMapper.selectById(id);
        if (position == null) throw new BusinessException("岗位不存在");
        boolean changed = !Integer.valueOf(enabled ? 1 : 0).equals(position.getIsEnabled());
        position.setIsEnabled(enabled ? 1 : 0);
        positionMapper.updateById(position);
        if (changed) audit.append(AuditOperation.POSITION_STATUS, id, List.of("isEnabled"));
    }
}
