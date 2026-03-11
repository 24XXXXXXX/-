<template>
  <div class="order-detail-page">
    <n-spin :show="loading">
      <n-card v-if="order">
        <template #header>
          <div class="header-row">
            <n-button text @click="$router.back()">
              <n-icon><ArrowBackOutline /></n-icon> 返回
            </n-button>
            <span>订单详情</span>
            <n-tag :type="getStatusType(order.status)">{{ getStatusText(order.status) }}</n-tag>
          </div>
        </template>

        <!-- 订单信息 -->
        <n-descriptions :column="2" label-placement="left">
          <n-descriptions-item label="订单号">{{ order.orderNo }}</n-descriptions-item>
          <n-descriptions-item label="下单时间">{{ formatDate(order.createdAt) }}</n-descriptions-item>
          <n-descriptions-item label="收货人">{{ order.receiverName || order.address?.name }}</n-descriptions-item>
          <n-descriptions-item label="联系电话">{{ order.receiverPhone || order.address?.phone }}</n-descriptions-item>
          <n-descriptions-item label="收货地址" :span="2">
            {{
              order.receiverAddress ||
              `${order.address?.province || ''}${order.address?.city || ''}${order.address?.district || ''}${order.address?.detail || ''}`
            }}
          </n-descriptions-item>
          <n-descriptions-item v-if="order.trackingNo" label="物流单号">{{ order.trackingNo }}</n-descriptions-item>
        </n-descriptions>

        <n-divider />

        <!-- 商品列表 -->
        <h3>商品清单</h3>
        <div class="goods-list">
          <div v-for="item in order.items" :key="item.equipmentId || item.id" class="goods-item">
            <img :src="getItemCoverUrl(item)" class="goods-img" />
            <div class="goods-info">
              <h4>{{ item.name || item.equipmentName }}</h4>
              <p class="price">¥{{ item.price }} × {{ item.quantity }}</p>
            </div>
            <span class="subtotal">¥{{ Number(item.subtotal ?? item.price * item.quantity).toFixed(2) }}</span>
            <!-- 评价按钮 -->
            <n-button
              v-if="isStatus(order.status, 'received') && !item.reviewed"
              size="small"
              type="primary"
              @click="openReviewModal(item)"
            >
              评价
            </n-button>
            <n-tag v-else-if="item.reviewed" type="success" size="small">已评价</n-tag>
          </div>
        </div>

        <n-divider />

        <!-- 金额汇总 -->
        <div class="amount-summary">
          <div class="row">
            <span>商品总额</span>
            <span>¥{{ order.totalAmount }}</span>
          </div>
          <div class="row">
            <span>运费</span>
            <span>¥0.00</span>
          </div>
          <div class="row total">
            <span>实付金额</span>
            <span class="amount">¥{{ order.totalAmount }}</span>
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="action-bar" v-if="isStatus(order.status, 'shipped')">
          <n-button type="primary" @click="handleReceive">确认收货</n-button>
        </div>
      </n-card>
    </n-spin>

    <!-- 评价弹窗 -->
    <n-modal v-model:show="showReviewModal" preset="card" title="评价商品" style="width: 500px">
      <n-form ref="reviewFormRef" :model="reviewForm" :rules="reviewRules">
        <n-form-item label="评分" path="rating">
          <n-rate v-model:value="reviewForm.rating" />
        </n-form-item>
        <n-form-item label="评价内容" path="content">
          <n-input
            v-model:value="reviewForm.content"
            type="textarea"
            placeholder="请输入评价内容"
            :rows="4"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showReviewModal = false">取消</n-button>
          <n-button type="primary" :loading="submittingReview" @click="submitReviewForm">提交评价</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowBackOutline } from '@vicons/ionicons5'
import { getMyOrderDetail, confirmReceive, submitReview, getEquipmentDetail } from '@/api/equipment'
import dayjs from 'dayjs'

