package cn.lx.worldcoffee.module.shop.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;

@Data
@Document(indexName = "products") // ES 索引名，类似 MySQL 的表名  存到 ES 的 products 索引里
public class EsProduct {
    @Id //ES 里的文档 ID
    private Long id;

    @Field(type = FieldType.Text, analyzer = "ik_smart")  //文本字段，用 IK 中文分词（搜"哥伦比亚"能匹配到"哥伦比亚咖啡"）
    private String name;        // 商品名，用中文分词器

    @Field(type = FieldType.Text, analyzer = "ik_smart")
    private String description; // 商品描述，用中文分词器

    @Field(type = FieldType.Keyword)     //精确匹配字段，不分词
    private String origin;      // 产地，精确匹配

    @Field(type = FieldType.Keyword)
    private String roastLevel;  // 烘焙度，精确匹配

    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Integer)
    private Integer status;

    @Field(type = FieldType.Long)
    private Long category;
}
