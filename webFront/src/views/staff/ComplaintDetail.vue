<template>
  <div class="staff-complaint-detail-page">
    <n-spin :show="loading">
      <n-card v-if="complaint">
        <template #header>
          <div class="header-row">
            <n-button text @click="$router.back()">
              <n-icon><ArrowBackOutline /></n-icon> 返回
            </n-button>
            <span>投诉详情</span>
            <n-tag :type="getStatusType(complaint.status)">{{ getStatusText(complaint.status) }}</n-tag>
          </div>
        </template>

        <n-descriptions :column="2" label-placement="left">
          <n-descriptions-item label="投诉编号">{{ complaint.complaintNo }}</n-descriptions-item>
          <n-descriptions-item label="投诉类型">
            <n-tag :type="getTypeTagType(complaint.complaintType || complaint.type)" size="small">{{ getTypeText(complaint.complaintType || complaint.type) }}</n-tag>
          </n-descriptions-item>
          <n-descriptions-item label="提交时间">{{ formatDate(complaint.createdAt) }}</n-descriptions-item>
          <n-descriptions-item label="投诉人">{{ complaint.userName || complaint.username || '-' }}</n-descriptions-item>
        </n-descriptions>

        <n-divider />

        <h3>{{ complaint.title }}</h3>
        <p class="complaint-content">{{ complaint.content }}</p>

        <div v-if="complaint.attachments?.length" class="attachments">
          <h4>附件</h4>
          <n-image-group>
            <n-space>
              <n-image
                v-for="(url, index) in complaint.attachments"
                :key="index"
                :src="url"
                width="100"
                height="100"
                object-fit="cover"
              />
            </n-space>
          </n-image-group>
        </div>

        <n-divider />

        <!-- 状态更新 -->
        <div v-if="!isResolved" class="status-section">
          <h4>更新状态</h4>
          <n-space>
            <n-select v-model:value="newStatus" :options="statusOptions" style="width: 150px" />
            <n-button type="primary" :loading="updatingStatus" @click="handleUpdateStatus">更新状态</n-button>
          </n-space>
        </div>

        <n-divider />

        <!-- 消息记录 -->
        <h4>处理记录</h4>
        <div class="message-list">
          <div v-for="msg in complaint.messages" :key="msg.id" :class="['message-item', msg.senderRole === 'STAFF' ? 'staff' : 'user']">
            <div class="msg-header">
              <n-avatar :src="msg.avatar" size="small" />
              <span class="msg-sender">{{ msg.senderUsername || msg.senderName || '-' }}</span>
              <span class="msg-time">{{ formatDate(msg.createdAt) }}</span>
            </div>
            <div class="msg-content">{{ msg.content }}</div>
          </div>
          <n-empty v-if="!complaint.messages?.length" description="暂无处理记录" size="small" />
        </div>

        <!-- 回复 -->
        <div v-if="!isResolved" class="reply-section">
          <n-input
            v-model:value="replyContent"
            type="textarea"
            placeholder="输入回复内容..."
            :rows="3"
          />
          <n-button type="primary" :loading="sending" :disabled="!replyContent.trim()" @click="handleSendMessage">
            发送回复
          </n-button>
        </div>
      </n-card>
    </n-spin>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowBackOutline } from '@vicons/ionicons5'
import { getStaffComplaintDetail, updateComplaintStatus, addStaffComplaintMessage } from '@/api/staff'
import dayjs from 'dayjs'

const route = useRoute()
const loading = ref(false)
const complaint = ref(null)
const replyContent = ref('')
const sending = ref(false)
const newStatus = ref('processing')
const updatingStatus = ref(false)

const statusOptions = [
  { label: '处理中', value: 'PROCESSING' },
  { label: '已解决', value: 'RESOLVED' }
]

const formatDate = (date) => dayjs(date).format('YYYY-MM-DD HH:mm')