// 用户端「器材订单详情」页面
//
// 页面职责：
// - 展示单笔订单的收货信息、商品清单、金额汇总、物流信息（若有）
// - 在“已发货”状态允许用户确认收货（状态机：shipped -> received）
// - 在“已完成/已收货”后允许对每个商品逐条评价（item.reviewed 控制按钮/标签）
//
// 数据流：
// onMounted -> loadReviewedCache -> fetchDetail -> 渲染
// - fetchDetail 会调用 getMyOrderDetail(orderId)
// - 为了补齐商品封面：会对 items 中缺封面的 equipmentId 再调用 getEquipmentDetail 做 coverUrl 缓存
//
// 关于 reviewed：
// - 后端通常会返回 item.reviewed，但为了避免“评价后立即刷新仍显示未评价”的体验问题，
//   这里用 sessionStorage 做了一层 reviewedEquipmentCache 兜底（按订单维度存储）。
// - 这不是强一致来源：最终以服务端为准；缓存只是 UI 体验层。

// 权限边界：
// - getMyOrderDetail/confirmReceive/submitReview 都需要登录，并由后端保证“只能操作自己的订单”。

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const order = ref(null)

const showReviewModal = ref(false)
const submittingReview = ref(false)
const reviewFormRef = ref(null)
const currentReviewItem = ref(null)
const reviewForm = reactive({
  rating: 5,
  content: ''
})

const reviewRules = {
  rating: { required: true, type: 'number', min: 1, message: '请选择评分' },
  content: { required: true, message: '请输入评价内容' }
}

const formatDate = (date) => dayjs(date).format('YYYY-MM-DD HH:mm')

const statusMap = {
  created: { text: '待付款', type: 'warning' },
  paid: { text: '待发货', type: 'info' },
  shipped: { text: '待收货', type: 'primary' },
  received: { text: '已完成', type: 'success' },
  cancelled: { text: '已取消', type: 'default' },
  canceled: { text: '已取消', type: 'default' }
}

// 注意：statusMap 的 key 统一转小写，是为了兼容后端可能返回的不同大小写/拼写（cancelled/canceled）。

const equipmentCoverCache = reactive({})
const reviewedEquipmentCache = reactive({})

const getReviewedStorageKey = () => `equipment_order_reviewed_${route.params.id}`

const loadReviewedCache = () => {
  // 从 sessionStorage 恢复“本订单的已评价商品集合”。
  // 这样即使刚评价完立刻刷新页面，也能快速把按钮切到“已评价”。
  try {
    const raw = sessionStorage.getItem(getReviewedStorageKey())
    if (!raw) return
    const parsed = JSON.parse(raw)
    if (parsed && typeof parsed === 'object') {
      for (const [k, v] of Object.entries(parsed)) {
        reviewedEquipmentCache[k] = v
      }
    }
  } catch (e) {
    // ignore
  }
}

const saveReviewedCache = () => {
  // 保存 reviewedEquipmentCache 到 sessionStorage（按订单维度隔离）。
  try {
    sessionStorage.setItem(getReviewedStorageKey(), JSON.stringify(reviewedEquipmentCache))
  } catch (e) {
    // ignore
  }
}

const markReviewed = (equipmentId) => {
  // 标记某个商品已评价：
  // - 写入 sessionStorage 缓存
  // - 同时把当前页面 order.items 中对应条目的 review 字段改为 true，立刻更新 UI
  if (!equipmentId) return
  reviewedEquipmentCache[String(equipmentId)] = true
  saveReviewedCache()
  const items = order.value?.items
  if (Array.isArray(items)) {
    for (const it of items) {
      if (String(it?.equipmentId) === String(equipmentId)) it.reviewed = true
    }
  }
}

const normalizeImageUrl = (url) => {
  if (url === undefined || url === null) return ''
  const s = String(url).trim()
  if (!s) return ''
  if (s.toLowerCase() === 'noimage') return ''
  return s
}

