<template>
  <div class="venue-detail-page">
    <div class="page-wrapper">
      <div class="back-btn-wrap">
        <n-button text @click="router.back()">
          <template #icon><n-icon><ArrowBackOutline /></n-icon></template>
          返回
        </n-button>
      </div>
      <n-spin :show="loading">
        <template v-if="venue">
          <!-- 场地信息 -->
          <n-card class="venue-info-card">
            <div class="venue-info">
              <div class="venue-cover">
                <img :src="venue.coverUrl || '/placeholder.svg'" :alt="venue.name" />
              </div>
              <div class="venue-content">
                <div class="venue-header">
                  <n-tag type="success">{{ venue.typeName }}</n-tag>
                  <n-tag :type="getStatusType(venue.status)">{{ getStatusText(venue.status) }}</n-tag>
                </div>
                <h1 class="venue-name">{{ venue.name }}</h1>
                <p class="venue-location">
                  <n-icon><LocationOutline /></n-icon>
                  {{ venue.area }} · {{ venue.address }}
                </p>
                <div class="venue-meta">
                  <div class="meta-item">
                    <span class="label">规格</span>
                    <span class="value">{{ venue.spec }}</span>
                  </div>
                  <div class="meta-item">
                    <span class="label">开放时间</span>
                    <span class="value">{{ venue.openTimeDesc }}</span>
                  </div>
                  <div class="meta-item">
                    <span class="label">联系电话</span>
                    <span class="value">{{ venue.contactPhone || '-' }}</span>
                  </div>
                </div>
                <div class="venue-price">
                  <span class="price-value">¥{{ venue.pricePerHour }}</span>
                  <span class="price-unit">/小时</span>
                </div>
                <div class="venue-actions">
                  <n-button
                    :type="isFavorited ? 'warning' : 'default'"
                    @click="handleToggleFavorite"
                  >
                    <template #icon>
                      <n-icon><component :is="isFavorited ? Heart : HeartOutline" /></n-icon>
                    </template>
                    {{ isFavorited ? '已收藏' : '收藏' }}
                  </n-button>
                </div>
              </div>
            </div>
          </n-card>

          <!-- 场地介绍 -->
          <n-card v-if="venue.description" title="场地介绍" class="section-card">
            <div class="description" v-html="venue.description"></div>
          </n-card>

          <!-- 预约时段 -->
          <n-card title="选择预约时段" class="section-card">
            <div class="booking-section">
              <div class="date-picker">
                <n-date-picker
                  v-model:value="selectedDate"
                  type="date"
                  :is-date-disabled="isDateDisabled"
                  @update:value="fetchTimeslots"
                />
              </div>

              <n-spin :show="timeslotsLoading">
                <div v-if="timeslots.length" class="timeslot-grid">
                  <div
                    v-for="slot in timeslots"
                    :key="slot.id"
                    class="timeslot-item"
                    :class="{
                      selected: selectedTimeslot?.id === slot.id,
                      disabled: !isSlotSelectable(slot)
                    }"
                    @click="handleSelectTimeslot(slot)"
                  >
                    <div class="slot-time">
                      {{ formatTime(slot.startTime) }} - {{ formatTime(slot.endTime) }}
                    </div>
                    <div class="slot-price">¥{{ slot.price }}</div>
                    <div class="slot-status">
                      {{ getSlotText(slot) }}
                    </div>
                  </div>
                </div>
                <n-empty v-else description="该日期暂无可预约时段" />
              </n-spin>

              <div v-if="selectedTimeslot" class="booking-summary">
                <div class="summary-info">
                  <span>已选时段：{{ formatTime(selectedTimeslot.startTime) }} - {{ formatTime(selectedTimeslot.endTime) }}</span>
                  <span class="summary-price">¥{{ selectedTimeslot.price }}</span>
                </div>
                <n-button type="primary" size="large" :loading="booking" @click="handleBooking">
                  立即预约
                </n-button>
              </div>
            </div>
          </n-card>

          <n-card title="用户评价" class="section-card">
            <n-spin :show="reviewsLoading">
              <div v-if="reviews.length">
                <div v-for="r in reviews" :key="r.id" class="review-item">
                  <div class="review-header">
                    <div class="review-user">{{ r.username || '匿名用户' }}</div>
                    <div class="review-meta">
                      <n-rate :value="r.rating" readonly size="small" />
                      <span class="review-time">{{ formatDateTime(r.createdAt) }}</span>
                    </div>
                  </div>
                  <div class="review-content">{{ r.content || '默认好评' }}</div>
                </div>

                <div class="pagination-wrap" v-if="reviewTotal > 0">
                  <n-pagination
                    v-model:page="reviewPagination.page"
                    :page-size="reviewPagination.pageSize"
                    :item-count="reviewTotal"
                    @update:page="fetchReviews"
                  />
                </div>
              </div>
              <n-empty v-else description="暂无评价" />
            </n-spin>
          </n-card>
        </template>
      </n-spin>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'
