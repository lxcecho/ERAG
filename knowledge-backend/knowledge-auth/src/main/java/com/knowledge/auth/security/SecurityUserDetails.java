package com.knowledge.auth.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.knowledge.system.entity.User;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 安全上下文用户详情
 * 封装用户基本信息与权限（角色以 ROLE_ 前缀、权限标识原样作为 authority）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class SecurityUserDetails implements UserDetails {

    private Long userId;
    private Long tenantId;
    private String username;
    @JsonIgnore
    private String password;
    private Integer status;
    private List<String> roles;
    private List<String> permissions;

    @JsonIgnore
    private Collection<? extends GrantedAuthority> authorities;

    public SecurityUserDetails(User user, List<String> roles, List<String> permissions) {
        this.userId = user.getId();
        this.tenantId = user.getTenantId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.status = user.getStatus();
        this.roles = roles;
        this.permissions = permissions;

        Set<SimpleGrantedAuthority> auths = new HashSet<>();
        // 角色 → ROLE_xxx（供 hasRole 使用）
        roles.forEach(r -> auths.add(new SimpleGrantedAuthority("ROLE_" + r)));
        // 权限标识 → authority（供 hasAuthority / @PreAuthorize 使用）
        permissions.forEach(p -> auths.add(new SimpleGrantedAuthority(p)));
        this.authorities = auths;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status != null && status == 0;
    }
}
