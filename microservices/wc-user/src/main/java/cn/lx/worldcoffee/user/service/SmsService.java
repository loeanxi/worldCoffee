package cn.lx.worldcoffee.user.service;

import cn.hutool.core.util.RandomUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SmsService {

    private final StringRedisTemplate redisTemplate;

    /** 发送短信验证码 */
    public String sendSmsCode(String phone) {
        String code = RandomUtil.randomNumbers(6);
        // 存 Redis，5分钟过期
        redisTemplate.opsForValue().set("sms:code:" + phone, code, 5, TimeUnit.MINUTES);
        // TODO: 实际调用短信服务商 API（阿里云/腾讯云等）
        System.out.println("【模拟短信】手机号：" + phone + "，验证码：" + code);
        return code;
    }

    /** 校验短信验证码 */
    public boolean verifySmsCode(String phone, String code) {
        String storedCode = redisTemplate.opsForValue().get("sms:code:" + phone);
        if (storedCode == null) return false;
        if (!storedCode.equals(code)) return false;
        // 校验成功后删除，防止重复使用
        redisTemplate.delete("sms:code:" + phone);
        return true;
    }
}
