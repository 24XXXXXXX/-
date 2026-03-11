<template>
  <div class="course-detail">
    <div class="back-btn-wrap">
      <n-button text @click="router.back()">
        <template #icon><n-icon><ArrowBackOutline /></n-icon></template>
        返回
      </n-button>
    </div>
    <n-spin :show="loading">
      <n-card v-if="course">
        <div class="detail-content">
          <div class="left">
            <img :src="course.coverUrl || '/placeholder.svg'" :alt="course.title" class="main-img" />
          </div>
          <div class="right">
            <h1 class="title">{{ course.title }}</h1>
            <div class="course-id">课程ID：{{ course.id ?? route.params.id }}</div>
            <div class="coach-info">
              <n-avatar v-if="false" size="large" round />
              <div class="coach-text">
                <span class="coach-name">{{ course.coachUsername }}</span>
                <span class="coach-title">{{ course.coachTitle || '认证教练' }}</span>
              </div>
            </div>
            <p class="desc">{{ course.outline }}</p>
            <div class="price-row">
              <span class="price">¥{{ course.price }}</span>
              <n-rate :value="course.rating || 5" readonly />
            </div>
            <n-divider />
            <div class="action-row">
              <n-button :type="isFavorited ? 'warning' : 'default'" @click="toggleFavorite">
                <template #icon><n-icon><component :is="isFavorited ? Heart : HeartOutline" /></n-icon></template>
                {{ isFavorited ? '已收藏' : '收藏' }}
              </n-button>
              <n-button type="info" @click="handleConsult">
                咨询教练
              </n-button>
            </div>
          </div>
        </div>
      </n-card>

      <!-- 课程场次 -->
      <n-card title="课程场次" class="sessions-card">
        <div v-if="displaySessions.length" class="sessions-list">
          <div v-for="session in displaySessions" :key="session.id" class="session-item">
            <div class="session-info">
              <span class="session-date">{{ formatDate(session.startTime) }}</span>
              <span class="session-time">{{ formatTime(session.startTime) }} - {{ formatTime(session.endTime) }}</span>
              <span class="session-venue">{{ getSessionStatusText(session) }}</span>
            </div>
            <div class="session-right">
              <span :class="['capacity', { full: session.enrolledCount >= session.capacity }]">
                {{ session.enrolledCount }}/{{ session.capacity }}人
              </span>
              <n-button
                type="primary"
                size="small"
                :disabled="!canBook(session)"
                @click="handleBook(session)"
              >
                {{ getSessionActionText(session) }}
              </n-button>
            </div>
          </div>
        </div>
        <n-empty v-else description="暂无可预约场次" />
      </n-card>

      <!-- 评价区域 -->
      <n-card title="学员评价" class="reviews-card">
        <div v-if="reviews.length">
          <div v-for="review in reviews" :key="review.id" class="review-item">
            <div class="review-header">
              <n-avatar :src="review.userAvatar" size="small" />
              <span class="username">{{ review.username }}</span>
              <n-rate :value="review.rating" readonly size="small" />
              <span class="time">{{ formatDate(review.createdAt) }}</span>
            </div>
            <p class="review-content">{{ review.content }}</p>
          </div>
          <div class="pagination-wrap" v-if="reviewTotal > 5">
            <n-pagination
              v-model:page="reviewPage"
              :page-size="5"
              :item-count="reviewTotal"
              @update:page="fetchReviews"
            />
          </div>
        </div>
        <n-empty v-else description="暂无评价" />
      </n-card>
    </n-spin>

    <!-- 预约确认弹窗 -->
    <n-modal v-model:show="showBookModal" preset="card" title="确认预约" style="width: 400px">
      <div v-if="selectedSession">
        <n-descriptions :column="1" label-placement="left">
          <n-descriptions-item label="课程">{{ course?.title }}</n-descriptions-item>
          <n-descriptions-item label="教练">{{ course?.coachUsername }}</n-descriptions-item>
          <n-descriptions-item label="时间">{{ formatDate(selectedSession.startTime) }} {{ formatTime(selectedSession.startTime) }}-{{ formatTime(selectedSession.endTime) }}</n-descriptions-item>
          <n-descriptions-item label="状态">{{ selectedSession.status === 'OPEN' ? '可预约' : '已关闭' }}</n-descriptions-item>
          <n-descriptions-item label="费用">¥{{ course?.price }}</n-descriptions-item>
        </n-descriptions>
      </div>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showBookModal = false">取消</n-button>
          <n-button type="primary" :loading="booking" @click="confirmBook">确认预约</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal v-model:show="showConsultModal" preset="card" title="咨询教练" style="width: 420px">
      <n-input v-model:value="consultContent" type="textarea" placeholder="请输入咨询内容" :rows="4" />
      <template #footer>
        <n-space justify="end">
          <n-button @click="showConsultModal = false">取消</n-button>
          <n-button type="primary" :loading="consulting" :disabled="!consultContent.trim()" @click="confirmConsult">发送</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { HeartOutline, Heart, ArrowBackOutline } from '@vicons/ionicons5'
