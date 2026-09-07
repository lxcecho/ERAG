/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.auth.service.impl;

import com.knowledge.auth.dto.PasswordRequest;
import com.knowledge.auth.dto.ProfileRequest;
import com.knowledge.auth.dto.UserInfoVo;
import com.knowledge.auth.jwt.JwtTokenProvider;
import com.knowledge.auth.security.SecurityUserDetails;
import com.knowledge.auth.service.AvatarStorageService;
import com.knowledge.common.exception.BizException;
import com.knowledge.common.tenant.TenantProperties;
import com.knowledge.system.entity.User;
import com.knowledge.system.service.SysTenantService;
import com.knowledge.system.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuthServiceImpl} 账户模块单元测试：updateProfile 资料更新、changePassword 旧密码校验与更新。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserService userService;

    @Mock
    private SysTenantService sysTenantService;

    @Mock
    private TenantProperties tenantProps;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AvatarStorageService avatarStorageService;

    @InjectMocks
    private AuthServiceImpl authService;

    /** 构造当前登录用户并写入 SecurityContext */
    private User loginUser(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setTenantId(1L);
        user.setUsername("admin");
        user.setPassword("$2a$10$storedHash");
        user.setNickname("旧昵称");
        user.setEmail("old@x.com");
        user.setPhone("");
        user.setStatus(0);

        SecurityUserDetails details = new SecurityUserDetails(user, List.of("ADMIN"), List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
        return user;
    }

    /* ---------- updateProfile ---------- */

    @Test
    void should_update_profile_fields_and_return_vo() {
        User user = loginUser(1L);
        when(userService.getById(1L)).thenReturn(user);

        ProfileRequest req = new ProfileRequest();
        req.setNickname("新昵称");
        req.setEmail("new@x.com");
        req.setPhone("13800000000");

        UserInfoVo vo = authService.updateProfile(req);

        assertEquals("新昵称", vo.getNickname());
        assertEquals("new@x.com", vo.getEmail());
        assertEquals("13800000000", vo.getPhone());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).updateById(captor.capture());
        assertEquals("新昵称", captor.getValue().getNickname());
        assertEquals("new@x.com", captor.getValue().getEmail());
    }

    @Test
    void should_throw_when_user_not_found() {
        loginUser(1L);
        when(userService.getById(1L)).thenReturn(null);

        ProfileRequest req = new ProfileRequest();
        req.setNickname("x");

        assertThrows(BizException.class, () -> authService.updateProfile(req));
    }

    /* ---------- changePassword ---------- */

    @Test
    void should_reject_wrong_old_password() {
        User user = loginUser(1L);
        when(userService.getById(1L)).thenReturn(user);
        when(passwordEncoder.matches("wrongOld", "$2a$10$storedHash")).thenReturn(false);

        PasswordRequest req = new PasswordRequest();
        req.setOldPassword("wrongOld");
        req.setNewPassword("newPass123");

        BizException ex = assertThrows(BizException.class, () -> authService.changePassword(req));
        assertEquals("旧密码不正确", ex.getMessage());
    }

    @Test
    void should_update_password_when_old_password_matches() {
        User user = loginUser(1L);
        when(userService.getById(1L)).thenReturn(user);
        when(passwordEncoder.matches("oldPass", "$2a$10$storedHash")).thenReturn(true);
        when(passwordEncoder.matches("newPass123", "$2a$10$storedHash")).thenReturn(false);
        when(passwordEncoder.encode("newPass123")).thenReturn("$2a$10$newHash");

        PasswordRequest req = new PasswordRequest();
        req.setOldPassword("oldPass");
        req.setNewPassword("newPass123");

        authService.changePassword(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).updateById(captor.capture());
        assertEquals("$2a$10$newHash", captor.getValue().getPassword());
    }
}
