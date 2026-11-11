package com.personnel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personnel.entity.AttendanceRuleDO;

public interface AttendanceRuleMapper extends BaseMapper<AttendanceRuleDO> {
    AttendanceRuleDO selectForUpdate();
}
