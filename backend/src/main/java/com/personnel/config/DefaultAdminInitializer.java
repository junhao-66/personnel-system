package com.personnel.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personnel.entity.EmployeeProfileDO;
import com.personnel.entity.UserDO;
import com.personnel.mapper.EmployeeProfileMapper;
import com.personnel.mapper.UserMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DefaultAdminInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final EmployeeProfileMapper employeeProfileMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public DefaultAdminInitializer(
        UserMapper userMapper,
        EmployeeProfileMapper employeeProfileMapper
    ) {
        this.userMapper = userMapper;
        this.employeeProfileMapper = employeeProfileMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(String... args) {
        if (
            userMapper.selectCount(
                Wrappers.<UserDO>lambdaQuery().eq(UserDO::getUserName, "admin")
            ) >
            0
        ) return;
        UserDO admin = new UserDO();
        admin.setUserName("admin");
        admin.setPasswordHash(passwordEncoder.encode("admin123"));
        admin.setRealName("系统管理员");
        admin.setRoleCode("ADMIN");
        admin.setAuditStatus("APPROVED");
        admin.setUserStatus("ENABLED");
        userMapper.insert(admin);
        EmployeeProfileDO profile = new EmployeeProfileDO();
        profile.setUserId(admin.getId());
        profile.setEmploymentStatus("ACTIVE");
        employeeProfileMapper.insert(profile);
    }
}
