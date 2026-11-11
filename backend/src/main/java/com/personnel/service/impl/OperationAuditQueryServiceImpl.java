package com.personnel.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personnel.common.base.PageResult;
import com.personnel.common.exception.BusinessException;
import com.personnel.convert.OperationAuditConvert;
import com.personnel.dto.req.OperationAuditQueryReqDTO;
import com.personnel.entity.OperationAuditDO;
import com.personnel.mapper.OperationAuditMapper;
import com.personnel.service.*;
import org.springframework.stereotype.Service;

@Service
public class OperationAuditQueryServiceImpl implements OperationAuditQueryService {

    private final OperationAuditMapper mapper;
    private final UserService users;

    public OperationAuditQueryServiceImpl(OperationAuditMapper mapper, UserService users) {
        this.mapper = mapper;
        this.users = users;
    }

    @Override
    public PageResult<com.personnel.vo.OperationAuditVO> listAuditLogs(
        Long actorId,
        OperationAuditQueryReqDTO query
    ) {
        var actor = users.getById(actorId);
        if (
            !"ADMIN".equals(actor.roleCode()) ||
            !"APPROVED".equals(actor.auditStatus()) ||
            !"ENABLED".equals(actor.userStatus())
        ) {
            throw new BusinessException("无权查看操作审计");
        }
        if (
            query.getStartDate() != null &&
            query.getEndDate() != null &&
            query.getStartDate().isAfter(query.getEndDate())
        ) {
            throw new BusinessException("开始日期不能晚于结束日期");
        }
        var filter = Wrappers.<OperationAuditDO>lambdaQuery()
            .eq(OperationAuditDO::getEnterpriseId, 1L)
            .eq(
                query.getActorUserId() != null,
                OperationAuditDO::getActorUserId,
                query.getActorUserId()
            )
            .eq(query.getTargetId() != null, OperationAuditDO::getTargetId, query.getTargetId())
            .eq(
                query.getTargetType() != null,
                OperationAuditDO::getTargetType,
                query.getTargetType()
            )
            .eq(query.getOperation() != null, OperationAuditDO::getOperation, query.getOperation());
        if (query.getStartDate() != null) filter.ge(
            OperationAuditDO::getOccurredAt,
            query.getStartDate().atStartOfDay()
        );
        if (query.getEndDate() != null) filter.le(
            OperationAuditDO::getOccurredAt,
            query.getEndDate().atTime(23, 59, 59, 999999000)
        );
        var page = mapper.selectPage(
            new Page<>(query.getPageNum(), query.getPageSize()),
            filter.orderByDesc(OperationAuditDO::getOccurredAt).orderByDesc(OperationAuditDO::getId)
        );
        return new PageResult<>(
            page.getRecords().stream().map(OperationAuditConvert::toVO).toList(),
            page.getTotal(),
            page.getCurrent(),
            page.getSize()
        );
    }
}