import { getCourseDetail, getCourseSessions, getCourseReviews, createCourseBooking, createConsultation, getMyCourseBookings } from '@/api/course'
import { addFavorite, removeFavorite, checkFavorite } from '@/api/favorite'
import { useAuthStore } from '@/stores/auth'
import dayjs from 'dayjs'

// 公共页面「课程详情」（用户端核心链路页面）
//
// 页面职责：
// - 展示课程基础信息（封面/标题/教练信息/简介/价格/评分）
// - 展示课程场次（session）列表，并允许用户对可预约场次发起预约
// - 展示学员评价（分页）
// - 维护收藏状态（登录用户维度）
// - 提供“咨询教练”入口：发送咨询后跳转到我的咨询页
//
// 数据流（高层）：
// onMounted 并行触发：
// - fetchDetail：getCourseDetail(id) -> course
// - fetchSessions：getCourseSessions(id) -> sessions
//   - 若已登录：同时 getMyCourseBookings 拉取“我的预约”，派生 myBookedSessionIds
// - fetchReviews：getCourseReviews(id, {page,size}) -> reviews/reviewTotal
// - checkFavoriteStatus：checkFavorite({type,targetId}) -> isFavorited
//
// 场次可预约判定（核心 UI 逻辑）：
// - 必须 session.status === 'OPEN'
// - 不能是“我已预约”（myBookedSessionIds）
// - 不能满员（enrolledCount >= capacity）
// - 不能已开始/已过期（基于 startTime/endTime 与当前时间比较）
//
// 资金/并发敏感点：
// - createCourseBooking 属于支付/扣费链路的一部分（或会生成待支付订单），最终校验与幂等必须由后端保证
// - 前端仅做按钮禁用与提示，避免重复点击造成体验问题

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const course = ref(null)
const sessions = ref([])
const isFavorited = ref(false)
const reviews = ref([])
const reviewTotal = ref(0)
const reviewPage = ref(1)

const showBookModal = ref(false)
const selectedSession = ref(null)
const booking = ref(false)

const myBookedSessionIds = ref(new Set())

const showConsultModal = ref(false)
const consultContent = ref('')
const consulting = ref(false)

const formatDate = (date) => dayjs(date).format('YYYY-MM-DD')
const formatTime = (date) => dayjs(date).format('HH:mm')

const isBookedByMe = (session) => {
  // 判断某场次是否已被当前用户预约：
  // - myBookedSessionIds 是由 getMyCourseBookings 派生的集合
  const sid = session?.id
  return sid != null && myBookedSessionIds.value?.has?.(sid)
}

const isExpired = (session) => {
  // 是否已过期：当前时间在 endTime 之后。
  const end = session?.endTime ? dayjs(session.endTime) : null
  if (end && end.isValid()) {
    return end.isBefore(dayjs())
  }
  const start = session?.startTime ? dayjs(session.startTime) : null
  return start && start.isValid() ? start.isBefore(dayjs()) : false
}

const isStarted = (session) => {
  // 是否已开始：当前时间在 startTime 之后。
  const start = session?.startTime ? dayjs(session.startTime) : null
  return start && start.isValid() ? start.isBefore(dayjs()) : false
}

const isFull = (session) => {
  // 是否满员：enrolledCount >= capacity。
  const enrolled = session?.enrolledCount == null ? 0 : Number(session.enrolledCount)
  const cap = session?.capacity == null ? 0 : Number(session.capacity)
  return cap > 0 && enrolled >= cap
}

