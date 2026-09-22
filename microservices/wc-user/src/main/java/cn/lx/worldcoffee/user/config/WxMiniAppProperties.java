package cn.lx.worldcoffee.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序配置。
 *
 * mock-enabled=true（默认，仅开发环境）：不调微信服务器，用固定 openid 完成登录闭环，
 *   便于本地/CI 验证小程序一键登录链路；
 * mock-enabled=false（生产）：走真实 code2Session，appid/app-secret 必须配置齐全，
 *   app-secret 只允许存在于服务端配置/环境变量，严禁下发到任何前端。
 */
@Data
@Component
@ConfigurationProperties(prefix = "wechat.miniapp")
public class WxMiniAppProperties {

    /** 小程序 AppID */
    private String appid = "";

    /** 小程序 AppSecret（仅服务端持有） */
    private String appSecret = "";

    /** 开发期 mock 登录开关 */
    private boolean mockEnabled = true;
}
