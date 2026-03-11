<template>
  <div class="user-consultations-page">
    <n-card title="我的咨询">
      <template #header-extra>
        <n-button type="primary" @click="showCreateModal = true">发起咨询</n-button>
      </template>

      <n-space style="margin-bottom: 12px">
        <n-select
          v-model:value="filterStatus"
          :options="statusOptions"
          placeholder="状态筛选"
          clearable
          style="width: 140px"
          @update:value="handleFilter"
        />
      </n-space>

      <n-spin :show="loading">
        <div v-if="consultations.length" class="consultation-list">
          <div v-for="item in consultations" :key="item.id" class="consultation-card" @click="openDetail(item)">
            <div class="consultation-header">
              <span class="course-name">{{ item.courseTitle }}</span>
              <n-tag :type="item.status === 'OPEN' ? 'warning' : 'default'" size="small">
                {{ item.status === 'OPEN' ? '进行中' : '已关闭' }}
              </n-tag>
            </div>
            <div class="consultation-meta">
              <span class="coach-name">教练：{{ item.coachUsername || '-' }}</span>
              <span class="consultation-time">{{ formatDate(item.updatedAt || item.createdAt) }}</span>
            </div>
            <div class="consultation-no">{{ item.consultationNo }}</div>
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

    <n-modal v-model:show="showCreateModal" preset="card" title="发起咨询" style="width: 520px">
      <n-form :model="createForm" label-placement="left" label-width="90">
        <n-form-item label="课程ID">
          <n-input-number v-model:value="createForm.courseId" :min="1" style="width: 100%" placeholder="请输入课程ID（可从课程详情页进入自动填充）" />
        </n-form-item>
        <n-form-item label="内容">
          <n-input v-model:value="createForm.content" type="textarea" :rows="4" placeholder="请输入咨询内容" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showCreateModal = false">取消</n-button>
          <n-button type="primary" :loading="creating" :disabled="!canCreate" @click="handleCreate">提交</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal v-model:show="showDetailModal" preset="card" title="咨询详情" style="width: 640px">
      <div v-if="current">
        <div class="detail-header">
          <div class="detail-info">
            <div class="detail-row">
              <span class="label">课程</span>
              <span class="value">{{ current.courseTitle }}</span>
            </div>
            <div class="detail-row">
              <span class="label">教练</span>
              <span class="value">{{ current.coachUsername || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="label">状态</span>
              <n-tag :type="current.status === 'OPEN' ? 'warning' : 'default'" size="small">
                {{ current.status === 'OPEN' ? '进行中' : '已关闭' }}
              </n-tag>
            </div>
          </div>
        </div>

        <div class="message-list">
          <div
            v-for="msg in current.messages"
            :key="msg.id"
            :class="['message-item', msg.senderRole === 'USER' ? 'user' : 'coach']"
          >
            <div class="msg-sender">{{ getSenderName(msg) }}</div>
            <p class="msg-content">{{ msg.content }}</p>
            <span class="msg-time">{{ formatDate(msg.createdAt) }}</span>
          </div>
          <n-empty v-if="!current.messages?.length" description="暂无消息" size="small" />
        </div>

        <div v-if="current.status === 'OPEN'" class="reply-section">
          <n-input v-model:value="replyContent" type="textarea" placeholder="追加消息..." :rows="3" />
          <n-button type="primary" :loading="sending" :disabled="!replyContent.trim()" @click="handleSendMessage">发送</n-button>
        </div>
      </div>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import dayjs from 'dayjs'
import { createConsultation, getMyConsultations, getConsultationDetail, sendConsultationMessage } from '@/api/course'

// 用户端「我的咨询」页面（课程咨询/问答会话）
//
// 页面职责：
// - 展示当前用户发起的咨询会话列表（分页）
// - 支持按状态筛选：OPEN（进行中）/CLOSED（已关闭）
// - 支持查看会话详情：展示双方消息流（USER/COACH）
// - 在会话 OPEN 时允许继续追加消息（sendConsultationMessage）
// - 支持发起新咨询（createConsultation），并在创建成功后可直接打开详情
//
// 数据流：
// - 列表：fetchConsultations -> getMyConsultations({page,size,status}) -> consultations/total
// - 详情：openDetail -> getConsultationDetail(id) -> current.messages 渲染
// - 发送消息：sendConsultationMessage -> 重新拉取详情 + 刷新列表（让 updatedAt/状态等同步）
//
// 路由联动：
// - 允许从课程详情页带 courseId 进入：/user/consultations?courseId=xx（自动打开“发起咨询”弹窗并预填）
// - 允许创建咨询后带 consultationId 直达详情：/user/consultations?consultationId=xx
//
// 权限边界：
// - 相关接口都需要登录
// - “只能查看/操作自己的会话”的资源级校验由后端保证

const route = useRoute()

const loading = ref(false)
const consultations = ref([])
const total = ref(0)

const pagination = reactive({
  page: 1,
  pageSize: 10
})

const filterStatus = ref(null)
const statusOptions = [
  { label: '进行中', value: 'OPEN' },
  { label: '已关闭', value: 'CLOSED' }
]

const showDetailModal = ref(false)
const current = ref(null)
const replyContent = ref('')
const sending = ref(false)

const showCreateModal = ref(false)
const creating = ref(false)
const createForm = reactive({
  courseId: null,
  content: ''
})

const canCreate = computed(() => {
  // canCreate：创建咨询的最小校验。
  // - 课程 ID 必须为正数
  // - 内容不能为空（trim 后）
  return Number.isFinite(Number(createForm.courseId)) && Number(createForm.courseId) > 0 && !!createForm.content.trim()
})

const formatDate = (d) => dayjs(d).format('YYYY-MM-DD HH:mm')

// 根据消息的senderRole获取发送者名称
const getSenderName = (msg) => {
  // senderRole 由后端标记消息发送方角色：USER/COACH。
  // 这里用于把消息列表的“发送者”展示为更友好的名称。
  if (!current.value) return '-'
  if (msg.senderRole === 'COACH') {
    return current.value.coachUsername || '教练'
  } else {
    return current.value.username || '我'
  }
}

const fetchConsultations = async () => {
  // 拉取咨询会话列表：
  // - 分页参数 page/size
  // - status 可为空（不过滤）
  // - 返回结构兼容 items/list/直接数组
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.pageSize,
      status: filterStatus.value || undefined
    }
    const res = await getMyConsultations(params)
    consultations.value = res.data?.items || res.data?.list || res.data || []
    total.value = res.data?.total ?? consultations.value.length
  } catch (e) {
    window.$message?.error(e?.response?.data?.message || '获取咨询列表失败')
  } finally {
    loading.value = false
  }
}

