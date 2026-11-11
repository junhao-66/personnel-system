package com.personnel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personnel.entity.AssessmentScaleDO;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AssessmentScaleMapper extends BaseMapper<AssessmentScaleDO> {
    int publishDraft(
        @Param("id") Long id,
        @Param("adminId") Long adminId,
        @Param("now") LocalDateTime now
    );
    int archivePublished(@Param("id") Long id, @Param("now") LocalDateTime now);
}
