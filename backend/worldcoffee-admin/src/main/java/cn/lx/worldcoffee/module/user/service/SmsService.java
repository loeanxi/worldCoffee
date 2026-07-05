package cn.lx.worldcoffee.module.user.service;

import cn.hutool.core.util.RandomUtil;
import cn.lx.worldcoffee.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SmsService {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${sms.code-expire}")
    private long codeExpire;

    @Value("${sms.code-interval}")
    private long codeInterval;

    /**
     * 发送验证码到指定手机号
     * 返回验证码（方便调试，生产环境去掉）
     */
    public String sendCode(String phone) {
        // 1. 校验手机号格式
        if (!phone.matches("^1[3-9]\\d{9}$")){
            throw new ServiceException("手机号格式错误");
        }

        // 2. 检查发送间隔（防刷）
        String intervalKey = "sms:interval:" + phone;
        String lastSend = stringRedisTemplate.opsForValue().get(intervalKey);
        if (lastSend != null) {
            throw new ServiceException("发送太频繁，请稍后再试");
        }

        // 3. 生成 6 位随机验证码
        String code = RandomUtil.randomNumbers(6);

        // 4. 存入 Redis，key = sms:code:{phone}，过期 5 分钟
        stringRedisTemplate.opsForValue().set(
                "sms:code:" + phone, code, codeExpire, TimeUnit.SECONDS
        );

        // 5. 记录发送间隔，60 秒内不让再发
        stringRedisTemplate.opsForValue().set(
                intervalKey, "1", codeInterval, TimeUnit.SECONDS
        );

        // 6. TODO: 对接真实 SMS 服务商发送短信
        // 比如阿里云 SMS、腾讯云 SMS
        System.out.println("【验证码】手机 " + phone + " 的验证码是: " + code);

        return code;
    }
    /**
     * 校验验证码
     */
    public void verifyCode(String phone, String code) {
        if (code == null || code.isBlank()) {
            throw new ServiceException("验证码不能为空");
        }

        String key = "sms:code:" + phone;
        String correctCode = stringRedisTemplate.opsForValue().get(key);

        if (correctCode == null) {
            throw new ServiceException("验证码已过期，请重新发送");
        }
        if (!correctCode.equals(code.trim())) {
            throw new ServiceException("验证码错误");
        }

        // 校验通过后删除验证码（一次性）
        stringRedisTemplate.delete(key);
    }

}
