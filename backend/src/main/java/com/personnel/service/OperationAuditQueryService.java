package com.personnel.service;

import com.personnel.common.base.PageResult;
import com.personnel.dto.req.OperationAuditQueryReqDTO;
import com.personnel.vo.OperationAuditVO;

public interface OperationAuditQueryService {
    PageResult<OperationAuditVO> listAuditLogs(Long actorId, OperationAuditQueryReqDTO query);
}
