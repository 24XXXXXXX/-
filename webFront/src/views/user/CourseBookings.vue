<template>
  <div class="course-bookings-page">
    <n-card title="我的课程预约">
      <n-tabs v-model:value="activeTab" @update:value="handleTabChange">
        <n-tab-pane name="all" tab="全部" />
        <n-tab-pane name="pending" tab="待确认" />
        <n-tab-pane name="toPay" tab="待支付" />
        <n-tab-pane name="paid" tab="待上课" />
        <n-tab-pane name="completed" tab="已完成" />
        <n-tab-pane name="cancelled" tab="已取消" />
      </n-tabs>

      <n-spin :show="loading">
        <div v-if="bookings.length" class="booking-list">
          <div v-for="booking in bookings" :key="booking.id" class="booking-card">
            <div class="booking-header">
              <span class="booking-no">预约号: {{ booking.bookingNo }}</span>
              <n-tag :type="getStatusType(booking.status)">{{ getStatusText(booking.status) }}</n-tag>
            </div>
            <div class="booking-content">
              <img :src="booking.courseCoverUrl || '/placeholder.svg'" class="course-img" />
              <div class="booking-info">
                <h4>{{ booking.courseTitle }}</h4>
                <p class="coach">教练: {{ booking.coachUsername || '-' }}</p>
                <p class="time">
                  <n-icon><TimeOutline /></n-icon>
                  {{ formatDate(booking.startTime) }} {{ formatTime(booking.startTime) }}-{{ formatTime(booking.endTime) }}
                </p>
                <p v-if="booking.venueName" class="venue">
                  <n-icon><LocationOutline /></n-icon>
                  {{ booking.venueName }}
                </p>
              </div>
              <div class="booking-price">
                <span class="price">¥{{ booking.amount }}</span>
              </div>
            </div>
            <div class="booking-footer">
              <div class="verify-code" v-if="booking.status === 'PAID' && booking.verificationCode">
                核销码: <n-tag type="success">{{ booking.verificationCode }}</n-tag>
              </div>
              <n-space>
                <n-button @click="goDetail(booking.id)">查看详情</n-button>
                <n-button v-if="booking.status === 'ACCEPTED'" type="primary" @click="handlePay(booking.id)">
                  立即支付
                </n-button>
                <n-button v-if="canCancel(booking.status)" @click="handleCancel(booking.id)">
                  取消预约
                </n-button>
                <n-button
                  v-if="booking.status === 'USED' && !booking.reviewed"
                  type="primary"
                  @click="openReviewModal(booking)"
                >
                  评价
                </n-button>
                <n-tag v-if="booking.reviewed" type="success" size="small">已评价</n-tag>
              </n-space>
            </div>
          </div>
        </div>
        <n-empty v-else description="暂无预约记录" />
      </n-spin>

      <div class="pagination-wrap" v-if="total > 0">
        <n-pagination
          v-model:page="pagination.page"
          :page-size="pagination.pageSize"
          :item-count="total"
          @update:page="fetchBookings"
        />
      </div>
    </n-card>

    <!-- 评价弹窗 -->
    <n-modal v-model:show="showReviewModal" preset="card" title="评价课程" style="width: 500px">
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
import { useRouter } from 'vue-router'
import { TimeOutline, LocationOutline } from '@vicons/ionicons5'
import { getMyCourseBookings, payCourseBooking, cancelCourseBooking, submitCourseReview } from '@/api/course'
import dayjs from 'dayjs'

// 用户端「我的课程预约」列表页
//
// 页面职责：
// - 分页展示当前用户的课程报名单列表
// - 通过 tabs 做状态筛选（待确认/待支付/待上课/已完成/已取消等）
// - 提供常用动作：
//   - 支付（仅 ACCEPTED）
//   - 取消（PENDING_COACH/ACCEPTED/PAID，具体规则仍以后端为准）
//   - 评价（USED 且 reviewed=false）
//   - 查看详情（进入 CourseBookingDetail.vue）
//
// 数据流：
// onMounted -> fetchBookings -> getMyCourseBookings(params) -> bookings/total -> template 渲染
//
// 说明：
// - tabToStatus 使用逗号分隔多状态（例如取消 tab 覆盖 CANCELED/REFUNDED/REJECTED），由后端解析
// - 支付/取消属于敏感操作：前端只触发，最终状态机与资金处理由后端保证

const router = useRouter()

const loading = ref(false)
const bookings = ref([])
const total = ref(0)
const activeTab = ref('all')

const pagination = reactive({
  page: 1,
  pageSize: 10
})

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

const formatDate = (date) => dayjs(date).format('YYYY-MM-DD')
const formatTime = (date) => dayjs(date).format('HH:mm')

