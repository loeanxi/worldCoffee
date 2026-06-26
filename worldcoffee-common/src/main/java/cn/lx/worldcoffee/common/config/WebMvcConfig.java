package cn.lx.worldcoffee.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射 /uploads/** 到文件系统的 uploads 目录
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/", "file:./uploads/");
    }
}
