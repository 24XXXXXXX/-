<template>
  <div class="course-booking-detail-page">
    <n-spin :show="loading">
      <n-card v-if="detail" title="预约详情">
        <div class="header-row">
          <div class="booking-no">预约号: {{ detail.bookingNo }}</div>
          <n-tag :type="getStatusType(detail.status)">{{ getStatusText(detail.status) }}</n-tag>
        </div>

        <div class="content-row">
          <img :src="detail.courseCoverUrl || '/placeholder.svg'" class="course-img" />
          <div class="info">
            <h3 class="title">{{ detail.courseTitle }}</h3>
            <div class="meta">教练: {{ detail.coachUsername || '-' }}</div>
            <div class="meta">时间: {{ formatDate(detail.startTime) }} {{ formatTime(detail.startTime) }}-{{ formatTime(detail.endTime) }}</div>
            <div class="meta" v-if="detail.venueName">场地: {{ detail.venueName }}</div>
            <div class="amount">金额: ¥{{ detail.amount }}</div>
            <div class="verify" v-if="detail.status === 'PAID' && detail.verificationCode">
              核销码: <n-tag type="success">{{ detail.verificationCode }}</n-tag>
            </div>
          </div>
        </div>

        <n-divider />

        <n-space>
          <n-button v-if="detail.status === 'ACCEPTED'" type="primary" @click="handlePay">
            立即支付
          </n-button>
          <n-button v-if="canCancel(detail.status)" @click="handleCancel">
            取消预约
          </n-button>
          <n-button v-if="detail.status === 'USED' && !detail.reviewed" type="primary" @click="openReviewModal">
            评价
          </n-button>
          <n-tag v-if="detail.reviewed" type="success" size="small">已评价</n-tag>
        </n-space>
      </n-card>
      <n-empty v-else description="预约不存在或无权限查看" />
    </n-spin>

    <n-modal v-model:show="showReviewModal" preset="card" title="评价课程" style="width: 500px">
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
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { getMyCourseBookingDetail, payCourseBooking, cancelCourseBooking, submitCourseReview } from '@/api/course'

// 用户端「课程预约（报名）详情」页面
//
// 页面职责：
// - 展示单笔报名单的核心信息：课程标题/教练/时间/场地/金额/核销码（若已支付）
// - 根据状态机展示可操作按钮：
//   - ACCEPTED：允许立即支付（后端校验状态是否允许支付 + 扣费/流水）
//   - PENDING_COACH/ACCEPTED/PAID：允许取消（后端校验是否可取消 + 是否退款）
//   - USED 且未评价：允许提交评价（reviewed=false）
//
// 数据流：
// onMounted -> fetchDetail -> getMyCourseBookingDetail(id) -> detail -> template 渲染
//
// 权限边界：
// - 详情/支付/取消/评价都需要登录
// - “只能查看/操作自己的报名单”的资源级校验由后端保证

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const detail = ref(null)

const showReviewModal = ref(false)
const submittingReview = ref(false)
const reviewFormRef = ref(null)
const reviewForm = reactive({
  rating: 5,
  content: ''
})

const reviewRules = {
  rating: { required: true, type: 'number', min: 1, message: '请选择评分' },
  content: { required: true, message: '请输入评价内容' }
}

const statusMap = {
  PENDING_COACH: { text: '待确认', type: 'warning' },
  ACCEPTED: { text: '待支付', type: 'warning' },
  PAID: { text: '待上课', type: 'info' },
  USED: { text: '已完成', type: 'success' },
  CANCELED: { text: '已取消', type: 'default' },
  REFUNDED: { text: '已退款', type: 'default' },
  REJECTED: { text: '已拒绝', type: 'error' }
}

// statusMap 是“后端状态码 -> 前端文案/Tag 样式”的映射。
// 注意：这只影响 UI 展示，真正状态机推进与校验在后端。