const handleFilter = () => {
  pagination.page = 1
  fetchConsultations()
}

const openDetail = async (item) => {
  // 打开会话详情弹窗：
  // - 会触发一次 getConsultationDetail 拉取消息流
  // - replyContent 清空，避免把上次输入带到下一次会话
  try {
    const res = await getConsultationDetail(item.id)
    current.value = res.data
    replyContent.value = ''
    showDetailModal.value = true
  } catch (e) {
    window.$message?.error(e?.response?.data?.message || '获取详情失败')
  }
}

const handleSendMessage = async () => {
  // 追加消息：
  // - 仅当 current.status === 'OPEN' 时在 UI 展示输入区（见 template）
  // - 发送成功后：清空输入框，并刷新详情与列表
  if (!current.value?.id) return
  if (!replyContent.value.trim()) return
  sending.value = true
  try {
    await sendConsultationMessage(current.value.id, { content: replyContent.value })
    replyContent.value = ''
    const res = await getConsultationDetail(current.value.id)
    current.value = res.data
    fetchConsultations()
  } catch (e) {
    window.$message?.error(e?.response?.data?.message || '发送失败')
  } finally {
    sending.value = false
  }
}

const handleCreate = async () => {
  // 发起咨询：
  // - createForm.courseId 由用户输入，或从路由 query 自动预填
  // - 创建成功后刷新列表
  // - 如果后端返回新会话 id，则进一步拉取详情并直接打开详情弹窗
  if (!canCreate.value) return
  creating.value = true
  try {
    const res = await createConsultation({
      courseId: Number(createForm.courseId),
      content: createForm.content
    })
    window.$message?.success('咨询已创建')
    showCreateModal.value = false
    createForm.content = ''
    // 创建成功后打开详情
    const id = res?.data?.id
    if (id) {
      const d = await getConsultationDetail(id)
      current.value = d.data
      replyContent.value = ''
      showDetailModal.value = true
    }
    fetchConsultations()
  } catch (e) {
    window.$message?.error(e?.response?.data?.message || '创建失败')
  } finally {
    creating.value = false
  }
}

onMounted(() => {
  // 支持从其它页面带参数进入：
  // - courseId：直接打开“发起咨询”弹窗，并预填课程 ID
  // - consultationId：直接拉取并打开会话详情
  // 允许从课程详情页带 courseId 过来：/user/consultations?courseId=xx
  const cid = Number(route.query?.courseId)
  if (Number.isFinite(cid) && cid > 0) {
    createForm.courseId = cid
    showCreateModal.value = true
  }

  // 允许创建咨询后直接跳进详情：/user/consultations?consultationId=xx
  const consultId = Number(route.query?.consultationId)
  if (Number.isFinite(consultId) && consultId > 0) {
    getConsultationDetail(consultId)
      .then((res) => {
        current.value = res.data
        replyContent.value = ''
        showDetailModal.value = true
      })
      .catch(() => {
      })
  }

  fetchConsultations()
})
</script>

<style scoped>
.user-consultations-page {
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;
}

.consultation-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.consultation-card {
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 14px 16px;
  cursor: pointer;
  transition: all 0.2s;
}

.consultation-card:hover {
  border-color: #18a058;
}

.consultation-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.course-name {
  font-weight: 600;
}

.consultation-meta {
  margin-top: 8px;
  display: flex;
  gap: 16px;
  color: #666;
  font-size: 13px;
}

.consultation-time {
  margin-left: auto;
  color: #999;
}

.consultation-no {
  margin-top: 6px;
  color: #999;
  font-size: 12px;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

.detail-header {
  margin-bottom: 12px;
}

.detail-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.detail-row .label {
  width: 48px;
  color: #666;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 12px;
  margin-bottom: 12px;
}

.message-item {
  padding: 10px 12px;
  border-radius: 8px;
  max-width: 85%;
}

.message-item.user {
  align-self: flex-end;
  background: #e8f5e9;
}

.message-item.coach {
  align-self: flex-start;
  background: #f5f5f5;
}

.msg-sender {
  font-weight: 500;
  font-size: 13px;
  margin-bottom: 4px;
}

.msg-content {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.6;
}

.msg-time {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: #999;
}

.reply-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px solid #eee;
}

.reply-section .n-button {
  align-self: flex-end;
}
</style>
