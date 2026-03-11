package com.communitysport.auth.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.communitysport.auth.dto.LoginRequest;
import com.communitysport.auth.dto.LogoutRequest;
import com.communitysport.auth.dto.RegisterRequest;
import com.communitysport.auth.dto.RefreshRequest;
import com.communitysport.auth.dto.TokenPairResponse;
import com.communitysport.auth.entity.AuthRefreshToken;
import com.communitysport.auth.entity.SysRole;
import com.communitysport.auth.entity.SysUser;
import com.communitysport.auth.entity.SysUserRole;
import com.communitysport.auth.mapper.AuthRefreshTokenMapper;
import com.communitysport.auth.mapper.SysRoleMapper;
import com.communitysport.auth.mapper.SysUserMapper;
import com.communitysport.auth.mapper.SysUserRoleMapper;
import com.communitysport.auth.util.TokenHashUtil;
import com.communitysport.security.JwtProperties;
import com.communitysport.security.JwtService;
import com.communitysport.wallet.service.WalletService;

import io.jsonwebtoken.Claims;

/**
 * 认证业务服务（登录/注册/刷新/退出）。
 *
 * <p>为什么要把逻辑放在 Service 而不是 Controller？
 * <p>- 认证流程往往跨多个资源：用户表、角色表、refresh token 表、钱包初始化等
 * <p>- Service 更适合承载事务（@Transactional）与复杂规则
 *
 * <p>本项目认证的关键点：
 * <p>1）AccessToken + RefreshToken（双 Token）
 * <p>2）RefreshToken 不只存在客户端，服务端也会落库（auth_refresh_token），用于“可控失效”
 * <p>3）refreshToken 落库时不存明文，而存 SHA-256 哈希（TokenHashUtil），降低泄露风险
 * <p>4）支持 deviceId：把同一账号的不同设备会话区分开（刷新/退出更可控）
 */
@Service
public class AuthService {

    private static final String ADMIN_PLACEHOLDER_HASH = "CHANGE_ME_BCRYPT";

    private final SysUserMapper sysUserMapper;

    private final SysRoleMapper sysRoleMapper;

    private final SysUserRoleMapper sysUserRoleMapper;

    private final AuthRefreshTokenMapper authRefreshTokenMapper;

    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    private final JwtProperties jwtProperties;

    private final WalletService walletService;

