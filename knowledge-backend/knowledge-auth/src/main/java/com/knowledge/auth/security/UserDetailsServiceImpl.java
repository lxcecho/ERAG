package com.knowledge.auth.security;

import com.knowledge.system.entity.User;
import com.knowledge.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户详情加载实现
 * 供登录认证（AuthenticationManager）与 JWT 过滤器（按用户名加载权限）共用。
 *
 * @author: lxcechoo@gmail.com
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserService userService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userService.getByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        List<String> roles = userService.listRoleKeysByUserId(user.getId());
        List<String> perms = userService.listPermsByUserId(user.getId());
        return new SecurityUserDetails(user, roles, perms);
    }
}
