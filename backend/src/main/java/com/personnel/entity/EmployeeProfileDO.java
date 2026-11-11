package com.personnel.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import lombok.Data;

@Data
@TableName("employee_profile")
public class EmployeeProfileDO {

    @TableId
    private Long userId;

    private LocalDate hireDate;
    private String employmentStatus;
}
