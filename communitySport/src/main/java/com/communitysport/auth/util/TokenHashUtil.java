package com.communitysport.auth.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Token 哈希工具。
 *
 * <p>本项目在服务端持久化 refreshToken 时，不直接存 refreshToken 明文，
 * 而是存它的 SHA-256 哈希（tokenHash）。
 *
 * <p>这样做的意义：
 * <p>- 即使数据库泄露，攻击者也拿不到可直接使用的 refreshToken
 * <p>- 服务端在 refresh/logout 时只需要把客户端传来的 refreshToken 做同样的 sha256，
 *   与数据库的 tokenHash 比对即可
 *
 * <p>注意：
 * <p>- SHA-256 是不可逆哈希，不要指望从 hash 反推出原 token
 * <p>- Token 本身已经是高随机字符串（类似密码），对它做 SHA-256 主要是为了“避免明文落库”
 */
public final class TokenHashUtil {

    private TokenHashUtil() {
    }

    public static String sha256Hex(String raw) {
        // 把输入字符串做 SHA-256，并输出 16 进制字符串。
        // 如果你的 JDK 版本较新，可以使用 HexFormat 简化 hex 编码。
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