import { getVenueDetail, getTimeslots, createBooking, getMyBookings, getVenueReviews } from '@/api/venue'
import { checkFavoriteStatus, addFavorite, removeFavorite } from '@/api/favorite'
import dayjs from 'dayjs'
import { LocationOutline, HeartOutline, Heart, ArrowBackOutline } from '@vicons/ionicons5'

// 场地详情页（用户端核心链路页面）
//
// 页面职责：
// - 展示场地基础信息（封面/类型/状态/规格/开放时间/价格/富文本介绍）
// - 展示某天的可预约时段（timeslots），并允许用户选择时段后下单预约
// - 展示用户评价列表（分页）
// - 展示并维护收藏状态（登录用户专属）
//
// 数据流（高层）：
// onMounted 并行触发：
// - fetchVenue：加载场地信息 +（若已登录）检查收藏状态
// - fetchTimeslots：加载所选日期的时段 +（若已登录）派生“我已预约”的时段集合
// - fetchReviews：加载评价分页列表
//
// 权限边界：
// - venueDetail/timeslots/reviews 通常是公共接口，不要求登录
// - 收藏/预约/获取我的预约 需要登录；最终的资源级校验与幂等/并发控制都由后端保证

const route = useRoute()
const router = useRouter()
const message = useMessage()
const authStore = useAuthStore()

const loading = ref(true)
const venue = ref(null)
const isFavorited = ref(false)

const selectedDate = ref(Date.now())
const timeslots = ref([])
const timeslotsLoading = ref(false)
const selectedTimeslot = ref(null)
const booking = ref(false)
const myBookedTimeslotIds = ref(new Set())

const reviewsLoading = ref(false)
const reviews = ref([])
const reviewTotal = ref(0)
const reviewPagination = ref({
  page: 1,
  pageSize: 10
})

const venueId = computed(() => Number(route.params.id))

const fetchVenue = async () => {
  // 拉取场地详情：
  // - 该接口通常是公共可访问（不要求登录）
  // - 但收藏状态是“登录用户维度”的，因此需要额外调用 checkFavorite
  loading.value = true
  try {
    const res = await getVenueDetail(venueId.value)
    venue.value = res.data
    await checkFavorite()
  } catch (e) {
    message.error('获取场地信息失败')
    console.error(e)
  } finally {
    loading.value = false
  }
}

const fetchReviews = async () => {
  // 拉取场地评价（分页）：
  // - 参数使用 page/size
  // - 返回结构以 {items,total} 为主（这里做了兜底）
  reviewsLoading.value = true
  try {
    const res = await getVenueReviews(venueId.value, {
      page: reviewPagination.value.page,
      size: reviewPagination.value.pageSize
    })
    const data = res.data || {}
    reviews.value = data.items || []
    reviewTotal.value = data.total ?? reviews.value.length
  } catch (e) {
    reviews.value = []
    reviewTotal.value = 0
  } finally {
    reviewsLoading.value = false
  }
}

const checkFavorite = async () => {
  // 收藏状态是“用户维度”的信息：未登录就不查。
  if (!authStore.isLoggedIn) return
  try {
    const res = await checkFavoriteStatus({
      targetType: 'VENUE',
      targetId: venueId.value
    })
    isFavorited.value = res.data?.favorited || false
  } catch (e) {
    // ignore
  }
}

