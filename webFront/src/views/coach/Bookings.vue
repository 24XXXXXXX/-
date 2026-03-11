<template>
  <div class="coach-bookings-page">
    <n-card title="预约管理">
      <n-tabs v-model:value="activeTab" @update:value="handleTabChange">
        <n-tab-pane name="pending" tab="待处理" />
        <n-tab-pane name="confirmed" tab="已确认" />
        <n-tab-pane name="paid" tab="已支付" />
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
              <div class="booking-info">
                <p><strong>课程:</strong> {{ booking.courseTitle }}</p>
                <p><strong>学员:</strong> {{ booking.username || booking.userId }}</p>
                <p><strong>时间:</strong> {{ formatDate(booking.startTime) }} {{ formatTime(booking.startTime) }}-{{ formatTime(booking.endTime) }}</p>
                <p><strong>费用:</strong> ¥{{ booking.amount }}</p>
              </div>
            </div>
            <div class="booking-footer">
              <div class="verify-code" v-if="booking.status === 'PAID' && booking.verificationCode">
                核销码: <n-tag type="success">{{ booking.verificationCode }}</n-tag>
              </div>
              <n-space>
                <n-button v-if="booking.status === 'PENDING_COACH'" type="primary" @click="handleAccept(booking.id)">
                  接受预约
                </n-button>
                <n-button v-if="booking.status === 'PENDING_COACH'" @click="openRejectModal(booking)">
                  拒绝
                </n-button>
                <n-button v-if="booking.status === 'PAID'" type="success" :loading="booking.verifying" @click="handleVerify(booking)">
                  核销
                </n-button>
              </n-space>
            </div>
          </div>
        </div>
        <n-empty v-else description="暂无预约" />
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

    <!-- 拒绝弹窗 -->
    <n-modal v-model:show="showRejectModal" preset="card" title="拒绝预约" style="width: 400px">
      <n-form-item label="拒绝原因">
        <n-input v-model:value="rejectReason" type="textarea" placeholder="请输入拒绝原因" :rows="3" />
      </n-form-item>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showRejectModal = false">取消</n-button>
          <n-button type="error" :loading="rejecting" @click="handleReject">确认拒绝</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getCoachBookings, acceptBooking, rejectBooking, verifyCoachBooking } from '@/api/coach'
import dayjs from 'dayjs'

const loading = ref(false)
const bookings = ref([])
const total = ref(0)
const activeTab = ref('pending')

const pagination = reactive({
  page: 1,
  pageSize: 10
})

const showRejectModal = ref(false)
const rejecting = ref(false)
const rejectReason = ref('')
const currentBooking = ref(null)

// 英文错误消息翻译映射
const errorMsgMap = {
  'invalid verification code': '核销码无效',
  'verification code not found': '核销码不存在',
  'booking not found': '预约不存在',
  'booking already verified': '该预约已核销',
  'booking already used': '该预约已使用',
  'booking cancelled': '该预约已取消',
  'booking expired': '该预约已过期',
  'not paid': '该预约未支付',
  'class not started': '未到上课时间，无法核销',
  'course not started': '未到上课时间，无法核销'
}

const translateErrorMsg = (msg) => {
  if (!msg) return '核销失败'
  const lowerMsg = msg.toLowerCase()
  for (const [en, zh] of Object.entries(errorMsgMap)) {
    if (lowerMsg.includes(en.toLowerCase())) {
      return zh
    }
  }
  return msg
}

const formatDate = (date) => dayjs(date).format('YYYY-MM-DD')
const formatTime = (date) => dayjs(date).format('HH:mm')

const statusMap = {
  PENDING_COACH: { text: '待处理', type: 'warning' },
  ACCEPTED: { text: '已确认', type: 'info' },
  PAID: { text: '已支付', type: 'info' },
  USED: { text: '已完成', type: 'success' },
  CANCELED: { text: '已取消', type: 'default' },
  REFUNDED: { text: '已退款', type: 'default' },
  REJECTED: { text: '已拒绝', type: 'error' }
}

const tabToStatus = {
  pending: 'PENDING_COACH',
  confirmed: 'ACCEPTED',
  paid: 'PAID',
  completed: 'USED',
  cancelled: 'CANCELED'
}

const getStatusText = (status) => statusMap[status]?.text || status
const getStatusType = (status) => statusMap[status]?.type || 'default'

const fetchBookings = async () => {
  loading.value = true
  try {
    const params = {
      status: tabToStatus[activeTab.value] || undefined,
      page: pagination.page,
      size: pagination.pageSize
    }
    const res = await getCoachBookings(params)
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

const handleAccept = async (id) => {
  try {
    await acceptBooking(id)
    window.$message?.success('已接受预约')
    fetchBookings()
  } catch (e) {
    window.$message?.error('操作失败')
  }
}

const openRejectModal = (booking) => {
  currentBooking.value = booking
  rejectReason.value = ''
  showRejectModal.value = true
}

const handleReject = async () => {
  rejecting.value = true
  try {
    await rejectBooking(currentBooking.value.id, { rejectReason: rejectReason.value })
    window.$message?.success('已拒绝预约')
    showRejectModal.value = false
    fetchBookings()
  } catch (e) {
    window.$message?.error('操作失败')
  } finally {
    rejecting.value = false
  }
}

const handleVerify = async (booking) => {
  if (!booking.verificationCode) {
    window.$message?.warning('该预约没有核销码')
    return
  }
  booking.verifying = true
  try {
    const res = await verifyCoachBooking({ bookingNo: booking.bookingNo, verificationCode: booking.verificationCode })
    // 检查响应体中的业务错误码
    if (res.data?.code && res.data.code !== 200) {
      const msg = translateErrorMsg(res.data?.msg || res.data?.message)
      window.$message?.error(msg)
      return
    }
    window.$message?.success('核销成功')
    fetchBookings()
  } catch (e) {
    const rawMsg = e?.response?.data?.msg || e?.response?.data?.message || '核销失败'
    const msg = translateErrorMsg(rawMsg)
    window.$message?.error(msg)
  } finally {
    booking.verifying = false
  }
}

onMounted(() => {
  fetchBookings()
})
</script>

<style scoped>
.coach-bookings-page {
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
  padding: 16px;
}
.booking-info p {
  margin: 0 0 8px;
  font-size: 14px;
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
