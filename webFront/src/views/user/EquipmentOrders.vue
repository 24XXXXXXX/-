<template>
  <div class="orders-page">
    <n-card title="我的订单">
      <n-tabs v-model:value="activeTab" @update:value="handleTabChange">
        <n-tab-pane name="all" tab="全部" />
        <n-tab-pane name="created" tab="待付款" />
        <n-tab-pane name="paid" tab="待发货" />
        <n-tab-pane name="shipped" tab="待收货" />
        <n-tab-pane name="received" tab="已完成" />
      </n-tabs>

      <n-spin :show="loading">
        <div v-if="orders.length" class="order-list">
          <div v-for="order in orders" :key="order.id" class="order-card">
            <div class="order-header">
              <span class="order-no">订单号: {{ order.orderNo }}</span>
              <span class="order-time">{{ formatDate(order.createdAt) }}</span>
              <n-tag :type="getStatusType(order.status)">{{ getStatusText(order.status) }}</n-tag>
            </div>
            <div class="order-items">
              <div v-for="item in order.items" :key="item.equipmentId || item.id" class="order-item">
                <img :src="getItemCoverUrl(item)" class="item-img" />
                <div class="item-info">
                  <h4>{{ item.name || item.equipmentName }}</h4>
                  <p>¥{{ item.price }} × {{ item.quantity }}</p>
                </div>
              </div>
            </div>
            <div class="order-footer">
              <span class="total">共 {{ order.items?.length || 0 }} 件商品，合计: <em>¥{{ order.totalAmount }}</em></span>
              <n-space>
                <n-button v-if="isStatus(order.status, 'shipped')" type="primary" @click="handleReceive(order.id)">
                  确认收货
                </n-button>
                <n-button @click="goDetail(order.id)">查看详情</n-button>
              </n-space>
            </div>
          </div>
        </div>
        <n-empty v-else description="暂无订单" />
      </n-spin>

      <div class="pagination-wrap" v-if="total > 0">
        <n-pagination
          v-model:page="pagination.page"
          :page-size="pagination.pageSize"
          :item-count="total"
          @update:page="fetchOrders"
        />
      </div>
    </n-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMyOrders, getMyOrderDetail, confirmReceive, getEquipmentDetail } from '@/api/equipment'
import dayjs from 'dayjs'

// 用户端「我的器材订单」列表页
//
// 页面职责：
// - 展示当前用户的订单列表（分页）
// - 通过 tabs 做状态筛选（待付款/待发货/待收货/已完成）
// - 在列表页提供“确认收货”快捷入口（仅 shipped 状态展示）
// - 点击“查看详情”进入订单详情页
//
// 数据流（两阶段加载）：
// 1) fetchOrders：调用 getMyOrders(params) 拉取订单列表（通常只有订单摘要字段）
// 2) enrichOrders：为每个订单再调用 getMyOrderDetail(orderId) 拿到 items，才能在列表里展示商品清单
//    - 这种写法的优点：后端列表接口响应轻，缺点：会产生 N+1 请求（订单多时变慢）
//    - 若未来要优化，建议后端列表接口直接返回 items 摘要，或提供批量详情接口
//
// 图片补齐策略：
// - item 自带 coverUrl 优先
// - 否则通过 getEquipmentDetail(equipmentId) 获取 coverUrl 并写入 equipmentCoverCache
//
// 权限与状态机说明：
// - 本页所有接口都需要登录，且后端会保证“只能看自己的订单/只能确认自己的订单”
// - confirmReceive 属于敏感操作：会推进订单状态（shipped -> received），成功后需要刷新列表

const router = useRouter()
const loading = ref(false)
const orders = ref([])
const total = ref(0)
const activeTab = ref('all')

const pagination = reactive({
  page: 1,
  pageSize: 10
})

const formatDate = (date) => dayjs(date).format('YYYY-MM-DD HH:mm')

const statusMap = {
  created: { text: '待付款', type: 'warning' },
  paid: { text: '待发货', type: 'info' },
  shipped: { text: '待收货', type: 'primary' },
  received: { text: '已完成', type: 'success' },
  cancelled: { text: '已取消', type: 'default' },
  canceled: { text: '已取消', type: 'default' }
}

// statusMap 的 key 用小写，是为了兼容后端可能返回的大小写与不同拼写（cancelled/canceled）。

