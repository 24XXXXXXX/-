<template>
  <div class="admin-dashboard">
    <n-spin :show="loading">
      <div class="stats-row">
        <n-card class="stat-card">
          <div class="stat-content">
            <span class="stat-label">近7日订单</span>
            <span class="stat-value">{{ totalOrders || 0 }}</span>
          </div>
          <n-icon size="40" color="#18a058"><CartOutline /></n-icon>
        </n-card>
        <n-card class="stat-card">
          <div class="stat-content">
            <span class="stat-label">近7日消费净额</span>
            <span class="stat-value">¥{{ formatAmount(netPayAmount) }}</span>
          </div>
          <n-icon size="40" color="#2080f0"><CashOutline /></n-icon>
        </n-card>
        <n-card class="stat-card">
          <div class="stat-content">
            <span class="stat-label">近7日新增用户</span>
            <span class="stat-value">{{ metrics.newUsers || 0 }}</span>
          </div>
          <n-icon size="40" color="#f0a020"><PeopleOutline /></n-icon>
        </n-card>
        <n-card class="stat-card">
          <div class="stat-content">
            <span class="stat-label">近7日新增投诉</span>
            <span class="stat-value">{{ metrics.complaintsCreated || 0 }}</span>
          </div>
          <n-icon size="40" color="#d03050"><AlertCircleOutline /></n-icon>
        </n-card>
      </div>

      <div class="charts-row">
        <n-card title="近7日收入汇总" class="chart-card">
          <div class="order-stats">
            <div class="order-stat-item">
              <span class="order-label">消费金额</span>
              <n-progress type="line" :percentage="100" />
              <span class="order-count">¥{{ formatAmount(metrics.totalPayAmount) }}</span>
            </div>
            <div class="order-stat-item">
              <span class="order-label">退款金额</span>
              <n-progress type="line" :percentage="100" status="error" />
              <span class="order-count">¥{{ formatAmount(metrics.totalRefundAmount) }}</span>
            </div>
            <div class="order-stat-item">
              <span class="order-label">消费净额</span>
              <n-progress type="line" :percentage="100" status="success" />
              <span class="order-count">¥{{ formatAmount(netPayAmount) }}</span>
            </div>
            <div class="order-stat-item">
              <span class="order-label">充值金额</span>
              <n-progress type="line" :percentage="100" status="info" />
              <span class="order-count">¥{{ formatAmount(metrics.totalTopupAmount) }}</span>
            </div>
            <div class="order-stat-item">
              <span class="order-label">签到奖励</span>
              <n-progress type="line" :percentage="100" status="warning" />
              <span class="order-count">¥{{ formatAmount(metrics.signinRewardAmount) }}</span>
            </div>
          </div>
        </n-card>
        <n-card title="订单分布" class="chart-card">
          <div class="order-stats">
            <div class="order-stat-item">
              <span class="order-label">场地预约</span>
              <n-progress type="line" :percentage="getPercentage(metrics.venueBookingsCreated)" />
              <span class="order-count">{{ metrics.venueBookingsCreated || 0 }}</span>
            </div>
            <div class="order-stat-item">
              <span class="order-label">课程预约</span>
              <n-progress type="line" :percentage="getPercentage(metrics.courseBookingsCreated)" status="info" />
              <span class="order-count">{{ metrics.courseBookingsCreated || 0 }}</span>
            </div>
            <div class="order-stat-item">
              <span class="order-label">器材订单</span>
              <n-progress type="line" :percentage="getPercentage(metrics.equipmentOrdersCreated)" status="warning" />
              <span class="order-count">{{ metrics.equipmentOrdersCreated || 0 }}</span>
            </div>
            <div class="order-stat-item">
              <span class="order-label">视频购买</span>
              <n-progress type="line" :percentage="getPercentage(metrics.videoPurchasesCreated)" status="error" />
              <span class="order-count">{{ metrics.videoPurchasesCreated || 0 }}</span>
            </div>
          </div>
        </n-card>
      </div>

      <div class="quick-actions">
        <n-card title="快捷操作">
          <n-space>
            <n-button @click="$router.push('/admin/users')">用户管理</n-button>
            <n-button @click="$router.push('/admin/coaches')">教练审核</n-button>
            <n-button @click="$router.push('/admin/topups')">充值审核</n-button>
            <n-button @click="$router.push('/admin/complaints')">投诉管理</n-button>
            <n-button @click="$router.push('/admin/orders')">订单管理</n-button>
          </n-space>
        </n-card>
      </div>
    </n-spin>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { CartOutline, CashOutline, PeopleOutline, AlertCircleOutline } from '@vicons/ionicons5'
import { getMetrics } from '@/api/admin'

const loading = ref(false)
const metrics = ref({})

const totalOrders = computed(() =>
  (metrics.value.venueBookingsCreated || 0) +
  (metrics.value.courseBookingsCreated || 0) +
  (metrics.value.equipmentOrdersCreated || 0) +
  (metrics.value.videoPurchasesCreated || 0)
)

const netPayAmount = computed(() => {
  const pay = metrics.value.totalPayAmount || 0
  const refund = metrics.value.totalRefundAmount || 0
  const v = pay - refund
  return v > 0 ? v : 0
})

const formatAmount = (value) => {
  const v = Number(value ?? 0)
  if (Number.isNaN(v)) return 0
  if (Number.isInteger(v)) return v
  return v.toFixed(2)
}

const getPercentage = (value) => {
  const total = totalOrders.value
  if (total === 0) return 0
  return Math.round((value || 0) / total * 100)
}

const formatDate = (d) => {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

const fetchMetrics = async () => {
  loading.value = true
  try {
    const end = new Date()
    const start = new Date()
    start.setDate(end.getDate() - 6)
    const res = await getMetrics({
      startDate: formatDate(start),
      endDate: formatDate(end)
    })
    metrics.value = res.data || {}
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchMetrics()
})
</script>

<style scoped>
.admin-dashboard {
  padding: 20px;
}
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 20px;
}
.stat-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.stat-card :deep(.n-card__content) {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.stat-content {
  display: flex;
  flex-direction: column;
}
.stat-label {
  font-size: 14px;
  color: #666;
}
.stat-value {
  font-size: 28px;
  font-weight: 600;
}
.charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 20px;
}
.chart-card {
  min-height: 300px;
}
.chart-placeholder {
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  height: 200px;
  padding: 20px 0;
}
.bar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.bar {
  width: 40px;
  background: linear-gradient(180deg, #18a058, #36ad6a);
  border-radius: 4px 4px 0 0;
  min-height: 10px;
}
.bar-label {
  margin-top: 8px;
  font-size: 12px;
  color: #666;
}
.order-stats {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 20px 0;
}
.order-stat-item {
  display: flex;
  align-items: center;
  gap: 16px;
}
.order-label {
  width: 80px;
  font-size: 14px;
}
.order-stat-item .n-progress {
  flex: 1;
}
.order-count {
  width: 50px;
  text-align: right;
  font-weight: 500;
}
</style>
