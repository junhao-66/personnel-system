package com.personnel.service;

import com.personnel.common.constant.AuditOperation;
import java.util.List;

public interface OperationAuditService {
    void append(AuditOperation operation, Long targetId, List<String> changedFields);
}
