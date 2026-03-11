<template>
  <div class="bookings-page">
    <n-card title="场地预约记录">
      <!-- 筛选 -->
      <template #header-extra>
        <n-select
          v-model:value="filters.status"
          placeholder="预约状态"
          clearable
          :options="statusOptions"
          style="width: 150px"
          @update:value="handleFilter"
          v-if="false"
        />
      </template>

      <n-tabs v-model:value="activeTab" @update:value="handleTabChange">
        <n-tab-pane name="all" tab="全部" />
        <n-tab-pane name="booked" tab="已预约" />
        <n-tab-pane name="completed" tab="已完成" />
        <n-tab-pane name="cancelled" tab="已取消" />
      </n-tabs>

      <!-- 预约列表 -->
      <n-spin :show="loading">
        <div v-if="bookings.length" class="booking-list">
          <div v-for="booking in bookings" :key="booking.id" class="booking-card">
            <div class="booking-header">
              <span class="booking-no">预约单号：{{ booking.bookingNo }}</span>
              <n-tag :type="getStatusType(booking.status)" size="small">
                {{ getStatusText(booking.status) }}
              </n-tag>
            </div>
            
            <div class="booking-content">
              <div class="venue-info">
                <h3>{{ booking.venueName }}</h3>
                <p>{{ booking.venueArea }} · {{ booking.venueAddress }}</p>
              </div>
              
              <div class="booking-meta">
                <div class="meta-item">
                  <span class="label">预约时段</span>
                  <span class="value">{{ formatDateTime(booking.startTime) }} - {{ formatTime(booking.endTime) }}</span>
                </div>
                <div class="meta-item">
                  <span class="label">预约金额</span>
                  <span class="value price">¥{{ booking.amount }}</span>
                </div>
                <div class="meta-item">
                  <span class="label">预约时间</span>
                  <span class="value">{{ formatDateTime(booking.createdAt) }}</span>
                </div>
              </div>

            </div>

            <div class="booking-footer">
              <div class="verify-code" v-if="booking.status === 'PAID' && booking.verificationCode">
                核销码: <n-tag type="success">{{ booking.verificationCode }}</n-tag>
              </div>
              <n-space>
                <n-button
                  v-if="booking.status === 'PAID' && canCancel(booking)"
                  type="error"
                  ghost
                  size="small"
                  :loading="canceling === booking.id"
                  @click="handleCancel(booking)"
                >
                  取消预约
                </n-button>
                <n-button size="small" @click="router.push(`/venues/${booking.venueId}`)">
                  查看场地
                </n-button>
                <n-button
                  v-if="booking.status === 'USED' && !booking.reviewed"
                  type="primary"
                  size="small"
                  @click="openReviewModal(booking)"
                >
                  评价
                </n-button>
                <n-tag v-if="booking.reviewed" type="success" size="small">已评价</n-tag>
              </n-space>
            </div>
          </div>
        </div>

        <n-empty v-else-if="!loading" description="暂无预约记录" />
      </n-spin>

      <!-- 分页 -->
      <div v-if="total > 0" class="pagination-wrap">
        <n-pagination
          v-model:page="pagination.page"
          :page-size="pagination.size"
          :item-count="total"
          @update:page="fetchBookings"
        />
      </div>
    </n-card>

    <n-modal v-model:show="showReviewModal" preset="card" title="评价场地" style="width: 500px">
      <n-form ref="reviewFormRef" :model="reviewForm" :rules="reviewRules">
        <n-form-item label="评分" path="rating">
          <n-rate v-model:value="reviewForm.rating" />
        </n-form-item>
        <n-form-item label="评价内容" path="content">
          <n-input v-model:value="reviewForm.content" type="textarea" placeholder="请输入评价内容" :rows="4" />
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
import { useRouter } from 'vue-router'
import { useMessage, useDialog } from 'naive-ui'
import { getMyBookings, cancelBooking, submitVenueReview } from '@/api/venue'
import dayjs from 'dayjs'

// 用户端「我的场地预约」页面
//
// 页面职责：
// - 查询并展示当前登录用户的预约单列表（分页）
// - 支持按 tab（全部/已预约/已完成/已取消）切换筛选口径
// - 对于可取消的预约提供取消入口（会触发后端状态机与退款规则）
// - 对于已完成但未评价的预约提供“评价”入口（提交后刷新列表让 reviewed 变为 true）
//
// 数据流（核心思路）：
// onMounted -> fetchBookings -> getMyBookings(params) -> bookings/total -> template 渲染
//
// 权限边界说明：
// - 该页面依赖登录态（token）；真正的“只能看自己的预约/只能取消自己的预约”等资源级校验由后端保证

const router = useRouter()
const message = useMessage()
const dialog = useDialog()

const loading = ref(false)
const bookings = ref([])
const total = ref(0)
const canceling = ref(null)

const showReviewModal = ref(false)
const submittingReview = ref(false)
const reviewFormRef = ref(null)
const currentBooking = ref(null)
const reviewForm = reactive({
  rating: 5,
  content: ''
})

const reviewRules = {
  rating: { required: true, type: 'number', min: 1, message: '请选择评分' },
  content: { required: true, message: '请输入评价内容' }
}

const activeTab = ref('all')

const filters = ref({
  status: null
})

const pagination = ref({
  page: 1,
  size: 10
})

const statusOptions = [
  { label: '已支付', value: 'PAID' },
  { label: '已使用', value: 'USED' },
  { label: '已取消', value: 'CANCELED' },
  { label: '已退款', value: 'REFUNDED' }
]

