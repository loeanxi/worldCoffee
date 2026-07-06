package cn.lx.worldcoffee.common.config;

import cn.lx.worldcoffee.common.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // 让 Spring Security 认可 CORS 预检（OPTIONS 请求），避免 POST 请求返回 403
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        // 完全公开的接口（无写操作）
                        .requestMatchers(
                                "/api/users/register",
                                "/api/users/login",
                                "/api/admin/login",
                                "/api/coffee/search",
                                "/uploads/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/webjars/**",
                                "/swagger-resources/**"
                        ).permitAll()
                        // 帖子：GET 公开浏览，写操作需要登录
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/coffee/posts",
                                "/api/coffee/posts/*"
                        ).permitAll()
                        // 商品 & 分类：GET 公开浏览
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/shop/products",
                                "/api/shop/products/*",
                                "/api/shop/categories",
                                "/api/shop/categories/*"
                        ).permitAll()
                        // 优惠券：GET 列表公开，领取需要登录
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/shop/coupons",
                                "/api/shop/coupons/my"
                        ).permitAll()
                        // 秒杀：GET 活动列表公开，下单需要登录
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/shop/seckill/activities"
                        ).permitAll()
                        // 管理后台接口：需要 ADMIN_ROLE 权限
                        .requestMatchers("/api/admin/**").hasAuthority("ADMIN_ROLE")
                        .anyRequest().authenticated()
                )
                // 3. 把JWT拦截器插到Spring Security过滤器链里
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());

        return http.build();
    }

    /**
     * 全局 CORS 配置：与 WebMvcConfig 保持一致，
     * 让 Spring Security 在处理 OPTIONS 预检请求时直接放行，
     * 避免前端 POST 请求因 CORS 预检未通过而返回 403。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);  // 预检结果缓存 1 小时

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
