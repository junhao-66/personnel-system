package com.personnel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personnel.entity.UserDO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
    Long lockUser(@Param("id") Long id);
    List<Long> selectEnabledAdminIdsForUpdate();
    Page<UserDO> selectEmployeePage(
        Page<UserDO> page,
        @Param("keyword") String keyword,
        @Param("departmentId") Long departmentId,
        @Param("employmentStatus") String employmentStatus
    );

    @Select(
        "SELECT id, user_name, real_name, phone, role_code, audit_status, audit_reason, reviewed_at, auth_version, user_status, department_id, position_id, create_time, update_time FROM sys_user WHERE audit_status = 'PENDING' ORDER BY create_time ASC"
    )
    List<UserDO> selectPendingUsers();
}