const canBook = (session) => {
  // canBook：决定“预约”按钮是否可用。
  if (!session || session.status !== 'OPEN') return false
  if (isBookedByMe(session)) return false
  if (isFull(session)) return false
  if (isStarted(session)) return false
  if (isExpired(session)) return false
  return true
}

const getSessionStatusText = (session) => {
  // 场次状态展示文案：优先显示“已预约”，否则根据可预约条件展示。
  // 已预约优先显示
  if (isBookedByMe(session)) return '已预约'
  // 状态不是OPEN
  if (!session || session.status !== 'OPEN') return '不可预约'
  // 已过期或已开始（在预约时间段之后）
  if (isExpired(session) || isStarted(session)) return '不可预约'
  // 人数已满
  if (isFull(session)) return '不可预约'
  // 可预约
  return '可预约'
}

const getSessionActionText = (session) => {
  if (isBookedByMe(session)) return '已预约'
  return canBook(session) ? '预约' : '不可预约'
}

const displaySessions = computed(() => {
  // displaySessions：过滤掉“已结束”的场次（endTime < now）。
  // 未结束场次都展示（可预约/不可预约/已预约）。
  const list = sessions.value || []
  const now = dayjs()
  return list.filter((s) => {
    if (!s) return false

    const end = s.endTime ? dayjs(s.endTime) : null

    // 已过期（当前时间在结束时间之后）：不渲染
    if (end && end.isValid() && end.isBefore(now)) {
      return false
    }

    // 未过期的场次都显示（可预约/不可预约/已预约）
    return true
  })
})

const fetchDetail = async () => {
  // 拉取课程详情：公共可见。
  loading.value = true
  try {
    const res = await getCourseDetail(route.params.id)
    course.value = res.data
  } catch (e) {
    window.$message?.error('获取课程详情失败')
  } finally {
    loading.value = false
  }
}

const fetchSessions = async () => {
  // 拉取课程场次：
  // - 基础列表来自 getCourseSessions(courseId)
  // - 若已登录：并行拉取我的预约 getMyCourseBookings，用于派生 myBookedSessionIds
  //   这样 UI 可以区分“已被我预约”而不是笼统的“不可预约”
  try {
    const courseId = route.params.id
    const reqs = [getCourseSessions(courseId)]
    if (authStore.isLoggedIn) {
      reqs.push(getMyCourseBookings({ page: 1, size: 200, status: 'PENDING_COACH,ACCEPTED,PAID' }))
    }
    const res = await Promise.all(reqs)

    sessions.value = res?.[0]?.data || []

    if (authStore.isLoggedIn) {
      const bookingResp = res?.[1]?.data
      const items = bookingResp?.items || bookingResp?.list || bookingResp || []
      const set = new Set()
      const targetCourseId = Number(courseId)
      for (const it of items) {
        if (!it) continue
        if (it.courseId != null && Number(it.courseId) !== targetCourseId) continue
        if (it.sessionId != null) {
          set.add(it.sessionId)
        }
      }
      myBookedSessionIds.value = set
    } else {
      myBookedSessionIds.value = new Set()
    }
  } catch (e) {
    console.error(e)
  }
}

const fetchReviews = async () => {
  // 拉取课程评价（分页）：这里固定 size=5。
  try {
    const res = await getCourseReviews(route.params.id, { page: reviewPage.value, size: 5 })
    reviews.value = res.data?.items || res.data?.list || res.data || []
    reviewTotal.value = res.data?.total ?? reviews.value.length
  } catch (e) {
    console.error(e)
  }
}

const checkFavoriteStatus = async () => {
  // 收藏状态是“用户维度”的信息：未登录不查。
  if (!authStore.isLoggedIn) return
  try {
    const res = await checkFavorite({ type: 'course', targetId: route.params.id })
    isFavorited.value = res.data?.favorited || false
  } catch (e) {
    console.error(e)
  }
}

const toggleFavorite = async () => {
  // 收藏/取消收藏：未登录提示并跳转。
  if (!authStore.isLoggedIn) {
    window.$message?.warning('请先登录')
    router.push('/login')
    return
  }
  try {
    if (isFavorited.value) {
      await removeFavorite({ type: 'course', targetId: route.params.id })
      isFavorited.value = false
      window.$message?.success('已取消收藏')
    } else {
      await addFavorite({ type: 'course', targetId: route.params.id })
      isFavorited.value = true
      window.$message?.success('收藏成功')
    }
  } catch (e) {
    window.$message?.error('操作失败')
  }
}

