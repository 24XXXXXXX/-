<template>
  <div class="coach-earnings-page">
    <n-card>
      <div class="earnings-overview">
        <div class="stat-card">
          <span class="label">总收入</span>
          <span class="value">¥{{ formatMoney(stats.totalEarnings) }}</span>
        </div>
        <div class="stat-card">
          <span class="label">总支出</span>
          <span class="value">¥{{ formatMoney(totalExpenseDisplay) }}</span>
        </div>
        <div class="stat-card">
          <span class="label">可提现</span>
          <span class="value">¥{{ formatMoney(stats.availableBalance) }}</span>
        </div>
        <div class="stat-card">
          <span class="label">已提现</span>
          <span class="value">¥{{ formatMoney(stats.withdrawnAmount) }}</span>
        </div>
        <n-button type="primary" @click="$router.push('/coach/withdraw')">申请提现</n-button>
      </div>
    </n-card>

    <n-card title="收入明细" style="margin-top: 20px">
      <n-spin :show="loading">
        <div v-if="earnings.length" class="earnings-list">
          <div v-for="item in earnings" :key="item.id" class="earning-item">
            <div class="earning-info">
              <span class="earning-type">{{ getItemTitleText(item) }}</span>
              <span v-if="shouldShowItemDesc(item)" class="earning-desc">{{ getDescText(item.description) }}</span>
              <span class="earning-time">{{ formatDate(item.createdAt) }}</span>
            </div>
            <span :class="['earning-amount', Number(item.amount) < 0 ? 'expense' : 'income']">{{ formatSignedMoney(item.amount) }}</span>
          </div>
        </div>
        <n-empty v-else description="暂无收入记录" />
      </n-spin>

      <div class="pagination-wrap" v-if="total > 0">
        <n-pagination
          v-model:page="pagination.page"
          :page-size="pagination.pageSize"
          :item-count="total"
          @update:page="fetchEarnings"
        />
      </div>
    </n-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { http } from '@/api/http'
import dayjs from 'dayjs'

const loading = ref(false)
const stats = ref({})
const earnings = ref([])
const total = ref(0)

const totalExpenseDisplay = computed(() => {
  const rows = earnings.value
  let pageSum = 0
  if (Array.isArray(rows) && rows.length > 0) {
    for (const it of rows) {
      const a = Number(it?.amount)
      if (Number.isFinite(a) && a < 0) pageSum += Math.abs(a)
    }
  }

  const raw = stats.value?.totalExpense
  if (raw === undefined || raw === null) {
    return pageSum
  }

  const direct = Number(raw)
  if (!Number.isFinite(direct)) return pageSum

  if (direct === 0 && pageSum > 0) return pageSum
  return direct
})

const pagination = reactive({
  page: 1,
  pageSize: 20
})

const formatDate = (date) => dayjs(date).format('YYYY-MM-DD HH:mm')

const formatMoney = (v) => {
  const n = Number(v)
  if (!Number.isFinite(n)) return '0.00'
  return n.toFixed(2)
}

const formatSignedMoney = (v) => {
  const n = Number(v)
  if (!Number.isFinite(n) || n === 0) return '¥0.00'
  const sign = n > 0 ? '+' : '-'
  return `${sign}¥${Math.abs(n).toFixed(2)}`
}

const translateText = (text) => {
  if (!text || typeof text !== 'string') return ''
  const s = text.trim()
  if (!s) return ''

  const key = s.toLowerCase()
  const map = {
    'wallet topup': '钱包充值',
    'course earning': '课程收入',
    'video earning': '视频收入',
    'equipment order': '器材订单',
    'equipment_order': '器材订单',
    'course booking': '课程支付',
    'course refund': '课程退款',
    'coach video': '购买教学视频',
    'withdraw approved': '提现(已通过)',
    'withdraw rejected': '提现(已拒绝)',
    'withdraw pending': '提现(待审核)',
    'topup approved': '充值(已通过)',
    'topup rejected': '充值(已拒绝)',
    'topup pending': '充值(待审核)'
  }
  if (map[key]) return map[key]
  return ''
}

const typeMap = {
  course: '课程收入',
  video: '视频收入',
  COACH_COURSE_EARNING: '课程收入',
  COACH_VIDEO_EARNING: '视频收入',
  EQUIPMENT_ORDER: '器材订单',
  equipment_order: '器材订单',
  withdraw: '提现',
  topup: '充值',
  signin: '签到奖励',
  course_booking: '课程支付',
  video_purchase: '视频购买',
  tip: '打赏收入'
}

const getTypeText = (type) => {
  if (typeMap[type]) return typeMap[type]
  if (typeof type === 'string') {
    const lower = type.toLowerCase()
    if (typeMap[lower]) return typeMap[lower]
    const upper = type.toUpperCase()
    if (typeMap[upper]) return typeMap[upper]
  }
  return type
}

const getDescText = (desc) => {
  const translated = translateText(desc)
  if (translated) return translated
  return desc || ''
}

const normalizeText = (v) => {
  if (v === undefined || v === null) return ''
  return String(v).trim()
}

const getItemTitleText = (item) => {
  const typeText = getTypeText(item?.type)
  const descText = getDescText(item?.description)
  const t = normalizeText(typeText)
  const d = normalizeText(descText)
  if (!t) return d
  if (!d) return t
  if (d === t) return t
  if (d.startsWith(t)) return d
  return t
}

const shouldShowItemDesc = (item) => {
  const typeText = getTypeText(item?.type)
  const descText = getDescText(item?.description)
  const t = normalizeText(typeText)
  const d = normalizeText(descText)
  if (!d) return false
  if (!t) return true
  if (d === t) return false
  if (d.startsWith(t)) return false
  return true
}

const fetchStats = async () => {
  try {
    const res = await http.get('/api/coach/earnings/stats')
    stats.value = res.data || {}
  } catch (e) {
    console.error(e)
  }
}

const fetchEarnings = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.pageSize
    }
    const res = await http.get('/api/coach/earnings', { params })
    earnings.value = res.data?.items || res.data?.list || res.data || []
    total.value = res.data?.total ?? earnings.value.length
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchStats()
  fetchEarnings()
})
</script>

<style scoped>
.coach-earnings-page {
  padding: 20px;
}
.earnings-overview {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 40px;
}
.stat-card {
  display: flex;
  flex-direction: column;
}
.stat-card .label {
  font-size: 14px;
  color: #666;
}
.stat-card .value {
  font-size: 28px;
  font-weight: 600;
  color: #18a058;
}
.earnings-list {
  display: flex;
  flex-direction: column;
}
.earning-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #eee;
}
.earning-item:last-child {
  border-bottom: none;
}
.earning-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.earning-type {
  font-weight: 500;
}
.earning-desc {
  font-size: 13px;
  color: #666;
}
.earning-time {
  font-size: 12px;
  color: #999;
}
.earning-amount {
  font-size: 16px;
  font-weight: 500;
}
.earning-amount.income {
  color: #18a058;
}
.earning-amount.expense {
  color: #f5222d;
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