const getStatusText = (status) => statusMap[status]?.text || status
const getStatusType = (status) => statusMap[status]?.type || 'default'

const canCancel = (status) => {
  // 前端展示层的取消条件：
  // - 待确认/待支付/待上课：允许取消
  // - 其他状态（已完成/已取消/已拒绝等）不展示取消按钮
  // 最终是否可取消仍由后端决定（例如开课前 N 小时可取消）。
  return status === 'PENDING_COACH' || status === 'ACCEPTED' || status === 'PAID'
}

const formatDate = (date) => dayjs(date).format('YYYY-MM-DD')
const formatTime = (date) => dayjs(date).format('HH:mm')

const fetchDetail = async () => {
  // 拉取报名单详情：
  // - 成功：detail 为对象
  // - 失败：detail=null，模板会展示“预约不存在或无权限查看”
  loading.value = true
  try {
    const res = await getMyCourseBookingDetail(route.params.id)
    detail.value = res.data
  } catch (e) {
    detail.value = null
  } finally {
    loading.value = false
  }
}

const handlePay = async () => {
  // 立即支付：资金敏感操作。
  // - 后端会校验当前状态是否允许支付，并完成扣费/流水等事务
  // - 成功后刷新详情，让 status/核销码/按钮状态即时更新
  if (!detail.value?.id) return
  try {
    await payCourseBooking(detail.value.id)
    window.$message?.success('支付成功')
    await fetchDetail()
  } catch (e) {
    window.$message?.error(e?.response?.data?.message || '支付失败')
  }
}

const handleCancel = async () => {
  // 取消预约：
  // - 可能触发退款或取消规则（以后端实现为准）
  // - 成功后刷新详情，让状态立刻变为 CANCELED/REFUNDED
  if (!detail.value?.id) return
  try {
    await cancelCourseBooking(detail.value.id)
    window.$message?.success('已取消预约')
    await fetchDetail()
  } catch (e) {
    window.$message?.error(e?.response?.data?.message || '取消失败')
  }
}

const openReviewModal = () => {
  // 打开评价弹窗：
  // - 入口按钮在模板中由 detail.status === 'USED' && !detail.reviewed 控制
  if (!detail.value?.id) return
  reviewForm.rating = 5
  reviewForm.content = ''
  showReviewModal.value = true
}

const submitReviewForm = async () => {
  // 提交评价：
  // - 传 bookingId（报名单 id）让后端关联到具体订单
  // - 成功后刷新详情，让 reviewed=true，从而在 UI 上显示“已评价”
  if (!detail.value?.id) return
  try {
    await reviewFormRef.value?.validate()
    submittingReview.value = true
    await submitCourseReview({
      bookingId: detail.value.id,
      rating: reviewForm.rating,
      content: reviewForm.content
    })
    window.$message?.success('评价成功')
    showReviewModal.value = false
    fetchDetail()
  } catch (e) {
    if (e?.message) window.$message?.error(e.message)
  } finally {
    submittingReview.value = false
  }
}

onMounted(() => {
  // 进入详情页必须携带 id：
  // - 若缺失则回到列表页
  // - 有 id 则拉取详情
  if (!route.params.id) {
    router.replace('/user/course-bookings')
    return
  }
  fetchDetail()
})
</script>

<style scoped>
.course-booking-detail-page {
  padding: 20px;
}
.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.booking-no {
  font-weight: 500;
}
.content-row {
  display: flex;
  gap: 16px;
}
.course-img {
  width: 160px;
  height: 100px;
  object-fit: cover;
  border-radius: 4px;
}
.info {
  flex: 1;
}
.title {
  margin: 0 0 8px;
  font-size: 16px;
}
.meta {
  margin: 0 0 6px;
  font-size: 13px;
  color: #666;
}
.amount {
  margin-top: 10px;
  font-weight: 600;
}
.verify {
  margin-top: 10px;
}
</style>
