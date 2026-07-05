package cn.lx.worldcoffee.module.ai.tool;

import cn.lx.worldcoffee.module.coffee.service.CoffeeService;
import cn.lx.worldcoffee.module.shop.domain.EsProduct;
import cn.lx.worldcoffee.module.shop.repostitory.EsProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProductTool {
    private final EsProductRepository esProductRepository;

    @Tool(description = "根据关键词搜索商城中的咖啡商品，返回商品名称、价格、产地、烘焙度")
    public String searchProducts(String keyword) {
        List<EsProduct> products = esProductRepository.findByNameLikeOrDescriptionLike(keyword, keyword);
        if (products.isEmpty()) return "没有找到相关的商品";

        return products.stream()
                .limit(5)
                .map(p -> String.format("- %s：%.2f元（产地：%s，烘焙度：%s）",
                        p.getName(),p.getPrice(),p.getOrigin(),p.getRoastLevel()))
                .collect(Collectors.joining("\n"));
    }
}
