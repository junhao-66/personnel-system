package com.personnel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personnel.entity.AssessmentSessionDO;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AssessmentSessionMapper extends BaseMapper<AssessmentSessionDO> {
    AssessmentSessionDO selectForUpdate(@Param("id") Long id);
    int saveDraft(
        @Param("id") Long id,
        @Param("revision") int revision,
        @Param("answers") String answers,
        @Param("now") LocalDateTime now
    );
    int completeDraft(
        @Param("id") Long id,
        @Param("revision") int revision,
        @Param("answers") String answers,
        @Param("result") String result,
        @Param("now") LocalDateTime now
    );
}
