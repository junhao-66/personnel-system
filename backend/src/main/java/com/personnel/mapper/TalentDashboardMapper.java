package com.personnel.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personnel.dto.req.TalentQueryReqDTO;
import com.personnel.entity.TalentEmployeeDO;
import com.personnel.entity.TalentGroupDO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TalentDashboardMapper {
    List<TalentGroupDO> selectGroups(@Param("query") TalentQueryReqDTO query);
    Page<TalentEmployeeDO> selectEmployeePage(
        Page<TalentEmployeeDO> page,
        @Param("query") TalentQueryReqDTO query
    );
    TalentEmployeeDO selectEmployee(@Param("id") Long id);
}
