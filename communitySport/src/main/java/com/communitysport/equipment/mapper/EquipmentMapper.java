package com.communitysport.equipment.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.communitysport.equipment.entity.Equipment;

@Mapper
public interface EquipmentMapper extends BaseMapper<Equipment> {

     // 关键并发点：扣减库存（防超卖）
     //
     // 这个 SQL 是一个典型的“CAS（Compare-And-Set）式条件更新”写法：
     // - UPDATE ... SET stock = stock - qty
     // - WHERE stock >= qty  （只有库存足够时才允许扣减）
     // - AND status = 'ON_SALE'（只允许对上架商品扣减，避免下架/禁售商品被继续售卖）
     //
     // 为什么这样写能防超卖？
     // - 在高并发下，多个线程同时下单时都可能“先读到库存足够”。
     // - 但真正生效的是这条 UPDATE：数据库会保证单条更新语句的原子性。
     // - 只有第一批成功把 stock 扣到足够低的线程会返回 updated=1，后续线程因 stock 已不足而 updated=0。
     //
     // 返回值语义：
     // - 返回更新行数（一般是 0 或 1）
     // - 业务层看到 <=0 就应当认为“库存不足/商品不可售”，并回滚当前下单事务。
     //
     // 注意：
     // - 该方法自身不负责“锁定/解锁”概念，而是依赖条件更新实现乐观并发控制。
     // - 它应当被调用在 @Transactional 的下单事务中，与订单落库、钱包扣款等保持一致性。
    @Update("UPDATE equipment SET stock = stock - #{quantity} WHERE id = #{id} AND stock >= #{quantity} AND status = 'ON_SALE'")
    int subtractStock(@Param("id") Long id, @Param("quantity") int quantity);
}