const handleConsult = () => {
  // 打开咨询弹窗：仅登录用户可用。
  if (!authStore.isLoggedIn) {
    window.$message?.warning('请先登录')
    router.push('/login')
    return
  }
  consultContent.value = ''
  showConsultModal.value = true
}

const confirmConsult = async () => {
  // 发送咨询：
  // - createConsultation 创建会话
  // - 成功后跳转到 /user/consultations，并携带 consultationId 直接打开详情
  if (!authStore.isLoggedIn) {
    window.$message?.warning('请先登录')
    router.push('/login')
    return
  }
  if (!consultContent.value.trim()) return
  consulting.value = true
  try {
    const res = await createConsultation({
      courseId: Number(route.params.id),
      content: consultContent.value
    })
    const id = res?.data?.id
    window.$message?.success('咨询已发送')
    showConsultModal.value = false
    if (id) {
      router.push(`/user/consultations?consultationId=${id}`)
    } else {
      router.push('/user/consultations')
    }
  } catch (e) {
    window.$message?.error(e?.response?.data?.message || '发送失败')
  } finally {
    consulting.value = false
  }
}

const handleBook = (session) => {
  // 打开预约确认弹窗：未登录提示并跳转。
  if (!authStore.isLoggedIn) {
    window.$message?.warning('请先登录')
    router.push('/login')
    return
  }
  selectedSession.value = session
  showBookModal.value = true
}

const confirmBook = async () => {
  // 确认预约：
  // - createCourseBooking 由后端做幂等/并发与费用校验
  // - 成功后刷新场次列表，更新“已预约”展示
  booking.value = true
  try {
    const res = await createCourseBooking({
      sessionId: selectedSession.value.id
    })
    // 检查响应体中的业务错误码
    if (res.data?.code && res.data.code !== 200) {
      window.$message?.error(res.data?.msg || res.data?.message || '预约失败')
      return
    }
    window.$message?.success('预约成功，请前往我的预约查看')
    showBookModal.value = false
    fetchSessions()
  } catch (e) {
    window.$message?.error(e?.response?.data?.msg || e?.response?.data?.message || '预约失败')
  } finally {
    booking.value = false
  }
}

onMounted(() => {
  fetchDetail()
  fetchSessions()
  fetchReviews()
  checkFavoriteStatus()
})
</script>

<style scoped>
.course-detail {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}
.back-btn-wrap {
  margin-bottom: 16px;
}
.detail-content {
  display: flex;
  gap: 40px;
}
.left {
  flex: 0 0 400px;
}
.main-img {
  width: 100%;
  border-radius: 8px;
}
.right {
  flex: 1;
}
.title {
  font-size: 24px;
  margin: 0 0 16px;
}
.coach-info {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.coach-text {
  display: flex;
  flex-direction: column;
}
.coach-name {
  font-weight: 500;
}
.coach-title {
  font-size: 12px;
  color: #999;
}
.desc {
  color: #666;
  line-height: 1.6;
  margin-bottom: 20px;
}
.price-row {
  display: flex;
  align-items: center;
  gap: 20px;
}
.price {
  font-size: 28px;
  color: #f5222d;
  font-weight: 600;
}
.action-row {
  display: flex;
  gap: 16px;
}
.sessions-card, .reviews-card {
  margin-top: 20px;
}
.sessions-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.session-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
}
.session-info {
  display: flex;
  gap: 20px;
}
.session-date {
  font-weight: 500;
}
.session-time {
  color: #666;
}
.session-venue {
  color: #999;
}
.session-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.capacity {
  font-size: 13px;
  color: #18a058;
}
.capacity.full {
  color: #f5222d;
}
.review-item {
  padding: 16px 0;
  border-bottom: 1px solid #eee;
}
.review-item:last-child {
  border-bottom: none;
}
.review-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.username {
  font-weight: 500;
}
.time {
  color: #999;
  font-size: 12px;
  margin-left: auto;
}
.review-content {
  margin: 0;
  color: #333;
  line-height: 1.6;
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
