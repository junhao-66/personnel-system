package com.personnel.service.impl;

import com.personnel.common.constant.AuditOperation;
import com.personnel.common.util.AuditActorContext;
import com.personnel.entity.OperationAuditDO;
import com.personnel.mapper.OperationAuditMapper;
import com.personnel.service.OperationAuditService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationAuditServiceImpl implements OperationAuditService {

    private final OperationAuditMapper mapper;
    private final Clock clock;

    public OperationAuditServiceImpl(OperationAuditMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Shares the business transaction: failed audit insertion must roll the mutation back. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void append(AuditOperation operation, Long targetId, List<String> changedFields) {
        if (
            targetId == null ||
            changedFields == null ||
            changedFields.stream().anyMatch(f -> !operation.allows(f))
        ) {
            throw new IllegalArgumentException("审计对象或字段不合法");
        }
        if (changedFields.isEmpty()) return;
        var entry = com.personnel.convert.OperationAuditConvert.toDO(
            operation,
            targetId,
            changedFields.stream().distinct().toList(),
            AuditActorContext.get(),
            LocalDateTime.now(clock)
        );
        if (mapper.insert(entry) != 1) throw new IllegalStateException("审计记录保存失败");
    }
}
