package com.personnel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personnel.entity.SystemSettingsDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SystemSettingsMapper extends BaseMapper<SystemSettingsDO> {
    SystemSettingsDO selectForUpdate();
}
