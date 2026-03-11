<template>
  <div class="cart-page">
    <n-card title="购物车">
      <n-spin :show="cartStore.loading">
        <div v-if="!cartStore.isEmpty">
          <div class="cart-list">
            <div v-for="item in cartStore.items" :key="item.equipmentId" class="cart-item">
              <n-checkbox v-model:checked="item.selected" @update:checked="updateSelection" />
              <img :src="item.coverUrl || '/placeholder.svg'" class="item-img" />
              <div class="item-info">
                <h4 class="item-name">{{ item.name }}</h4>
                <p class="item-price">¥{{ item.price }}</p>
              </div>
              <n-input-number
                :value="item.quantity"
                :min="1"
                :max="item.stock"
                @update:value="(val) => updateQuantity(item.equipmentId, val)"
              />
              <span class="item-total">¥{{ (item.price * item.quantity).toFixed(2) }}</span>
              <n-button text type="error" @click="removeItem(item.equipmentId)">
                <n-icon><TrashOutline /></n-icon>
              </n-button>
            </div>
          </div>

          <n-divider />

          <div class="cart-footer">
            <n-checkbox v-model:checked="allSelected" @update:checked="toggleSelectAll">全选</n-checkbox>
            <div class="footer-right">
              <span>已选 {{ selectedCount }} 件</span>
              <span class="total-price">合计: <em>¥{{ selectedTotal.toFixed(2) }}</em></span>
              <n-button type="primary" :disabled="selectedCount === 0" @click="goCheckout">
                去结算
              </n-button>
            </div>
          </div>
        </div>
        <n-empty v-else description="购物车是空的">
          <template #extra>
            <n-button type="primary" @click="$router.push('/equipment')">去逛逛</n-button>
          </template>
        </n-empty>
      </n-spin>
    </n-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { TrashOutline } from '@vicons/ionicons5'
import { useCartStore } from '@/stores/cart'

// 器材商城「购物车」页面
//
// 页面职责：
// - 展示购物车条目（cartStore.items）
// - 支持勾选/全选（selected 字段属于前端 UI 状态）
// - 支持修改数量与移除商品（通过 cartStore -> API 更新服务端购物车）
// - 计算已选商品数量与合计金额，并进入结算页
//
// 数据流：
// onMounted -> cartStore.fetchCart() -> items 渲染
// 用户操作（改数量/移除）-> cartStore.update/remove -> 后端购物车更新 -> 再同步回 store
//
// 结算跳转设计：
// - 这里把“勾选的商品 equipmentId 列表”写入 sessionStorage.checkoutItems
// - Checkout 页再根据这个 id 列表从 cartStore.items 中筛出结算清单
// - 好处：无需在路由 query 里塞一长串 id；坏处：跨端/多标签页一致性较弱（但作为简单实现可接受）

const router = useRouter()
const cartStore = useCartStore()

const allSelected = ref(false)

const selectedCount = computed(() => {
  // 已选商品数：根据 items 上的 selected 统计。
  return cartStore.items.filter(item => item.selected).length
})

const selectedTotal = computed(() => {
  // 已选合计金额：仅对 selected 的条目求和。
  return cartStore.items
    .filter(item => item.selected)
    .reduce((sum, item) => sum + item.price * item.quantity, 0)
})

const updateSelection = () => {
  // 当单项勾选变化时，反推 allSelected 的状态。
  allSelected.value = cartStore.items.length > 0 && cartStore.items.every(item => item.selected)
}

const toggleSelectAll = (checked) => {
  // 全选/全不选：直接批量修改每个 item 的 selected。
  // 注意：selected 是 UI 状态，不需要同步到后端。
  cartStore.items.forEach(item => {
    item.selected = checked
  })
}

const updateQuantity = async (equipmentId, quantity) => {
  // 修改数量：委托给 cartStore，由 store 负责调用 API 并刷新购物车数据。
  await cartStore.updateItemQuantity(equipmentId, quantity)
}

const removeItem = async (equipmentId) => {
  // 移除商品：委托给 cartStore。
  await cartStore.removeItem(equipmentId)
  window.$message?.success('已移除')
}

const goCheckout = () => {
  // 进入结算：
  // - 只允许对“已勾选”的商品结算
  // - 用 sessionStorage 保存 equipmentId 列表，给 Checkout 页读取
  const selectedItems = cartStore.items.filter(item => item.selected)
  if (selectedItems.length === 0) {
    window.$message?.warning('请选择商品')
    return
  }
  // 存储选中的商品ID到sessionStorage
  sessionStorage.setItem('checkoutItems', JSON.stringify(selectedItems.map(i => i.equipmentId)))
  router.push('/checkout')
}

onMounted(() => {
  // 初始化加载购物车。
  cartStore.fetchCart()
})
</script>

<style scoped>
.cart-page {
  padding: 20px;
  max-width: 1000px;
  margin: 0 auto;
}
.cart-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.cart-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
}
.item-img {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 4px;
}
.item-info {
  flex: 1;
}
.item-name {
  margin: 0 0 8px;
  font-size: 15px;
}
.item-price {
  margin: 0;
  color: #f5222d;
}
.item-total {
  font-size: 16px;
  font-weight: 500;
  color: #f5222d;
  min-width: 80px;
  text-align: right;
}
.cart-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.footer-right {
  display: flex;
  align-items: center;
  gap: 20px;
}
.total-price {
  font-size: 14px;
}
.total-price em {
  font-style: normal;
  font-size: 20px;
  color: #f5222d;
  font-weight: 600;
}
</style>
