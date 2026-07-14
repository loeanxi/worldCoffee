package cn.lx.worldcoffee.module.shop.service;

import cn.lx.worldcoffee.module.shop.dao.CoffeeProductDao;
import cn.lx.worldcoffee.module.shop.domain.CoffeeProduct;
import cn.lx.worldcoffee.module.shop.domain.EsProduct;
import cn.lx.worldcoffee.module.shop.repostitory.EsProductRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EsSearchService {

    private final EsProductRepository esProductRepository;
    private final CoffeeProductDao productDao;

    /** 把所有商品从 MySQL 导入到 ES */    //第一次用，之后每次新增/修改商品时也要调（把mysql中的商品同步到es）
    public void importAll() {
        //SELECT * FROM coffee_product   null 就是"不加 WHERE"，全表查出来。
        List<CoffeeProduct> products = productDao.selectList(null);//没有条件，全查。
        List<EsProduct> esProducts = products.stream().map(this::toEsProduct).collect(Collectors.toList());
        esProductRepository.saveAll(esProducts);
        // 你看到的（方法引用）
        //.map(this::toEsProduct)
        // 等价于（Lambda 表达式）
        // .map(p -> this.toEsProduct(p))
        // 再等价于（匿名内部类）
        //.map(new Function<CoffeeProduct, EsProduct>() {
        //    @Override
        //    public EsProduct apply(CoffeeProduct p) {
        //        return toEsProduct(p);
        //    }
        //})
        //:: 就是"把这个方法当作参数传进去"的意思。
        //this 指的是 EsSearchService 这个对象本身
        //this 在 Java 里永远指向"当前正在跑这个方法的对象"。
        // 你现在在 importAll() 方法里，这个方法属于 EsSearchService，
        // 所以 this 就是那个 EsSearchService 实例。

    }
    /** 搜索商品（按名称和描述） */ //搜索方法。 前端调这个，传个关键词，返回匹配的商品。
    public List<EsProduct> search(String keyword){
        //没传关键词 → 返回 ES 里所有商品（相当于全量）。
        if (keyword == null || keyword.isBlank()){
            ArrayList<EsProduct> all = new ArrayList<>();
            esProductRepository.findAll().forEach(all::add);
            return all;
        }
        //传了关键词 → 去 ES 里搜 name 或 description 字段包含关键词的商品。
        return esProductRepository.findByNameLikeOrDescriptionLike(keyword,keyword);
    }



    // TODO 这样以后如果加了商品管理的接口，就在 Service 里调这两个方法。
    /** 新增或修改商品时，同步到 ES */
    public void saveProductToEs(CoffeeProduct product){
        esProductRepository.save(toEsProduct(product));
    }

    /** 删除商品时，从 ES 移除 */
    public void deleteProductFromEs(Long productId){
        esProductRepository.deleteById(productId);
    }

    @PostConstruct
    //@PostConstruct 告诉 Spring："这个类创建完之后，自动跑这个方法一次。"
    /**
     * Spring 启动
     *     │
     *     ├─ 创建 EsSearchService（因为你用了 @Service）
     *     │
     *     ├─ Spring 看到 @PostConstruct
     *     │
     *     └─ 自动调用 initData()
     */
    public void initDate() {
        // 启动时自动把 MySQL 数据导入 ES
        //启动时如果直接调 importAll()，Spring 要等它执行完才能继续启动。如果 ES 还没准备好，整个项目就启动失败了。
        /**
         * 主线程（Spring 启动）              新线程（数据导入）
         * ─────────────────                ────────────────
         * 创建 EsSearchService
         *                                     │
         * 执行其他 Bean 的创建                │ 等 5 秒（给 ES 时间准备）
         *                                     │
         * 项目启动完成 ✅                     │
         *                                     │
         *                                     ├─ importAll()
         *                                     │   ├─ MySQL 查商品
         *                                     │   └─ 存到 ES
         *                                     │
         *                                     └─ 打印"导入完成"
         *                                     主线程不阻塞，项目秒启动。新线程 5 秒后默默把数据导进去。
         */
        new Thread(() -> {
            try {
                //public interface Runnable {
                //    public abstract void run();  // 就这一个方法
                //}
                //Java 有个规则：如果一个接口只有一个方法，可以用 Lambda 表达式 () -> {} 直接代替。
                //为什么要等 5 秒？ 因为 Spring 启动时 ES 连接可能还没就绪，等一会确保 ES 能连上再导数据。
                Thread.sleep(5000); //// 等 5 秒，确保 ES 连接就绪
                importAll();
                System.out.println("es数据导入完成");
            }catch (Exception e){
                System.err.println("es数据导入失败： " + e.getMessage());
            }
        }).start();
    }


    /** CoffeeProduct → EsProduct */
    //转换方法。 把 MySQL 的 CoffeeProduct 转成 ES 的 EsProduct，只复制需要的字段。
    private EsProduct toEsProduct(CoffeeProduct p) {
        EsProduct e = new EsProduct();
        e.setId(p.getId());
        e.setName(p.getName());
        e.setDescription(p.getDescription());
        e.setOrigin(p.getOrigin());
        e.setRoastLevel(p.getRoastLevel());
        e.setPrice(p.getPrice());
        e.setStatus(p.getStatus());
        return e;
        /**
         *                 写入                          搜索
         * MySQL ──→ importAll() ──→ ES ──→ search("哥伦比亚") ──→ 返回结果
         *                                         │
         *                                    IK 分词器拆成
         *                                    "哥伦比亚" + "咖啡"
         *                                         │
         *                                    匹配 name 或 description
         */
    }
}