const statusMap = {
  PENDING_COACH: { text: '待确认', type: 'warning' },
  ACCEPTED: { text: '待支付', type: 'warning' },
  PAID: { text: '待上课', type: 'info' },
  USED: { text: '已完成', type: 'success' },
  CANCELED: { text: '已取消', type: 'default' },
  REFUNDED: { text: '已退款', type: 'default' },
  REJECTED: { text: '已拒绝', type: 'error' }
}

const tabToStatus = {
  pending: 'PENDING_COACH',
  toPay: 'ACCEPTED',
  paid: 'PAID',
  completed: 'USED',
  cancelled: 'CANCELED,REFUNDED,REJECTED'
}

// tabToStatus：把 UI 的 tab name 映射为后端筛选条件。
// - cancelled 是“聚合口径”，把所有不可继续履约的状态合并展示。

const getStatusText = (status) => statusMap[status]?.text || status
const getStatusType = (status) => statusMap[status]?.type || 'default'

const canCancel = (status) => {
  return status === 'PENDING_COACH' || status === 'ACCEPTED' || status === 'PAID'
}

const fetchBookings = async () => {
  // 拉取列表：
  // - 分页参数使用 page/size
  // - activeTab != all 时追加 status 作为筛选条件
  // - 返回结构兼容 items/list/直接数组（不同接口实现可能不同）
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.pageSize
    }
    if (activeTab.value !== 'all') {
      params.status = tabToStatus[activeTab.value]
    }
    const res = await getMyCourseBookings(params)
    bookings.value = res.data?.items || res.data?.list || res.data || []
    total.value = res.data?.total ?? bookings.value.length
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const handleTabChange = () => {
  pagination.page = 1
  fetchBookings()
}

const handlePay = async (id) => {
  // 支付报名单：
  // - 仅 ACCEPTED 状态展示入口（见 template）
  // - 成功后刷新列表，让 status/核销码等信息更新
  try {
    const res = await payCourseBooking(id)
    // 检查响应体中的业务错误码
    if (res.data?.code && res.data.code !== 200) {
      window.$message?.error(res.data?.msg || res.data?.message || '支付失败')
      return
    }
    window.$message?.success('支付成功')
    fetchBookings()
  } catch (e) {
    window.$message?.error(e?.response?.data?.msg || e?.response?.data?.message || '支付失败')
  }
}

const handleCancel = async (id) => {
  // 取消报名单：
  // - 前端按钮由 canCancel 控制
  // - 后端可能会根据开课时间决定是否允许取消/是否退款
  try {
    await cancelCourseBooking(id)
    window.$message?.success('已取消预约')
    fetchBookings()
  } catch (e) {
    window.$message?.error('取消失败')
  }
}

const goDetail = (id) => {
  // 进入详情页（CourseBookingDetail.vue）
  if (!id) return
  router.push(`/user/course-bookings/${id}`)
}

const openReviewModal = (booking) => {
  // 打开评价弹窗：
  // - 入口按钮在模板中由 booking.status === 'USED' && !booking.reviewed 控制
  currentBooking.value = booking
  reviewForm.rating = 5
  reviewForm.content = ''
  showReviewModal.value = true
}

const submitReviewForm = async () => {
  // 提交评价：成功后刷新列表以更新 reviewed 标记。
  try {
    await reviewFormRef.value?.validate()
    submittingReview.value = true
    await submitCourseReview({
      bookingId: currentBooking.value.id,
      rating: reviewForm.rating,
      content: reviewForm.content
    })
    window.$message?.success('评价成功')
    showReviewModal.value = false
    fetchBookings()
  } catch (e) {
    if (e?.message) window.$message?.error(e.message)
  } finally {
    submittingReview.value = false
  }
}

onMounted(() => {
  fetchBookings()
})
</script>

<style scoped>
.course-bookings-page {
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
  overflow: hidden;
}
.booking-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #fafafa;
}
.booking-no {
  font-weight: 500;
}
.booking-content {
  display: flex;
  gap: 16px;
  padding: 16px;
}
.course-img {
  width: 120px;
  height: 80px;
  object-fit: cover;
  border-radius: 4px;
}
.booking-info {
  flex: 1;
}
.booking-info h4 {
  margin: 0 0 8px;
  font-size: 15px;
}
.booking-info p {
  margin: 0 0 4px;
  font-size: 13px;
  color: #666;
  display: flex;
  align-items: center;
  gap: 4px;
}
.booking-price {
  display: flex;
  align-items: center;
}
.price {
  font-size: 18px;
  color: #f5222d;
  font-weight: 600;
}
.booking-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-top: 1px solid #eee;
  background: #fafafa;
}
.verify-code {
  font-size: 13px;
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