const getItemCoverUrl = (item) => {
  // 商品封面渲染策略：
  // 1) 优先使用订单 item 自带的 coverUrl（如果后端在下单时就把快照信息写进订单）
  // 2) 否则用 equipmentCoverCache（通过 getEquipmentDetail 补齐）
  // 3) 兜底 placeholder
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

const fetchDetail = async () => {
  // 拉取订单详情：
  // - 获取订单基本信息 + items
  // - 应用 reviewedEquipmentCache（把已评价的 item 标为 reviewed=true）
  // - 为缺失封面的商品补齐 coverUrl（逐个 getEquipmentDetail，并缓存到 equipmentCoverCache）
  loading.value = true
  try {
    const res = await getMyOrderDetail(route.params.id)
    order.value = res.data

    const items = order.value?.items
    if (Array.isArray(items)) {
      for (const it of items) {
        const equipmentId = it?.equipmentId
        if (!equipmentId) continue
        if (reviewedEquipmentCache[String(equipmentId)]) it.reviewed = true
      }
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
    }
  } catch (e) {
    window.$message?.error('获取订单详情失败')
  } finally {
    loading.value = false
  }
}

const handleReceive = async () => {
  // 确认收货：
  // - 仅在 shipped 状态显示按钮（见 template）
  // - 触发后端状态机：shipped -> received
  // - 成功后刷新详情
  try {
    await confirmReceive(order.value.id)
    window.$message?.success('确认收货成功')
    fetchDetail()
  } catch (e) {
    window.$message?.error('操作失败')
  }
}

const openReviewModal = (item) => {
  // 打开评价弹窗：item 来自 order.items。
  currentReviewItem.value = item
  reviewForm.rating = 5
  reviewForm.content = ''
  showReviewModal.value = true
}

const submitReviewForm = async () => {
  // 提交商品评价：
  // - 后端通常会校验“是否已购买/是否已完成/是否重复评价”
  // - 这里对 409（已评价）做了特殊兜底：直接 markReviewed 并提示用户
  try {
    await reviewFormRef.value?.validate()
    submittingReview.value = true
    const equipmentId = currentReviewItem.value?.equipmentId
    if (!equipmentId) {
      window.$message?.error('缺少器材信息')
      return
    }
    await submitReview({
      equipmentId,
      rating: reviewForm.rating,
      content: reviewForm.content
    })
    window.$message?.success('评价成功')
    markReviewed(equipmentId)
    showReviewModal.value = false
    fetchDetail()
  } catch (e) {
    const status = e?.response?.status
    if (status === 409) {
      const equipmentId = currentReviewItem.value?.equipmentId
      markReviewed(equipmentId)
      window.$message?.warning('该商品已评价')
      showReviewModal.value = false
      fetchDetail()
      return
    }
    if (e?.message) window.$message?.error(e.message)
  } finally {
    submittingReview.value = false
  }
}

onMounted(() => {
  loadReviewedCache()
  fetchDetail()
})
</script>

<style scoped>
.order-detail-page {
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;
}
.header-row {
  display: flex;
  align-items: center;
  gap: 16px;
}
.goods-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.goods-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px;
  background: #fafafa;
  border-radius: 8px;
}
.goods-img {
  width: 60px;
  height: 60px;
  object-fit: cover;
  border-radius: 4px;
}
.goods-info {
  flex: 1;
}
.goods-info h4 {
  margin: 0 0 4px;
  font-size: 14px;
}
.goods-info .price {
  margin: 0;
  color: #999;
  font-size: 13px;
}
.subtotal {
  font-weight: 500;
  color: #f5222d;
  margin-right: 16px;
}
.amount-summary {
  max-width: 300px;
  margin-left: auto;
}
.amount-summary .row {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
}
.amount-summary .row.total {
  font-size: 16px;
  font-weight: 500;
  border-top: 1px solid #eee;
  padding-top: 12px;
}
.amount-summary .amount {
  color: #f5222d;
  font-size: 20px;
}
.action-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 24px;
}
</style>
