package com.personnel.convert;

import com.personnel.common.util.AssessmentJson;
import com.personnel.entity.JobPreferenceProfileDO;
import com.personnel.vo.JobProfileVO;
import java.time.LocalDateTime;

public final class JobPreferenceConvert {

    private JobPreferenceConvert() {}

    public static JobProfileVO toVO(JobPreferenceProfileDO row, AssessmentJson json) {
        return new JobProfileVO(
            row.getId(),
            row.getStatus(),
            row.getContentHash(),
            json.read(row.getDefinitionJson(), JobProfileVO.Definition.class)
        );
    }

    public static JobPreferenceProfileDO toDO(
        JobProfileVO.Definition definition,
        String serialized,
        String hash,
        Long actor,
        LocalDateTime now
    ) {
        var row = new JobPreferenceProfileDO();
        row.setPositionId(definition.positionId());
        row.setVersion(definition.version());
        row.setStatus("DRAFT");
        row.setDefinitionJson(serialized);
        row.setContentHash(hash);
        row.setCreatedBy(actor);
        row.setCreatedAt(now);
        return row;
    }
}
