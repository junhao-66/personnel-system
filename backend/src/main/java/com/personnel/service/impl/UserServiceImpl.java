package com.personnel.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personnel.common.constant.AuditOperation;
import com.personnel.common.exception.BusinessException;
import com.personnel.common.util.JwtUtil;
import com.personnel.common.util.RateLimitGuard;
import com.personnel.convert.UserConvert;
import com.personnel.dto.req.AccountContactReqDTO;
import com.personnel.dto.req.AccountPasswordReqDTO;
import com.personnel.dto.req.AdminUserCreateReqDTO;
import com.personnel.dto.req.UserAccessReqDTO;
import com.personnel.dto.req.UserAuditReqDTO;
import com.personnel.dto.req.UserLoginReqDTO;
import com.personnel.dto.req.UserRegisterReqDTO;
import com.personnel.entity.EmployeeProfileDO;
import com.personnel.entity.UserDO;
import com.personnel.mapper.EmployeeProfileMapper;
import com.personnel.mapper.UserMapper;
import com.personnel.service.OperationAuditService;
import com.personnel.service.OrganizationService;
import com.personnel.service.UserService;
import com.personnel.vo.AccountReviewVO;
import com.personnel.vo.LoginVO;
import com.personnel.vo.UserVO;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private UserDO requireActiveAccount(Long id) {
        var user = userMapper.selectById(id);
        if (
            user == null ||
            !"APPROVED".equals(user.getAuditStatus()) ||
            !"ENABLED".equals(user.getUserStatus())
        ) {
            throw new BusinessException("账号未审核通过或已停用");
        }
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateOwnContact(Long userId, AccountContactReqDTO request) {
        String phone = request.phone() == null ? "" : request.phone().trim();
        if (!phone.isEmpty() && !phone.matches("[+0-9][0-9() +\\-]{2,19}")) {
            throw new BusinessException("联系方式应为3至20位电话号码，可留空");
        }
        lockUser(userId);
        var user = requireActiveAccount(userId);
        String normalized = phone.isEmpty() ? null : phone;
        if (java.util.Objects.equals(normalized, user.getPhone())) return UserConvert.toVO(user);
        userMapper.update(
            null,
            Wrappers.<UserDO>lambdaUpdate()
                .eq(UserDO::getId, userId)
                .set(UserDO::getPhone, normalized)
        );
        audit.append(AuditOperation.USER_CONTACT, userId, List.of("phone"));
        user.setPhone(normalized);
        return UserConvert.toVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeOwnPassword(Long userId, AccountPasswordReqDTO request) {
        // BCrypt accepts at most 72 UTF-8 bytes; character count alone is insufficient.
        String next = request.newPassword();
        if (
            next == null ||
            next.isBlank() ||
            next.length() < 8 ||
            next.length() > 64 ||
            next.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
        ) {
            throw new BusinessException("新密码须为8至64个字符，UTF-8长度不超过72字节");
        }
        lockUser(userId);
        var user = requireActiveAccount(userId);
        if (
            request.currentPassword() == null ||
            request.currentPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length >
            72 ||
            !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())
        ) {
            throw new BusinessException("当前密码不正确");
        }
        if (passwordEncoder.matches(next, user.getPasswordHash())) throw new BusinessException(
            "新密码不能与当前密码相同"
        );
        userMapper.update(
            null,
            Wrappers.<UserDO>lambdaUpdate()
                .eq(UserDO::getId, userId)
                .set(UserDO::getPasswordHash, passwordEncoder.encode(next))
                .set(UserDO::getAuthVersion, version(user) + 1)
        );
        // Store only the fact of a credential update, never a password or hash.
        audit.append(AuditOperation.USER_PASSWORD_CHANGE, userId, List.of("credentialUpdated"));
    }

    @Override
    public void lockUser(Long id) {
        if (userMapper.lockUser(id) == null) throw new BusinessException("用户不存在");
    }

    private final UserMapper userMapper;
    private final EmployeeProfileMapper employeeProfileMapper;
    private final OrganizationService organizationService;
    private final JwtUtil jwtUtil;
    private final OperationAuditService audit;
    private final Clock clock;
    private final RateLimitGuard rateLimits;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserServiceImpl(
        UserMapper userMapper,
        EmployeeProfileMapper employeeProfileMapper,
        OrganizationService organizationService,
        JwtUtil jwtUtil,
        OperationAuditService audit,
        Clock clock,
        RateLimitGuard rateLimits
    ) {
        this.userMapper = userMapper;
        this.employeeProfileMapper = employeeProfileMapper;
        this.organizationService = organizationService;
        this.jwtUtil = jwtUtil;
        this.audit = audit;
        this.clock = clock;
        this.rateLimits = rateLimits;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(UserRegisterReqDTO request) {
        rateLimits.checkRegistration(request.userName());
        if (
            userMapper.selectCount(
                Wrappers.<UserDO>lambdaQuery().eq(UserDO::getUserName, request.userName())
            ) >
            0
        ) {
            throw new BusinessException("用户名已存在");
        }
        UserDO user = UserConvert.toDO(request, passwordEncoder.encode(request.password()));
        user.setRoleCode("EMPLOYEE");
        user.setAuditStatus("PENDING");
        user.setUserStatus("ENABLED");
        userMapper.insert(user);
    }

    @Override
    public LoginVO login(UserLoginReqDTO request) {
        UserDO user = authenticateCredentials(request);
        if (!"APPROVED".equals(user.getAuditStatus())) throw new BusinessException(
            "账号尚未通过审核，请查询审核状态"
        );
        if (!"ENABLED".equals(user.getUserStatus())) throw new BusinessException(
            "账号已被禁用，请联系管理员"
        );
        return new LoginVO(
            jwtUtil.createToken(user.getId(), user.getRoleCode(), version(user)),
            UserConvert.toVO(user)
        );
    }

    private long version(UserDO user) {
        return user.getAuthVersion() == null ? 0L : user.getAuthVersion();
    }

    private UserDO authenticateCredentials(UserLoginReqDTO request) {
        rateLimits.checkCredentials(request.userName());
        UserDO user = userMapper.selectOne(
            Wrappers.<UserDO>lambdaQuery().eq(UserDO::getUserName, request.userName().trim())
        );
        if (user != null) rateLimits.checkKnownAccount(user.getId());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("用户名或密码错误");
        }
        return user;
    }

    @Override
    public AccountReviewVO getReviewStatus(UserLoginReqDTO request) {
        var user = authenticateCredentials(request);
        return new AccountReviewVO(
            user.getUserName(),
            user.getAuditStatus(),
            user.getUserStatus(),
            "REJECTED".equals(user.getAuditStatus()) ? user.getAuditReason() : null,
            user.getReviewedAt()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAccess(Long actorId, Long id, UserAccessReqDTO request) {
        if (
            !List.of("ADMIN", "DEPARTMENT_MANAGER", "EMPLOYEE").contains(request.roleCode()) ||
            !List.of("ENABLED", "DISABLED").contains(request.userStatus())
        ) throw new BusinessException("角色或账号状态不合法");
        // Lock active administrators in a stable order before either operator or target is read.
        var administratorIds = userMapper.selectEnabledAdminIdsForUpdate();
        var actor = userMapper.selectById(actorId);
        if (
            actor == null ||
            !"ADMIN".equals(actor.getRoleCode()) ||
            !"APPROVED".equals(actor.getAuditStatus()) ||
            !"ENABLED".equals(actor.getUserStatus())
        ) throw new BusinessException("仅有效管理员可调整账号权限");
        lockUser(id);
        var target = userMapper.selectById(id);
        if (!"APPROVED".equals(target.getAuditStatus())) throw new BusinessException(
            "仅可调整已通过审核的账号"
        );
        var changed = new ArrayList<String>();
        if (!request.roleCode().equals(target.getRoleCode())) changed.add("roleCode");
        if (!request.userStatus().equals(target.getUserStatus())) changed.add("userStatus");
        if (changed.isEmpty()) return;
        if (id.equals(actorId)) throw new BusinessException(
            "不能修改自己的角色或停用自己的账号，请由其他管理员操作"
        );
        if (
            administratorIds.contains(id) &&
            administratorIds.size() <= 1 &&
            (!"ADMIN".equals(request.roleCode()) || !"ENABLED".equals(request.userStatus()))
        ) {
            throw new BusinessException("必须保留至少一个有效管理员");
        }
        if (
            "DEPARTMENT_MANAGER".equals(target.getRoleCode()) &&
            (!"DEPARTMENT_MANAGER".equals(request.roleCode()) ||
                !"ENABLED".equals(request.userStatus())) &&
            organizationService
                .listDepartments()
                .stream()
                .anyMatch(d -> id.equals(d.managerUserId()) || id.equals(d.selfApproverUserId()))
        ) {
            throw new BusinessException("请先移交该账号负责的部门，再调整角色或停用");
        }
        userMapper.update(
            null,
            Wrappers.<UserDO>lambdaUpdate()
                .eq(UserDO::getId, id)
                .set(UserDO::getRoleCode, request.roleCode())
                .set(UserDO::getUserStatus, request.userStatus())
                .set(UserDO::getAuthVersion, version(target) + 1)
        );
        audit.append(AuditOperation.USER_ACCESS, id, changed);
    }

    @Override
    public List<UserVO> listByIds(java.util.Collection<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return userMapper.selectBatchIds(ids).stream().map(UserConvert::toVO).toList();
    }

    @Override
    public UserVO getById(Long id) {
        UserDO user = userMapper.selectById(id);
        if (user == null) throw new BusinessException("用户不存在");
        return UserConvert.toVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, UserAuditReqDTO request) {
        if (
            !"APPROVED".equals(request.auditStatus()) && !"REJECTED".equals(request.auditStatus())
        ) {
            throw new BusinessException("审核状态不合法");
        }
        String reason = request.auditReason() == null ? "" : request.auditReason().trim();
        if (
            reason.length() > 500 || ("REJECTED".equals(request.auditStatus()) && reason.isBlank())
        ) {
            throw new BusinessException("拒绝审核必须填写500字以内的原因");
        }
        lockUser(id);
        UserDO user = userMapper.selectById(id);
        if (user == null) throw new BusinessException("用户不存在");
        if (!"PENDING".equals(user.getAuditStatus())) {
            if (
                request.auditStatus().equals(user.getAuditStatus()) &&
                (!"REJECTED".equals(request.auditStatus()) || reason.equals(user.getAuditReason()))
            ) return;
            throw new BusinessException("该申请已处理，请刷新审核列表");
        }
        userMapper.update(
            null,
            Wrappers.<UserDO>lambdaUpdate()
                .eq(UserDO::getId, id)
                .set(UserDO::getAuditStatus, request.auditStatus())
                .set(
                    UserDO::getAuditReason,
                    "REJECTED".equals(request.auditStatus()) ? reason : null
                )
                .set(UserDO::getReviewedAt, LocalDateTime.now(clock))
                .set(UserDO::getAuthVersion, version(user) + 1)
        );
        if (
            "APPROVED".equals(request.auditStatus()) && employeeProfileMapper.selectById(id) == null
        ) {
            EmployeeProfileDO profile = new EmployeeProfileDO();
            profile.setUserId(id);
            profile.setEmploymentStatus("ACTIVE");
            employeeProfileMapper.insert(profile);
        }
        audit.append(
            AuditOperation.USER_AUDIT,
            id,
            "REJECTED".equals(request.auditStatus())
                ? List.of("auditStatus", "auditReason")
                : List.of("auditStatus")
        );
    }

    @Override
    public List<UserVO> listPendingUsers() {
        return userMapper.selectPendingUsers().stream().map(UserConvert::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO createUser(AdminUserCreateReqDTO request) {
        if (!List.of("ADMIN", "DEPARTMENT_MANAGER", "EMPLOYEE").contains(request.roleCode())) {
            throw new BusinessException("角色不合法");
        }
        organizationService.validateAssignment(request.departmentId(), request.positionId());
        if (
            userMapper.selectCount(
                Wrappers.<UserDO>lambdaQuery().eq(UserDO::getUserName, request.userName())
            ) >
            0
        ) {
            throw new BusinessException("用户名已存在");
        }
        UserDO user = new UserDO();
        user.setUserName(request.userName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRealName(request.realName());
        user.setPhone(request.phone());
        user.setRoleCode(request.roleCode());
        user.setAuditStatus("APPROVED");
        user.setUserStatus("ENABLED");
        user.setDepartmentId(request.departmentId());
        user.setPositionId(request.positionId());
        userMapper.insert(user);
        EmployeeProfileDO profile = new EmployeeProfileDO();
        profile.setUserId(user.getId());
        profile.setEmploymentStatus("ACTIVE");
        employeeProfileMapper.insert(profile);
        audit.append(
            AuditOperation.USER_CREATE,
            user.getId(),
            List.of(
                "userName",
                "realName",
                "phone",
                "roleCode",
                "departmentId",
                "positionId",
                "auditStatus",
                "employmentStatus"
            )
        );
        return UserConvert.toVO(user);
    }

    @Override
    public List<UserVO> listUsers() {
        return userMapper
            .selectList(Wrappers.<UserDO>lambdaQuery().orderByDesc(UserDO::getCreateTime))
            .stream()
            .map(UserConvert::toVO)
            .toList();
    }
}
