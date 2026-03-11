package com.communitysport.wallet.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.communitysport.wallet.entity.WalletAccount;

/**
 * 钱包账户表 Mapper。
 *
 * <p>这里最关键的是两条 SQL：
 * <p>- addBalance：余额增加
 * <p>- subtractBalance：余额扣减（带 balance >= amount 条件）
 *
 * <p>为什么 subtractBalance 要带条件？
 * <p>- 这是“防止余额变负数”的核心并发控制点
 * <p>- 它利用数据库的原子条件更新：只有余额足够时才会更新成功
 * <p>- 更新行数 updated=0 时，业务层即可判定“余额不足”
 */
@Mapper
public interface WalletAccountMapper extends BaseMapper<WalletAccount> {

    @Update("UPDATE wallet_account SET balance = balance + #{delta} WHERE user_id = #{userId}")
    // 加余额：只要账户存在就会成功更新 1 行。
    int addBalance(@Param("userId") Long userId, @Param("delta") int delta);

    @Update("UPDATE wallet_account SET balance = balance - #{amount} WHERE user_id = #{userId} AND balance >= #{amount}")
    // 扣余额：余额不足时不会更新任何行（返回 0）。
    int subtractBalance(@Param("userId") Long userId, @Param("amount") int amount);
}
