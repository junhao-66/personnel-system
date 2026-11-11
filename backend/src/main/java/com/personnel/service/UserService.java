package com.personnel.service;

import com.personnel.dto.req.AccountContactReqDTO;
import com.personnel.dto.req.AccountPasswordReqDTO;
import com.personnel.dto.req.AdminUserCreateReqDTO;
import com.personnel.dto.req.UserAccessReqDTO;
import com.personnel.dto.req.UserAuditReqDTO;
import com.personnel.dto.req.UserLoginReqDTO;
import com.personnel.dto.req.UserRegisterReqDTO;
import com.personnel.vo.AccountReviewVO;
import com.personnel.vo.LoginVO;
import com.personnel.vo.UserVO;
import java.util.List;

public interface UserService {
    UserVO updateOwnContact(Long userId, AccountContactReqDTO request);
    void changeOwnPassword(Long userId, AccountPasswordReqDTO request);
    void lockUser(Long id);
    void register(UserRegisterReqDTO request);
    LoginVO login(UserLoginReqDTO request);
    AccountReviewVO getReviewStatus(UserLoginReqDTO request);
    void updateAccess(Long actorId, Long id, UserAccessReqDTO request);
    UserVO getById(Long id);
    List<UserVO> listByIds(java.util.Collection<Long> ids);
    void audit(Long id, UserAuditReqDTO request);
    List<UserVO> listPendingUsers();
    UserVO createUser(AdminUserCreateReqDTO request);
    List<UserVO> listUsers();
}