const typeMap = {
  venue: { text: '场地问题', type: 'info' },
  VENUE: { text: '场地问题', type: 'info' },
  equipment: { text: '器材问题', type: 'warning' },
  EQUIPMENT: { text: '器材问题', type: 'warning' },
  course: { text: '课程问题', type: 'primary' },
  COURSE: { text: '课程问题', type: 'primary' },
  service: { text: '服务态度', type: 'error' },
  SERVICE: { text: '服务态度', type: 'error' },
  refund: { text: '退款问题', type: 'warning' },
  REFUND: { text: '退款问题', type: 'warning' },
  other: { text: '其他问题', type: 'default' },
  OTHER: { text: '其他问题', type: 'default' }
}

const getTypeText = (type) => typeMap[type]?.text || typeMap[type?.toLowerCase()]?.text || type || '-'
const getTypeTagType = (type) => typeMap[type]?.type || typeMap[type?.toLowerCase()]?.type || 'default'

const statusMap = {
  pending: { text: '待处理', type: 'warning' },
  PENDING: { text: '待处理', type: 'warning' },
  processing: { text: '处理中', type: 'info' },
  PROCESSING: { text: '处理中', type: 'info' },
  resolved: { text: '已解决', type: 'success' },
  RESOLVED: { text: '已解决', type: 'success' },
  closed: { text: '已关闭', type: 'default' },
  CLOSED: { text: '已关闭', type: 'default' }
}

const getStatusText = (status) => statusMap[status]?.text || statusMap[status?.toLowerCase()]?.text || status
const getStatusType = (status) => statusMap[status]?.type || statusMap[status?.toLowerCase()]?.type || 'default'

// 判断是否已解决或已关闭
const isResolved = computed(() => {
  const s = complaint.value?.status?.toUpperCase()
  return s === 'RESOLVED' || s === 'CLOSED'
})

const fetchDetail = async () => {
  loading.value = true
  try {
    const res = await getStaffComplaintDetail(route.params.id)
    complaint.value = res.data
    // 根据当前状态设置下拉框默认值
    const currentStatus = complaint.value.status?.toUpperCase()
    if (currentStatus === 'PENDING') {
      newStatus.value = 'PROCESSING'
    } else if (currentStatus === 'PROCESSING') {
      newStatus.value = 'PROCESSING'
    } else {
      newStatus.value = 'RESOLVED'
    }
  } catch (e) {
    window.$message?.error('获取投诉详情失败')
  } finally {
    loading.value = false
  }
}

const handleUpdateStatus = async () => {
  updatingStatus.value = true
  try {
    await updateComplaintStatus(complaint.value.id, { status: newStatus.value })
    complaint.value.status = newStatus.value
    window.$message?.success('状态已更新')
  } catch (e) {
    window.$message?.error('更新失败')
  } finally {
    updatingStatus.value = false
  }
}

const handleSendMessage = async () => {
  if (!replyContent.value.trim()) return
  sending.value = true
  try {
    await addStaffComplaintMessage(complaint.value.id, { content: replyContent.value })
    window.$message?.success('回复成功')
    replyContent.value = ''
    fetchDetail()
  } catch (e) {
    window.$message?.error('发送失败')
  } finally {
    sending.value = false
  }
}

onMounted(() => {
  fetchDetail()
})
</script>

<style scoped>
.staff-complaint-detail-page {
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;
}
.header-row {
  display: flex;
  align-items: center;
  gap: 16px;
}
.complaint-content {
  color: #333;
  line-height: 1.8;
  white-space: pre-wrap;
}
.attachments {
  margin-top: 20px;
}
.attachments h4 {
  margin: 0 0 12px;
}
.status-section {
  margin-bottom: 20px;
}
.status-section h4 {
  margin: 0 0 12px;
}
.message-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 20px;
}
.message-item {
  padding: 12px 16px;
  border-radius: 8px;
  max-width: 80%;
}
.message-item.user {
  background: #f5f5f5;
  align-self: flex-start;
}
.message-item.staff {
  background: #e8f5e9;
  align-self: flex-end;
}
.msg-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.msg-sender {
  font-weight: 500;
  font-size: 13px;
}
.msg-time {
  font-size: 12px;
  color: #999;
  margin-left: auto;
}
.msg-content {
  font-size: 14px;
  line-height: 1.6;
}
.reply-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #eee;
}
.reply-section .n-button {
  align-self: flex-end;
}
</style>
