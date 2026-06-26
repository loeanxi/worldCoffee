package cn.lx.worldcoffee.module.coffee.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
//用builder原因：
//需要构建不可变对象（CoffeePost 后续可能还要修改属性，Builder 常用来构建不可变对象）。
//实体字段超多（比如十几个字段），用多个重载构造器会参数顺序极易传错；
//字段区分必填、可选，Builder 可以链式赋值，不用传大量 null 占位；
//需要灵活构建多种不同属性组合的对象。

public class CommentVO {
    private Long id;
    private Long userId;
    private String username;       // 评论人昵称
    private String content;        // 评论内容
    private LocalDateTime createTime;
}