// tab -> status 的映射：
// - booked/completed 等 tab 实际上就是对后端 status 的筛选口径
// - cancelled 同时覆盖 CANCELED 与 REFUNDED（逗号分隔多值的写法由后端解析；见后端实现）
const tabToStatus = {
  booked: 'PAID',
  completed: 'USED',
  cancelled: 'CANCELED,REFUNDED'
}

const fetchBookings = async () => {
  // 拉取列表：
  // - 参数使用 page/size（与部分组件习惯的 pageSize 做了兼容，http.js 也会进一步兼容）
  // - 当 activeTab != all 时通过 status 传给后端做筛选
  loading.value = true
  try {
    const params = {
      page: pagination.value.page,
      size: pagination.value.size
    }
    if (activeTab.value !== 'all') {
      params.status = tabToStatus[activeTab.value]
    }
    const res = await getMyBookings(params)
    // 后端分页返回字段兼容：
    // - 有的接口返回 {items,total}
    // - 有的接口返回 {records,total}
    // 这里两者都兼容，避免改动后端返回结构时前端页面崩溃。
    const data = res.data || {}
    bookings.value = data.items || data.records || []
    total.value = data.total ?? bookings.value.length
  } catch (e) {
    console.error('Failed to fetch bookings:', e)
  } finally {
    loading.value = false
  }
}

const handleFilter = () => {
  pagination.value.page = 1
  fetchBookings()
}

const handleTabChange = () => {
  pagination.value.page = 1
  fetchBookings()
}

const canCancel = (booking) => {
  // 取消按钮的前端“体验层”限制：仅当预约开始时间在当前时间之后才展示。
  // 最终能否取消，仍以后端的业务规则为准（例如可能存在“提前 N 小时才能取消”的限制）。
  return dayjs(booking.startTime).isAfter(dayjs())
}

const handleCancel = (booking) => {
  // 取消预约：
  // - 先弹确认框（避免误触）
  // - 调用 cancelBooking 后刷新列表
  // - 若后端返回业务错误（例如已到开始时间/不可取消），展示 message
  dialog.warning({
    title: '确认取消',
    content: '确定要取消这个预约吗？取消后将按规则退款。',
    positiveText: '确定取消',
    negativeText: '再想想',
    onPositiveClick: async () => {
      canceling.value = booking.id
      try {
        await cancelBooking(booking.id)
        message.success('取消成功，退款将返还到钱包')
        fetchBookings()
      } catch (e) {
        const msg = e?.response?.data?.message || '取消失败'
        message.error(msg)
      } finally {
        canceling.value = null
      }
    }
  })
}

const openReviewModal = (booking) => {
  // 打开评价弹窗：
  // - 仅在 booking.status === 'USED' 且 reviewed=false 时展示入口
  // - currentBooking 用于提交评价时携带 bookingId
  if (!booking?.id) return
  currentBooking.value = booking
  reviewForm.rating = 5
  reviewForm.content = ''
  showReviewModal.value = true
}

const submitReviewForm = async () => {
  // 提交评价：
  // - 表单校验通过后调用 submitVenueReview
  // - 成功后关闭弹窗并刷新列表，让“已评价”标签立即生效
  if (!currentBooking.value?.id) return
  try {
    await reviewFormRef.value?.validate()
    submittingReview.value = true
    await submitVenueReview({
      bookingId: currentBooking.value.id,
      rating: reviewForm.rating,
      content: reviewForm.content
    })
    message.success('评价成功')
    showReviewModal.value = false
    fetchBookings()
  } catch (e) {
    const msg = e?.response?.data?.message || e?.message || '评价失败'
    message.error(msg)
  } finally {
    submittingReview.value = false
  }
}

const formatDateTime = (datetime) => {
  return dayjs(datetime).format('YYYY-MM-DD HH:mm')
}

const formatTime = (datetime) => {
  return dayjs(datetime).format('HH:mm')
}

const getStatusType = (status) => {
  const map = {
    CREATED: 'default',
    PAID: 'success',
    CANCELED: 'warning',
    REFUNDED: 'info',
    USED: 'primary'
  }
  return map[status] || 'default'
}

const getStatusText = (status) => {
  const map = {
    CREATED: '待支付',
    PAID: '已预约',
    CANCELED: '已取消',
    REFUNDED: '已取消',
    USED: '已完成'
  }
  return map[status] || status
}

onMounted(() => {
  fetchBookings()
})
</script>

<style scoped>
.bookings-page {
  padding: 20px;
}

.booking-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 20px;
}

.booking-card {
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 16px;
  transition: box-shadow 0.2s;
}

.booking-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.booking-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.booking-no {
  font-size: 13px;
  color: #999;
}

.booking-content {
  margin-bottom: 16px;
}

.venue-info h3 {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin: 0 0 4px;
}

.venue-info p {
  font-size: 13px;
  color: #666;
  margin: 0 0 16px;
}

.booking-meta {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.meta-item .label {
  font-size: 12px;
  color: #999;
}

.meta-item .value {
  font-size: 14px;
  color: #333;
}

.meta-item .value.price {
  font-size: 16px;
  font-weight: 600;
  color: #f5222d;
}

.verification-code {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  background: #fffbe6;
  border-radius: 8px;
}

.verification-code .label {
  font-size: 14px;
  color: #666;
}

.booking-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.booking-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.verify-code {
  font-size: 13px;
}

.pagination-wrap {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}

@media (max-width: 768px) {
  .booking-meta {
    grid-template-columns: 1fr;
  }
}
</style>