const handleToggleFavorite = async () => {
  // 收藏/取消收藏：
  // - 未登录：引导去登录（收藏是登录态能力）
  // - 已登录：根据 isFavorited 调不同接口
  // - 一般期望后端幂等（重复点击不会产生脏数据），前端用 try/catch 做兜底提示
  if (!authStore.isLoggedIn) {
    message.warning('请先登录')
    router.push('/login')
    return
  }

  try {
    if (isFavorited.value) {
      await removeFavorite({ targetType: 'VENUE', targetId: venueId.value })
      isFavorited.value = false
      message.success('已取消收藏')
    } else {
      await addFavorite({ targetType: 'VENUE', targetId: venueId.value })
      isFavorited.value = true
      message.success('收藏成功')
    }
  } catch (e) {
    message.error('操作失败')
  }
}

const fetchTimeslots = async () => {
  // 拉取某天时段：
  // 1) 先查公共时段列表（getTimeslots）
  // 2) 再（可选）查“我的预约”并派生一个 Set，用于把 UI 上的时段细分为：
  //    - 我已预约（不可再选，但显示为已预约）
  //    - 已过期（不可选）
  //    - 后端标记不可用（status != AVAILABLE，不可选）
  //
  // 这种“用我的预约反查”有一定代价（多一次请求），但能让用户明确区分“被别人抢了”还是“我自己已订”。
  timeslotsLoading.value = true
  selectedTimeslot.value = null
  try {
    const date = dayjs(selectedDate.value).format('YYYY-MM-DD')
    const res = await getTimeslots(venueId.value, { date })
    timeslots.value = res.data || []

    // 标记“我已预约”的时段（用于区分自己预约 vs 他人预约）
    myBookedTimeslotIds.value = new Set()
    if (authStore.isLoggedIn && authStore.accessToken) {
      try {
        const bookingRes = await getMyBookings({ page: 1, size: 200 })
        const records = bookingRes?.data?.records || bookingRes?.data?.items || []
        const targetDate = dayjs(selectedDate.value).format('YYYY-MM-DD')
        for (const b of records) {
          if (!b) continue
          if (b.venueId !== venueId.value) continue
          if (!b.timeslotId) continue
          if (b.status !== 'PAID' && b.status !== 'USED') continue
          const d = b.startTime ? dayjs(b.startTime).format('YYYY-MM-DD') : null
          if (d !== targetDate) continue
          myBookedTimeslotIds.value.add(b.timeslotId)
        }
      } catch (e) {
        // ignore
      }
    }
  } catch (e) {
    console.error('Failed to fetch timeslots:', e)
  } finally {
    timeslotsLoading.value = false
  }
}

const isDateDisabled = (ts) => {
  return dayjs(ts).isBefore(dayjs().startOf('day'))
}

const formatTime = (datetime) => {
  return dayjs(datetime).format('HH:mm')
}

const formatDateTime = (datetime) => {
  if (!datetime) return '-'
  return dayjs(datetime).format('YYYY-MM-DD HH:mm')
}

const isSlotBookedByMe = (slot) => {
  if (!slot || slot.id === undefined || slot.id === null) return false
  return myBookedTimeslotIds.value?.has?.(slot.id) || false
}

const isSlotExpired = (slot) => {
  if (!slot?.startTime) return false
  return dayjs(slot.startTime).isBefore(dayjs())
}

const getSlotText = (slot) => {
  // 时段展示文案：优先级
  // 1) 我已预约
  // 2) 已过期
  // 3) 后端可预约（AVAILABLE）
  if (isSlotBookedByMe(slot)) return '已预约'
  if (isSlotExpired(slot)) return '不可预约'
  if (slot?.status === 'AVAILABLE') return '可预约'
  return '不可预约'
}

const isSlotSelectable = (slot) => {
  if (!slot) return false
  if (isSlotBookedByMe(slot)) return false
  if (isSlotExpired(slot)) return false
  return slot.status === 'AVAILABLE'
}

const handleSelectTimeslot = (slot) => {
  // 选择时段：只允许选择“可预约”的 slot。
  if (!isSlotSelectable(slot)) return
  selectedTimeslot.value = slot
}

