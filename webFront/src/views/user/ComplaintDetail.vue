<template>
  <div class="complaint-detail-page">
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
            <n-tag :type="getTypeTagType(complaint.complaintType)" size="small">{{ getTypeText(complaint.complaintType) }}</n-tag>
          </n-descriptions-item>
          <n-descriptions-item label="提交时间">{{ formatDate(complaint.createdAt) }}</n-descriptions-item>
          <n-descriptions-item label="处理人">{{ complaint.assignedStaffUsername || '待分配' }}</n-descriptions-item>
        </n-descriptions>

        <n-divider />

        <p class="complaint-content">{{ complaint.content }}</p>

        <div v-if="attachments.length" class="attachments">
          <h4>附件</h4>
          <n-image-group>
            <n-space>
              <n-image
                v-for="(url, index) in attachments"
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

        <!-- 消息记录 -->
        <h4>处理进度</h4>
        <div class="message-list">
          <div v-for="msg in complaint.messages" :key="msg.id" :class="['message-item', isStaffMessage(msg) ? 'staff' : 'user']">
            <div class="msg-header">
              <n-avatar size="small">{{ (msg.senderUsername || '?').charAt(0).toUpperCase() }}</n-avatar>
              <span class="msg-sender">{{ msg.senderUsername || '-' }}</span>
              <span class="msg-time">{{ formatDate(msg.createdAt) }}</span>
            </div>
            <div class="msg-content">{{ msg.content }}</div>
          </div>
          <n-empty v-if="!complaint.messages?.length" description="暂无处理记录" size="small" />
        </div>

        <!-- 追加消息 -->
        <div v-if="complaint.status !== 'RESOLVED'" class="reply-section">
          <n-input
            v-model:value="replyContent"
            type="textarea"
            placeholder="追加说明..."
            :rows="3"
          />
          <n-button type="primary" :loading="sending" :disabled="!replyContent.trim()" @click="handleSendMessage">
            发送
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
import { getComplaintDetail, addComplaintMessage } from '@/api/complaint'
import dayjs from 'dayjs'

// 用户端「投诉详情」页面
//
// 页面职责：
// - 展示投诉单基本信息（编号、类型、提交时间、处理人、当前状态）
// - 展示用户提交的附件（图片预览）
// - 展示处理进度/沟通记录（messages 时间线/气泡）
// - 在投诉未解决（status !== 'RESOLVED'）时允许用户追加说明（addComplaintMessage）
//
// 数据流：
// onMounted -> fetchDetail -> getComplaintDetail(id) -> complaint
// - 追加消息：handleSendMessage -> addComplaintMessage -> fetchDetail 刷新
//
// 权限边界：
// - 本页需要登录
// - 只能查看/追加自己投诉的资源级校验由后端保证

const route = useRoute()
const loading = ref(false)
const complaint = ref(null)
const replyContent = ref('')
const sending = ref(false)

const formatDate = (date) => dayjs(date).format('YYYY-MM-DD HH:mm')

const typeMap = {
  VENUE: { text: '场地问题', type: 'info' },
  EQUIPMENT: { text: '器材问题', type: 'warning' },
  COURSE: { text: '课程问题', type: 'primary' },
  OTHER: { text: '其他问题', type: 'default' }
}

// typeMap：投诉类型码 -> 文案/Tag 类型（仅用于展示）。

const getTypeText = (type) => typeMap[type]?.text || type
const getTypeTagType = (type) => typeMap[type]?.type || 'default'

const statusMap = {
  SUBMITTED: { text: '待处理', type: 'warning' },
  ASSIGNED: { text: '已指派', type: 'info' },
  IN_PROGRESS: { text: '处理中', type: 'info' },
  RESOLVED: { text: '已解决', type: 'success' }
}

// statusMap：投诉状态机展示映射。
// 注意：状态推进一般由员工端/管理员端操作，本页不直接改变状态。

const getStatusText = (status) => statusMap[status]?.text || status
const getStatusType = (status) => statusMap[status]?.type || 'default'

const normalizeUrl = (u) => {
  // 附件 URL 归一化：
  // - 兼容完整 URL / 以 / 开头的绝对路径
  // - 兼容后端返回 upload/xxx 的相对路径（补前导 /）
  // - 其它异常值丢弃，避免 n-image 报错
  if (!u || typeof u !== 'string') return null
  const s = u.trim()
  if (!s) return null
  if (s.startsWith('http://') || s.startsWith('https://') || s.startsWith('/')) return s
  if (s.startsWith('upload/')) return `/${s}`
  return null
}

const attachments = computed(() => {
  // 把 complaint.attachments 统一转换为可预览的 URL 数组。
  const arr = complaint.value?.attachments
  if (!Array.isArray(arr)) return []
  return arr.map(normalizeUrl).filter(Boolean)
})

const isStaffMessage = (msg) => {
  // 判断消息是否来自工作人员：
  // - STAFF/ADMIN 在模板里使用不同 class（staff/user）来决定对话气泡样式
  const role = msg?.senderRole
  return role === 'STAFF' || role === 'ADMIN'
}

const fetchDetail = async () => {
  // 拉取投诉详情：
  // - id 来自路由 params
  // - 返回 complaint 及其 messages/attachments 等
  loading.value = true
  try {
    const res = await getComplaintDetail(route.params.id)
    complaint.value = res.data
  } catch (e) {
    window.$message?.error('获取投诉详情失败')
  } finally {
    loading.value = false
  }
}

const handleSendMessage = async () => {
  // 追加说明：
  // - UI 层已通过 v-if(complaint.status !== 'RESOLVED') 控制是否展示输入区
  // - 成功后清空输入并刷新详情（让消息列表与状态保持一致）
  if (!replyContent.value.trim()) return
  sending.value = true
  try {
    await addComplaintMessage(complaint.value.id, { content: replyContent.value })
    window.$message?.success('发送成功')
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
.complaint-detail-page {
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
  background: #e8f5e9;
  align-self: flex-end;
}
.message-item.staff {
  background: #f5f5f5;
  align-self: flex-start;
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
