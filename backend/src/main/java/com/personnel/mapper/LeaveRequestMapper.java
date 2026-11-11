package com.personnel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personnel.entity.LeaveRequestDO;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.*;

@Mapper
public interface LeaveRequestMapper extends BaseMapper<LeaveRequestDO> {
    java.util.List<LeaveRequestDO> selectApprovedPeriods(
        @Param("userIds") java.util.Collection<Long> userIds,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
    long countOverlapping(
        @Param("userId") Long userId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
    Page<LeaveRequestDO> selectLeavePage(
        Page<LeaveRequestDO> page,
        @Param("userId") Long userId,
        @Param("managerId") Long managerId,
        @Param("status") String status
    );
    int reviewPending(
        @Param("id") Long id,
        @Param("reviewerId") Long reviewerId,
        @Param("status") String status,
        @Param("comment") String comment,
        @Param("now") LocalDateTime now
    );
}