const handleBooking = async () => {
  // 创建预约（下单）：
  // - 未登录：引导登录
  // - 必须已选择时段
  // - 成功后跳转到“我的预约”列表，方便用户查看核销码/取消/评价
  if (!authStore.isLoggedIn) {
    message.warning('请先登录')
    router.push('/login')
    return
  }

  if (!selectedTimeslot.value) {
    message.warning('请选择预约时段')
    return
  }

  booking.value = true
  try {
    const res = await createBooking({
      timeslotId: selectedTimeslot.value.id
    })
    // 检查响应体中的业务错误码
    if (res.data?.code && res.data.code !== 200) {
      message.error(res.data?.msg || res.data?.message || '预约失败')
      return
    }
    message.success('预约成功')
    router.push('/user/bookings')
  } catch (e) {
    const msg = e?.response?.data?.msg || e?.response?.data?.message || '预约失败'
    message.error(msg)
  } finally {
    booking.value = false
  }
}

const getStatusType = (status) => {
  const map = {
    ACTIVE: 'success',
    MAINTENANCE: 'warning',
    DISABLED: 'error'
  }
  return map[status] || 'default'
}

const getStatusText = (status) => {
  const map = {
    ACTIVE: '可预约',
    MAINTENANCE: '维护中',
    DISABLED: '已停用'
  }
  return map[status] || status
}

onMounted(() => {
  fetchVenue()
  fetchTimeslots()
  fetchReviews()
})
</script>

<style scoped>
.venue-detail-page {
  min-height: 100%;
  background: #f5f7f9;
}

.page-wrapper {
  max-width: 1000px;
  margin: 0 auto;
  padding: 24px 16px;
}

.back-btn-wrap {
  margin-bottom: 16px;
}

.venue-info-card {
  margin-bottom: 16px;
}

.venue-info {
  display: flex;
  gap: 24px;
}

.venue-cover {
  width: 400px;
  height: 300px;
  flex-shrink: 0;
  border-radius: 8px;
  overflow: hidden;
}

.venue-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.venue-content {
  flex: 1;
}

.venue-header {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.venue-name {
  font-size: 24px;
  font-weight: 600;
  color: #333;
  margin: 0 0 12px;
}

.venue-location {
  font-size: 14px;
  color: #666;
  display: flex;
  align-items: center;
  gap: 4px;
  margin: 0 0 16px;
}

.venue-meta {
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

.venue-price {
  margin-bottom: 16px;
}

.price-value {
  font-size: 28px;
  font-weight: 600;
  color: #f5222d;
}

.price-unit {
  font-size: 14px;
  color: #999;
}

.venue-actions {
  display: flex;
  gap: 12px;
}

.section-card {
  margin-bottom: 16px;
}

.review-item {
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}

.review-item:last-child {
  border-bottom: none;
}

.review-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.review-user {
  font-weight: 600;
  color: #333;
}

.review-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.review-time {
  font-size: 12px;
  color: #999;
}

.review-content {
  font-size: 14px;
  color: #555;
  line-height: 1.6;
}

.description {
  line-height: 1.8;
  color: #666;
}

.booking-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.date-picker {
  display: flex;
  justify-content: center;
}

.timeslot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
}

.timeslot-item {
  padding: 12px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
}

.timeslot-item:hover:not(.disabled) {
  border-color: #18a058;
}

.timeslot-item.selected {
  border-color: #18a058;
  background: #f0fdf4;
}

.timeslot-item.disabled {
  background: #f5f5f5;
  cursor: not-allowed;
  opacity: 0.6;
}

.slot-time {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin-bottom: 4px;
}

.slot-price {
  font-size: 16px;
  font-weight: 600;
  color: #f5222d;
  margin-bottom: 4px;
}

.slot-status {
  font-size: 12px;
  color: #999;
}

.booking-summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: #f9f9f9;
  border-radius: 8px;
}

.summary-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.summary-price {
  font-size: 20px;
  font-weight: 600;
  color: #f5222d;
}

@media (max-width: 768px) {
  .venue-info {
    flex-direction: column;
  }

  .venue-cover {
    width: 100%;
    height: 200px;
  }

  .venue-meta {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
