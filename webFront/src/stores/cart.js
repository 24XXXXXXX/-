import { defineStore } from 'pinia'
import { getCart, updateCart } from '@/api/equipment'

export const useCartStore = defineStore('cart', {
  state: () => ({
    // items：购物车条目列表。
    // 数据来源：后端购物车接口（getCart）。
    //
    // 一般每个 item 至少包含：equipmentId、quantity、price、title/cover 等展示字段（以接口返回为准）。
    items: [],

    // loading：用于控制页面 loading 态（避免重复点击、展示骨架屏等）。
    loading: false
  }),
  
  getters: {
    // totalCount：购物车总件数（按 quantity 求和）。
    totalCount: (state) => state.items.reduce((sum, item) => sum + item.quantity, 0),

    // totalAmount：购物车总金额（按 price * quantity 求和）。
    // 注意：price 的单位/精度由后端决定（整数分/元浮点等），前端这里只做乘加，不做四舍五入策略。
    totalAmount: (state) => state.items.reduce((sum, item) => sum + item.price * item.quantity, 0),

    // isEmpty：是否为空，用于页面空态展示。
    isEmpty: (state) => state.items.length === 0
  },
  
  actions: {
    async fetchCart() {
      // 拉取购物车：
      // - 页面进入购物车、或对购物车做增删改后，都可以调用它刷新最新数据。
      this.loading = true
      try {
        const res = await getCart()
        // 约定后端返回：{ items: [...] }
        this.items = res.data?.items || []
      } catch (e) {
        console.error('Failed to fetch cart:', e)
      } finally {
        this.loading = false
      }
    },
    
    async addItem(equipmentId, quantity = 1) {
      // 添加商品到购物车：
      // - 实际写入逻辑在后端（updateCart）
      // - 成功后重新 fetchCart，确保本地状态与后端一致（避免“本地乐观更新”和后端规则不一致）
      try {
        await updateCart({ equipmentId, quantity })
        await this.fetchCart()
        return true
      } catch (e) {
        console.error('Failed to add item:', e)
        return false
      }
    },
    
    async updateItemQuantity(equipmentId, quantity) {
      // 更新某个条目的数量：
      // - quantity 由页面输入框/步进器决定
      // - 后端通常会做库存上限、最小数量等校验（前端这里不假设规则）
      try {
        await updateCart({ equipmentId, quantity })
        await this.fetchCart()
        return true
      } catch (e) {
        console.error('Failed to update item:', e)
        return false
      }
    },
    
    async removeItem(equipmentId) {
      // 删除条目：
      // - 本项目用 quantity = 0 表达“从购物车移除”
      // - 属于一种常见的“单接口 upsert”设计：新增/修改/删除都走同一个 updateCart
      try {
        await updateCart({ equipmentId, quantity: 0 })
        await this.fetchCart()
        return true
      } catch (e) {
        console.error('Failed to remove item:', e)
        return false
      }
    },
    
    clearCart() {
      // 清空本地购物车状态（纯前端行为）。
      // 通常在：退出登录、下单成功后调用。
      this.items = []
    }
  }
})