const equipmentCoverCache = reactive({})

const normalizeImageUrl = (url) => {
  if (url === undefined || url === null) return ''
  const s = String(url).trim()
  if (!s) return ''
  if (s.toLowerCase() === 'noimage') return ''
  return s
}

const getItemCoverUrl = (item) => {
  const direct = normalizeImageUrl(item?.coverUrl)
  if (direct) return direct

  const equipmentId = item?.equipmentId
  const cached = equipmentId ? normalizeImageUrl(equipmentCoverCache[equipmentId]) : ''
  return cached || '/placeholder.svg'
}

const normalizeStatusKey = (status) => {
  if (status === undefined || status === null) return ''
  return String(status).trim().toLowerCase()
}

const getStatusText = (status) => {
  const key = normalizeStatusKey(status)
  return statusMap[key]?.text || status
}

const getStatusType = (status) => {
  const key = normalizeStatusKey(status)
  return statusMap[key]?.type || 'default'
}

const isStatus = (status, expected) => normalizeStatusKey(status) === expected

const enrichOrders = async (rows) => {
  // enrichOrders：为列表行补齐 items。
  // 注意：这里是串行 await，会放大接口耗时；当前实现偏简单直观。
  // 若你希望更快，可以做并发 Promise.all + 并发上限（但属于逻辑改造，本轮先不动）。
  if (!Array.isArray(rows) || rows.length === 0) return
  for (const row of rows) {
    if (!row?.id) continue
    try {
      const detailResp = await getMyOrderDetail(row.id)
      const detail = detailResp?.data
      row.items = Array.isArray(detail?.items) ? detail.items : []

      const items = row.items
      for (const it of items) {
        const equipmentId = it?.equipmentId
        if (!equipmentId) continue
        if (equipmentCoverCache[equipmentId] !== undefined) continue
        try {
          const equipmentResp = await getEquipmentDetail(equipmentId)
          equipmentCoverCache[equipmentId] = equipmentResp?.data?.coverUrl
        } catch (e) {
          equipmentCoverCache[equipmentId] = null
        }
      }
    } catch (e) {
      row.items = []
    }
  }
}

const fetchOrders = async () => {
  // 拉取订单列表：
  // - page/pageSize 作为分页参数
  // - status：非 all 时把 tab name 转成大写（created/paid/shipped/received -> CREATED/PAID/...）
  // - 返回字段兼容：list/items/records/content
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      pageSize: pagination.pageSize
    }
    if (activeTab.value !== 'all') {
      params.status = String(activeTab.value).toUpperCase()
    }
    const res = await getMyOrders(params)
    const list = res.data?.list || res.data?.items || res.data?.records || res.data?.content || []
    orders.value = (Array.isArray(list) ? list : []).map(o => ({ ...o, items: [] }))
    total.value = res.data?.total ?? orders.value.length
    await enrichOrders(orders.value)
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const handleTabChange = () => {
  pagination.page = 1
  fetchOrders()
}

const handleReceive = async (id) => {
  // 列表页确认收货：成功后刷新订单列表。
  try {
    await confirmReceive(id)
    window.$message?.success('确认收货成功')
    fetchOrders()
  } catch (e) {
    window.$message?.error('操作失败')
  }
}

const goDetail = (id) => {
  router.push(`/user/orders/${id}`)
}

onMounted(() => {
  fetchOrders()
})
</script>

<style scoped>
.orders-page {
  padding: 20px;
}
.order-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 20px;
}
.order-card {
  border: 1px solid #eee;
  border-radius: 8px;
  overflow: hidden;
}
.order-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  background: #fafafa;
}
.order-no {
  font-weight: 500;
}
.order-time {
  color: #999;
  font-size: 13px;
  margin-left: auto;
  margin-right: 10px;
}
.order-items {
  padding: 16px;
}
.order-item {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}
.order-item:last-child {
  margin-bottom: 0;
}
.item-img {
  width: 60px;
  height: 60px;
  object-fit: cover;
  border-radius: 4px;
}
.item-info h4 {
  margin: 0 0 4px;
  font-size: 14px;
}
.item-info p {
  margin: 0;
  color: #999;
  font-size: 13px;
}
.order-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-top: 1px solid #eee;
  background: #fafafa;
}
.total em {
  font-style: normal;
  color: #f5222d;
  font-weight: 600;
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
