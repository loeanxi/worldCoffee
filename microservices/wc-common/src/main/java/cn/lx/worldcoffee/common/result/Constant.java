package cn.lx.worldcoffee.common.result;

public class Constant {
    /**
     * JWT 签名密钥：优先读环境变量 MICROSERVICES_JWT_SECRET（README 已要求设置），
     * 未设置时回退到历史默认值。该默认值已随仓库历史泄露过，生产环境务必设置环境变量并轮换。
     */
    public static final String JWT_SECRET =
            System.getenv().getOrDefault("MICROSERVICES_JWT_SECRET", "REDACTED_JWT_SECRET");
    public static final long JWT_EXPIRATION = 7 * 24 * 60 * 60 * 1000L; // 7天
}