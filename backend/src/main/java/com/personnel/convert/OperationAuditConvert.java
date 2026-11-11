package com.personnel.convert;

import com.personnel.common.constant.AuditOperation;
import com.personnel.common.util.AuditActorContext;
import com.personnel.entity.OperationAuditDO;
import com.personnel.vo.OperationAuditVO;
import java.time.LocalDateTime;
import java.util.List;

public final class OperationAuditConvert {

    private OperationAuditConvert() {}

    public static OperationAuditDO toDO(
        AuditOperation operation,
        Long targetId,
        List<String> fields,
        AuditActorContext.Actor actor,
        LocalDateTime occurredAt
    ) {
        var entry = new OperationAuditDO();
        entry.setActorUserId(actor == null ? null : actor.userId());
        entry.setActorRole(actor == null ? "SYSTEM" : actor.role());
        entry.setEnterpriseId(1L);
        entry.setOperation(operation.name());
        entry.setTargetType(operation.targetType());
        entry.setTargetId(targetId);
        entry.setChangedFields(String.join(",", fields));
        entry.setOccurredAt(occurredAt);
        return entry;
    }

    public static OperationAuditVO toVO(OperationAuditDO value) {
        return new OperationAuditVO(
            value.getId(),
            value.getActorUserId(),
            value.getActorRole(),
            value.getOperation(),
            value.getTargetType(),
            value.getTargetId(),
            List.of(value.getChangedFields().split(",")),
            value.getOccurredAt()
        );
    }
}