    public AuthService(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper,
            AuthRefreshTokenMapper authRefreshTokenMapper,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties,
            WalletService walletService
    ) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.authRefreshTokenMapper = authRefreshTokenMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.walletService = walletService;
    }

    @Transactional
    public TokenPairResponse login(LoginRequest request) {
        // 登录流程（核心链路）：
        // 1）参数校验
        // 2）查询用户 + 状态校验（是否启用）
        // 3）校验密码（并支持“旧密码格式升级到 BCrypt”）
        // 4）确保钱包账户初始化（钱包体系是全业务支付的基础）
        // 5）生成 token pair（access/refresh）
        // 6）refreshToken 落库（hash + 设备信息 + 过期时间）
        if (request == null || !StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username/password required");
        }

        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUsername, request.getUsername()));

        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            // 用户不存在或已被冻结：统一返回未授权（避免暴露“用户名是否存在”）
            throw unauthorized();
        }

        boolean passwordOk = verifyPasswordAndMaybeUpgrade(user, request.getPassword());
        if (!passwordOk) {
            throw unauthorized();
        }

        walletService.ensureAccountInitialized(user.getId());

        user.setLastLoginAt(LocalDateTime.now());
        sysUserMapper.updateById(user);

        List<String> roleCodes = sysRoleMapper.selectRoleCodesByUserId(user.getId());
        String accessToken = jwtService.createAccessToken(user.getId(), user.getUsername(), roleCodes);
        String refreshToken = jwtService.createRefreshToken(user.getId(), user.getUsername());

        // refreshToken 需要服务端持久化：
        // - logout 可以吊销
        // - refresh 时可以校验是否被吊销/是否过期/是否匹配 deviceId
        persistRefreshToken(user.getId(), refreshToken, request.getDeviceId());

        TokenPairResponse resp = new TokenPairResponse();
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setRoles(roleCodes);
        resp.setTokenType("Bearer");
        resp.setAccessToken(accessToken);
        resp.setRefreshToken(refreshToken);
        resp.setExpiresInSeconds(jwtProperties.getAccessTokenTtlMinutes() * 60);
        return resp;
    }

    @Transactional
    public TokenPairResponse register(RegisterRequest request) {
        // 注册流程：
        // 1）参数校验（用户名/密码长度）
        // 2）唯一性校验（用户名/手机号/邮箱）
        // 3）创建用户（密码用 BCrypt 哈希）
        // 4）赋予默认角色 USER
        // 5）初始化钱包账户
        // 6）签发 token pair + refreshToken 落库
        if (request == null || !StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username/password required");
        }
        String username = request.getUsername().trim();
        String password = request.getPassword();
        String phone = StringUtils.hasText(request.getPhone()) ? request.getPhone().trim() : null;
        String email = StringUtils.hasText(request.getEmail()) ? request.getEmail().trim() : null;
        if (username.length() < 3 || username.length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username length must be 3-50");
        }
        if (password.length() < 6 || password.length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password length must be 6-50");
        }

        SysUser exists = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (exists != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名已经被注册");
        }

        if (phone != null) {
            SysUser phoneExists = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, phone));
            if (phoneExists != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "手机号已经被注册");
            }
        }

        if (email != null) {
            SysUser emailExists = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, email));
            if (emailExists != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱已经被注册");
            }
        }

        SysRole userRole = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, "USER"));
        if (userRole == null || userRole.getId() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "USER role not initialized");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setPhone(phone);
        user.setEmail(email);
        if (StringUtils.hasText(request.getNickname())) {
            user.setNickname(request.getNickname().trim());
        }
        user.setStatus(1);
        user.setLastLoginAt(LocalDateTime.now());

        try {
            sysUserMapper.insert(user);
        } catch (DuplicateKeyException e) {
            SysUser againUsername = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
            if (againUsername != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名已经被注册");
            }
            if (phone != null) {
                SysUser againPhone = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, phone));
                if (againPhone != null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "手机号已经被注册");
                }
            }
            if (email != null) {
                SysUser againEmail = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, email));
                if (againEmail != null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱已经被注册");
                }
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "注册失败，请稍后重试");
        }

        if (user.getId() == null) {
            SysUser again = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
            if (again == null || again.getId() == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create user");
            }
            user = again;
        }

        walletService.ensureAccountInitialized(user.getId());

        SysUserRole ur = new SysUserRole();
        ur.setUserId(user.getId());
        ur.setRoleId(userRole.getId());
        ur.setCreatedAt(LocalDateTime.now());
        try {
            sysUserRoleMapper.insert(ur);
        } catch (DuplicateKeyException ignored) {
        }

        List<String> roleCodes = sysRoleMapper.selectRoleCodesByUserId(user.getId());
        String accessToken = jwtService.createAccessToken(user.getId(), user.getUsername(), roleCodes);
        String refreshToken = jwtService.createRefreshToken(user.getId(), user.getUsername());
        persistRefreshToken(user.getId(), refreshToken, request.getDeviceId());

        TokenPairResponse resp = new TokenPairResponse();
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setRoles(roleCodes);
        resp.setTokenType("Bearer");
        resp.setAccessToken(accessToken);
        resp.setRefreshToken(refreshToken);
        resp.setExpiresInSeconds(jwtProperties.getAccessTokenTtlMinutes() * 60);
        return resp;
    }

    @Transactional
    public void logout(LogoutRequest request) {
        // 退出登录（服务端吊销 refreshToken）：
        // - AccessToken 是短效的，不做服务端保存时一般无法主动吊销
        // - 所以本项目重点吊销 refreshToken，防止客户端继续刷新
        //
        // 注意：这里的“吊销”是把 auth_refresh_token.revoked 置为 1。
        // 之后 refresh 时会校验 revoked=0，保证吊销生效。
        if (request == null || !StringUtils.hasText(request.getRefreshToken())) {
            return;
        }

        Claims claims;
        try {
            claims = jwtService.parseAndValidate(request.getRefreshToken());
        } catch (Exception e) {
            return;
        }

        String tokenType = claims.get(JwtService.CLAIM_TOKEN_TYPE, String.class);
        if (!JwtService.TOKEN_TYPE_REFRESH.equals(tokenType)) {
            return;
        }

        Number uidNum = claims.get(JwtService.CLAIM_UID, Number.class);
        Long userId = uidNum == null ? null : uidNum.longValue();
        if (userId == null) {
            return;
        }

        String tokenHash = TokenHashUtil.sha256Hex(request.getRefreshToken());
        // 为什么只存 hash？
        // - 如果数据库泄露，攻击者拿不到可直接使用的 refreshToken 明文
        // - 服务端只需要比对 hash 是否一致即可
        LambdaQueryWrapper<AuthRefreshToken> qw = new LambdaQueryWrapper<AuthRefreshToken>()
            .eq(AuthRefreshToken::getUserId, userId)
            .eq(AuthRefreshToken::getTokenHash, tokenHash)
            .eq(AuthRefreshToken::getRevoked, 0);

        if (StringUtils.hasText(request.getDeviceId())) {
            String deviceId = request.getDeviceId();
            // deviceId 逻辑：
            // - 如果前端携带 deviceId，优先匹配同设备
            // - 同时兼容历史数据（deviceId 为空的旧记录）
            qw.and(w -> w.eq(AuthRefreshToken::getDeviceId, deviceId).or().isNull(AuthRefreshToken::getDeviceId));
        }

        AuthRefreshToken row = authRefreshTokenMapper.selectOne(qw);
        if (row == null) {
            return;
        }

        row.setRevoked(1);
        row.setLastUsedAt(LocalDateTime.now());
        authRefreshTokenMapper.updateById(row);
    }

    @Transactional
    public TokenPairResponse refresh(RefreshRequest request) {
        // 刷新 token：
        // 1）解析 refreshToken（必须是 token_type=refresh）
        // 2）去数据库查询该 refreshToken 的 hash，确保它未被吊销
        // 3）吊销旧 refreshToken（单次使用，避免重放攻击）
        // 4）签发新的 token pair，并把新的 refreshToken 落库
        if (request == null || !StringUtils.hasText(request.getRefreshToken())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "refreshToken required");
        }

        Claims claims;
        try {
            claims = jwtService.parseAndValidate(request.getRefreshToken());
        } catch (Exception e) {
            throw unauthorized();
        }

        String tokenType = claims.get(JwtService.CLAIM_TOKEN_TYPE, String.class);
        if (!JwtService.TOKEN_TYPE_REFRESH.equals(tokenType)) {
            throw unauthorized();
        }

        Number uidNum = claims.get(JwtService.CLAIM_UID, Number.class);
        Long userId = uidNum == null ? null : uidNum.longValue();
        if (userId == null) {
            throw unauthorized();
        }

        String tokenHash = TokenHashUtil.sha256Hex(request.getRefreshToken());

        LambdaQueryWrapper<AuthRefreshToken> qw = new LambdaQueryWrapper<AuthRefreshToken>()
            .eq(AuthRefreshToken::getUserId, userId)
            .eq(AuthRefreshToken::getTokenHash, tokenHash)
            .eq(AuthRefreshToken::getRevoked, 0);

        if (StringUtils.hasText(request.getDeviceId())) {
            String deviceId = request.getDeviceId();
            qw.and(w -> w.eq(AuthRefreshToken::getDeviceId, deviceId).or().isNull(AuthRefreshToken::getDeviceId));
        }

        AuthRefreshToken stored = authRefreshTokenMapper.selectOne(qw);
        if (stored == null) {
            throw unauthorized();
        }

        LocalDateTime now = LocalDateTime.now();
        if (stored.getExpiresAt() != null && stored.getExpiresAt().isBefore(now)) {
            // refreshToken 已过期：拒绝刷新
            throw unauthorized();
        }

        stored.setRevoked(1);
        stored.setLastUsedAt(now);
        authRefreshTokenMapper.updateById(stored);

        // 关键：刷新时吊销旧 refreshToken（一次性令牌思想），避免 refreshToken 被截获后反复刷新。

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw unauthorized();
        }

        List<String> roleCodes = sysRoleMapper.selectRoleCodesByUserId(userId);
        String accessToken = jwtService.createAccessToken(userId, user.getUsername(), roleCodes);
        String newRefreshToken = jwtService.createRefreshToken(userId, user.getUsername());

        persistRefreshToken(userId, newRefreshToken, request.getDeviceId());

        TokenPairResponse resp = new TokenPairResponse();
        resp.setUserId(userId);
        resp.setUsername(user.getUsername());
        resp.setRoles(roleCodes);
        resp.setTokenType("Bearer");
        resp.setAccessToken(accessToken);
        resp.setRefreshToken(newRefreshToken);
        resp.setExpiresInSeconds(jwtProperties.getAccessTokenTtlMinutes() * 60);
        return resp;
    }

    private void persistRefreshToken(Long userId, String refreshToken, String deviceId) {
        // refreshToken 持久化：
        // - 从 token 中解析过期时间（exp）写入数据库
        // - tokenHash 存 sha256(refreshToken)
        // - revoked=0 表示可用
        Claims rtClaims = jwtService.parseAndValidate(refreshToken);
        Instant expInstant = rtClaims.getExpiration().toInstant();
        LocalDateTime expiresAt = LocalDateTime.ofInstant(expInstant, ZoneId.systemDefault());

        AuthRefreshToken row = new AuthRefreshToken();
        row.setUserId(userId);
        row.setTokenHash(TokenHashUtil.sha256Hex(refreshToken));
        row.setDeviceId(StringUtils.hasText(deviceId) ? deviceId : null);
        row.setExpiresAt(expiresAt);
        row.setRevoked(0);
        row.setLastUsedAt(LocalDateTime.now());
        authRefreshTokenMapper.insert(row);
    }

    private boolean verifyPasswordAndMaybeUpgrade(SysUser user, String rawPassword) {
        // 密码校验与“旧密码升级”策略：
        //
        // 背景：
        // - 历史数据/初始化管理员可能存在非 BCrypt 的密码存储方式
        // - 当前系统要求 BCrypt，因此这里做兼容：
        //   - 如果看起来是 BCrypt：使用 passwordEncoder.matches 校验
        //   - 否则按旧方式对比（包括管理员占位 hash 的特殊逻辑）
        //   - 校验成功后立刻升级为 BCrypt 并写回数据库
        String stored = user.getPasswordHash();
        if (!StringUtils.hasText(stored)) {
            return false;
        }

        boolean looksBcrypt = stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$") || stored.startsWith("$2$");
        if (looksBcrypt) {
            return passwordEncoder.matches(rawPassword, stored);
        }

        boolean ok;
        if (ADMIN_PLACEHOLDER_HASH.equals(stored)) {
            ok = "123456".equals(rawPassword);
        } else {
            ok = stored.equals(rawPassword);
        }

        if (ok) {
            // 升级存储：把旧格式密码转换为 BCrypt
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            sysUserMapper.updateById(user);
        }

        return ok;
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码不正确");
    }
}
