<template>
  <div class="coach-consultations-page">
    <n-card title="咨询管理">
      <n-spin :show="loading">
        <div v-if="consultations.length" class="consultation-list">
          <div v-for="item in consultations" :key="item.id" class="consultation-card" @click="openDetail(item)">
            <div class="consultation-header">
              <n-avatar v-if="false" size="small" />
              <span class="user-name">{{ item.username || item.userId }}</span>
              <span class="course-name">{{ item.courseTitle }}</span>
              <n-tag :type="item.status === 'OPEN' ? 'warning' : 'default'" size="small">
                {{ item.status === 'OPEN' ? '待回复' : '已关闭' }}
              </n-tag>
            </div>
            <p class="consultation-content">{{ item.consultationNo }}</p>
            <span class="consultation-time">{{ formatDate(item.updatedAt || item.createdAt) }}</span>
          </div>
        </div>
        <n-empty v-else description="暂无咨询" />
      </n-spin>

      <div class="pagination-wrap" v-if="total > 0">
        <n-pagination
          v-model:page="pagination.page"
          :page-size="pagination.pageSize"
          :item-count="total"
          @update:page="fetchConsultations"
        />
      </div>
    </n-card>

    <!-- 咨询详情弹窗 -->
    <n-modal v-model:show="showDetailModal" preset="card" title="咨询详情" style="width: 600px">
      <div v-if="currentConsultation">
        <div class="detail-header">
          <n-avatar v-if="false" />
          <div class="detail-info">
            <span class="user-name">{{ currentConsultation.username || currentConsultation.userId }}</span>
            <span class="course-name">咨询课程: {{ currentConsultation.courseTitle }}</span>
          </div>
        </div>
        
        <div class="message-list">
          <div
            v-for="msg in currentConsultation.messages"
            :key="msg.id"
            :class="['message-item', msg.senderRole === 'COACH' ? 'coach' : 'user']"
          >
            <div class="msg-sender">{{ getSenderName(msg) }}</div>
            <p class="msg-content">{{ msg.content }}</p>
            <span class="msg-time">{{ formatDate(msg.createdAt) }}</span>
          </div>
        </div>

        <div v-if="currentConsultation.status === 'OPEN'" class="reply-section">
          <n-input v-model:value="replyContent" type="textarea" placeholder="输入回复内容..." :rows="3" />
          <n-space>
            <n-button type="primary" :loading="replying" :disabled="!replyContent.trim()" @click="handleReply">
              发送回复
            </n-button>
            <n-button @click="handleClose">关闭咨询</n-button>
          </n-space>
        </div>
      </div>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getCoachConsultations, getCoachConsultationDetail, replyConsultation, closeConsultation } from '@/api/coach'
import dayjs from 'dayjs'

const loading = ref(false)
const consultations = ref([])
const total = ref(0)

const pagination = reactive({
  page: 1,
  pageSize: 10
})

const showDetailModal = ref(false)
const currentConsultation = ref(null)
const replyContent = ref('')
const replying = ref(false)

const formatDate = (date) => dayjs(date).format('YYYY-MM-DD HH:mm')

// 根据消息的senderRole获取发送者名称
const getSenderName = (msg) => {
  if (!currentConsultation.value) return '-'
  if (msg.senderRole === 'COACH') {
    return currentConsultation.value.coachUsername || '教练'
  } else {
    return currentConsultation.value.username || '用户'
  }
}

const fetchConsultations = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.pageSize
    }
    const res = await getCoachConsultations(params)
    consultations.value = res.data?.items || res.data?.list || res.data || []
    total.value = res.data?.total ?? consultations.value.length
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const openDetail = async (item) => {
  try {
    const res = await getCoachConsultationDetail(item.id)
    currentConsultation.value = res.data
    replyContent.value = ''
    showDetailModal.value = true
  } catch (e) {
    window.$message?.error('获取详情失败')
  }
}

const handleReply = async () => {
  if (!replyContent.value.trim()) return
  replying.value = true
  try {
    await replyConsultation(currentConsultation.value.id, { content: replyContent.value })
    window.$message?.success('回复成功')
    replyContent.value = ''
    // 刷新详情
    const res = await getCoachConsultationDetail(currentConsultation.value.id)
    currentConsultation.value = res.data
  } catch (e) {
    window.$message?.error('回复失败')
  } finally {
    replying.value = false
  }
}

const handleClose = async () => {
  try {
    const res = await closeConsultation(currentConsultation.value.id)
    window.$message?.success('咨询已关闭')
    currentConsultation.value = res.data
    fetchConsultations()
  } catch (e) {
    window.$message?.error('操作失败')
  }
}

onMounted(() => {
  fetchConsultations()
})
</script>

<style scoped>
.coach-consultations-page {
  padding: 20px;
}
.consultation-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.consultation-card {
  padding: 16px;
  border: 1px solid #eee;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.consultation-card:hover {
  border-color: #18a058;
}
.consultation-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.user-name {
  font-weight: 500;
}
.course-name {
  color: #666;
  font-size: 13px;
  margin-left: auto;
  margin-right: 10px;
}
.consultation-content {
  margin: 0 0 8px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.consultation-time {
  font-size: 12px;
  color: #999;
}
.detail-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}
.detail-info {
  display: flex;
  flex-direction: column;
}
.message-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 300px;
  overflow-y: auto;
  margin-bottom: 20px;
}
.message-item {
  padding: 10px 14px;
  border-radius: 8px;
  max-width: 80%;
}
.message-item.user {
  background: #f5f5f5;
  align-self: flex-start;
}
.message-item.coach {
  background: #e8f5e9;
  align-self: flex-end;
}
.msg-sender {
  font-weight: 500;
  font-size: 13px;
  margin-bottom: 4px;
}
.msg-content {
  margin: 0;
}
.msg-time {
  display: block;
  margin-top: 6px;
  font-size: 11px;
  color: #999;
}
.reply-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid #eee;
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
