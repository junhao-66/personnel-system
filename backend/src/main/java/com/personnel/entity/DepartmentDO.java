package com.personnel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("sys_department")
public class DepartmentDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String departmentName;
    private String departmentCode;
    private Long managerUserId;
    private Long selfApproverUserId;
    private Integer sortOrder;
    private Integer isEnabled;
    private LocalDateTime createTime;
}
