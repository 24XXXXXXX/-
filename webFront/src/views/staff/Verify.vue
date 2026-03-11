<template>
  <div class="staff-verify-page">
    <n-card title="核销功能">
      <n-tabs v-model:value="activeTab">
        <n-tab-pane name="venue" tab="场地预约核销">
          <div class="verify-form">
            <n-form-item label="核销码">
              <n-input v-model:value="venueCode" placeholder="请输入场地预约核销码" clearable />
            </n-form-item>
            <n-button type="primary" :loading="verifyingVenue" @click="handleVerifyVenue">
              核销
            </n-button>
          </div>

          <div v-if="venueBooking" class="booking-info">
            <n-descriptions :column="2" label-placement="left" bordered>
              <n-descriptions-item label="预约号">{{ venueBooking.bookingNo }}</n-descriptions-item>
              <n-descriptions-item label="场地">{{ venueBooking.venueName || '-' }}</n-descriptions-item>
              <n-descriptions-item label="预约人">{{ venueBooking.username || '-' }}</n-descriptions-item>
              <n-descriptions-item label="时间">{{ formatDate(venueBooking.startTime) }}-{{ formatDate(venueBooking.endTime) }}</n-descriptions-item>
            </n-descriptions>
            <n-result v-if="venueVerified" status="success" title="核销成功" />
          </div>
        </n-tab-pane>

        <n-tab-pane name="course" tab="课程预约核销">
          <div class="verify-form">
            <n-form-item label="核销码">
              <n-input v-model:value="courseCode" placeholder="请输入课程预约核销码" clearable />
            </n-form-item>
            <n-button type="primary" :loading="verifyingCourse" @click="handleVerifyCourse">
              核销
            </n-button>
          </div>

          <div v-if="courseBooking" class="booking-info">
            <n-descriptions :column="2" label-placement="left" bordered>
              <n-descriptions-item label="预约号">{{ courseBooking.bookingNo }}</n-descriptions-item>
              <n-descriptions-item label="课程">{{ courseBooking.courseTitle || '-' }}</n-descriptions-item>
              <n-descriptions-item label="教练">{{ courseBooking.coachUsername || '-' }}</n-descriptions-item>
              <n-descriptions-item label="学员">{{ courseBooking.username || '-' }}</n-descriptions-item>
              <n-descriptions-item label="上课时间">{{ formatDate(courseBooking.startTime) }}-{{ formatDate(courseBooking.endTime) }}</n-descriptions-item>
            </n-descriptions>
            <n-result v-if="courseVerified" status="success" title="核销成功" />
          </div>
        </n-tab-pane>
      </n-tabs>
    </n-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { http } from '@/api/http'
import { verifyCourseBooking } from '@/api/course'
import dayjs from 'dayjs'

const activeTab = ref('venue')

const venueCode = ref('')
const verifyingVenue = ref(false)
const venueBooking = ref(null)
const venueVerified = ref(false)

const courseCode = ref('')
const verifyingCourse = ref(false)
const courseBooking = ref(null)
const courseVerified = ref(false)

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

const formatDate = (date) => {
  if (!date) return '-'
  return dayjs(date).format('YYYY-MM-DD HH:mm')
}

const handleVerifyVenue = async () => {
  if (!venueCode.value.trim()) {
    window.$message?.warning('请输入核销码')
    return
  }
  verifyingVenue.value = true
  venueVerified.value = false
  try {
    const res = await http.post('/api/bookings/verify', { verifyCode: venueCode.value })
    // 检查响应体中的业务错误码
    if (res.data?.code && res.data.code !== 200) {
      venueBooking.value = null
      const msg = translateErrorMsg(res.data?.msg || res.data?.message)
      window.$message?.error(msg)
      return
    }
    venueBooking.value = res.data
    venueVerified.value = true
    window.$message?.success('核销成功')
  } catch (e) {
    venueBooking.value = null
    const rawMsg = e?.response?.data?.msg || e?.response?.data?.message || '核销失败，请检查核销码'
    const msg = translateErrorMsg(rawMsg)
    window.$message?.error(msg)
  } finally {
    verifyingVenue.value = false
  }
}

const handleVerifyCourse = async () => {
  if (!courseCode.value.trim()) {
    window.$message?.warning('请输入核销码')
    return
  }
  verifyingCourse.value = true
  courseVerified.value = false
  try {
    const res = await verifyCourseBooking({ verifyCode: courseCode.value })
    // 检查响应体中的业务错误码
    if (res.data?.code && res.data.code !== 200) {
      courseBooking.value = null
      const msg = translateErrorMsg(res.data?.msg || res.data?.message)
      window.$message?.error(msg)
      return
    }
    courseBooking.value = res.data
    courseVerified.value = true
    window.$message?.success('核销成功')
  } catch (e) {
    courseBooking.value = null
    const rawMsg = e?.response?.data?.msg || e?.response?.data?.message || '核销失败，请检查核销码'
    const msg = translateErrorMsg(rawMsg)
    window.$message?.error(msg)
  } finally {
    verifyingCourse.value = false
  }
}
</script>

<style scoped>
.staff-verify-page {
  padding: 20px;
}
.verify-form {
  display: flex;
  gap: 16px;
  align-items: flex-end;
  margin-bottom: 24px;
}
.verify-form .n-form-item {
  flex: 1;
  margin-bottom: 0;
}
.booking-info {
  margin-top: 20px;
}
</style>
