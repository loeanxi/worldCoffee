package cn.lx.worldcoffee.module.shop.repostitory;

import cn.lx.worldcoffee.module.shop.domain.EsProduct;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface EsProductRepository extends ElasticsearchRepository<EsProduct, Long> {

    List<EsProduct> findByNameLikeOrDescriptionLike(String name,String description);

}
