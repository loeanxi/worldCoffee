package cn.lx.worldcoffee.module.shop.service;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.common.security.SecurityUtils;
import cn.lx.worldcoffee.module.shop.dao.ShippingAddressDao;
import cn.lx.worldcoffee.module.shop.domain.ShippingAddress;
import cn.lx.worldcoffee.module.shop.domain.vo.AddressForm;
import cn.lx.worldcoffee.module.shop.domain.vo.AddressVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 收货地址服务。
 *
 * 职责：
 *   - 地址 CRUD（增删改查）
 *   - 默认地址管理（设为默认时，先把其他地址取消默认）
 *   - 归属校验（用户 A 不能操作用户 B 的地址，和帖子删除同理）
 *
 * 为什么单独拆出来：
 *   地址是独立的领域，跟订单、商品没有耦合。
 *   它的逻辑很简单——就是查数据库、转 VO、校验归属。
 *   从 ShopService 里拆出来让 ShopService 更聚焦。
 *
 * 依赖：
 *   - ShippingAddressDao：收货地址表 CRUD
 */
@Service
@RequiredArgsConstructor
public class AddressService {

    private final ShippingAddressDao addressDao;

    /**
     * 当前用户的所有地址列表（按创建时间倒序，最新的在前）。
     * SQL: SELECT * FROM shipping_address WHERE user_id = ? ORDER BY create_time DESC
     */
    public List<AddressVO> listAddresses() {
        Long userId = SecurityUtils.requireUserId();

        List<ShippingAddress> list = addressDao.selectList(
                new LambdaQueryWrapper<ShippingAddress>()
                        .eq(ShippingAddress::getUserId, userId)
                        .orderByDesc(ShippingAddress::getCreateTime));
        return list.stream().map(this::toAddressVO).collect(Collectors.toList());
    }

    /**
     * 地址详情（单条）。
     * 校验地址存在 + 属于当前用户。
     */
    public AddressVO getAddress(Long id) {
        Long userId = SecurityUtils.requireUserId();

        ShippingAddress addr = addressDao.selectById(id);
        if (addr == null) throw new ServiceException("地址不存在");
        if (!addr.getUserId().equals(userId)) throw new ServiceException("无权操作");

        return toAddressVO(addr);
    }

    /**
     * 新增地址。
     *
     * 如果设为默认地址（isDefault=true），先把该用户其他地址的 isDefault 全部置 0，
     * 保证同一用户只有一个默认地址。
     *
     * SQL 流程：
     *   1. [如果 isDefault] UPDATE shipping_address SET is_default = 0 WHERE user_id = ?
     *   2. INSERT INTO shipping_address (user_id, receiver_name, ...) VALUES (?, ...)
     */
    @Transactional(rollbackFor = Exception.class)
    public AddressVO createAddress(AddressForm form) {
        Long userId = SecurityUtils.requireUserId();

        // 如果设为默认，先把其他地址的非默认
        if (Boolean.TRUE.equals(form.getIsDefault())) {
            addressDao.update(null, new LambdaUpdateWrapper<ShippingAddress>()
                    .eq(ShippingAddress::getUserId, userId)
                    .set(ShippingAddress::getIsDefault, 0));
        }

        ShippingAddress addr = new ShippingAddress();
        addr.setUserId(userId);
        addr.setReceiverName(form.getReceiverName());
        addr.setPhone(form.getPhone());
        addr.setProvince(form.getProvince());
        addr.setCity(form.getCity());
        addr.setDistrict(form.getDistrict());
        addr.setDetail(form.getDetail());
        addr.setIsDefault(Boolean.TRUE.equals(form.getIsDefault()) ? 1 : 0);
        addr.setCreateTime(LocalDateTime.now());
        addressDao.insert(addr);

        return toAddressVO(addr);
    }

    /**
     * 修改地址。
     * 校验地址存在 + 属于当前用户。默认地址逻辑同 createAddress。
     */
    @Transactional(rollbackFor = Exception.class)
    public AddressVO updateAddress(Long id, AddressForm form) {
        Long userId = SecurityUtils.requireUserId();

        ShippingAddress addr = addressDao.selectById(id);
        if (addr == null) throw new ServiceException("地址不存在");
        if (!addr.getUserId().equals(userId)) throw new ServiceException("无权操作");

        // 如果设为默认，先把其他地址的非默认
        if (Boolean.TRUE.equals(form.getIsDefault())) {
            addressDao.update(null, new LambdaUpdateWrapper<ShippingAddress>()
                    .eq(ShippingAddress::getUserId, userId)
                    .set(ShippingAddress::getIsDefault, 0));
        }

        addr.setReceiverName(form.getReceiverName());
        addr.setPhone(form.getPhone());
        addr.setProvince(form.getProvince());
        addr.setCity(form.getCity());
        addr.setDistrict(form.getDistrict());
        addr.setDetail(form.getDetail());
        addr.setIsDefault(Boolean.TRUE.equals(form.getIsDefault()) ? 1 : 0);
        addressDao.updateById(addr);

        return toAddressVO(addr);
    }

    /**
     * 删除地址。
     * 校验地址存在 + 属于当前用户。
     */
    public void deleteAddress(Long id) {
        Long userId = SecurityUtils.requireUserId();

        ShippingAddress addr = addressDao.selectById(id);
        if (addr == null) throw new ServiceException("地址不存在");
        if (!addr.getUserId().equals(userId)) throw new ServiceException("无权操作");

        addressDao.deleteById(id);
    }

    // ==================== 内部工具 ====================

    /**
     * Entity → VO 转换。
     * isDefault 字段：数据库存的是 0/1（Integer），VO 里是 true/false（Boolean），
     * 所以这里做个 == 1 的转换。
     */
    private AddressVO toAddressVO(ShippingAddress addr) {
        return AddressVO.builder()
                .id(addr.getId())
                .receiverName(addr.getReceiverName())
                .phone(addr.getPhone())
                .province(addr.getProvince())
                .city(addr.getCity())
                .district(addr.getDistrict())
                .detail(addr.getDetail())
                .isDefault(addr.getIsDefault() == 1)
                .build();
    }
}
