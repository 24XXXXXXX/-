<template>
  <div class="admin-complaints">
    <n-card title="投诉管理">
      <n-space vertical :size="16">
        <n-space>
          <n-select v-model:value="filters.status" placeholder="状态" clearable style="width: 150px" :options="statusOptions" @update:value="handleSearch" />
          <n-select v-model:value="filters.type" placeholder="类型" clearable style="width: 150px" :options="typeOptions" @update:value="handleSearch" />
        </n-space>

        <n-data-table :columns="columns" :data="complaints" :loading="loading" :pagination="pagination" remote @update:page="handlePageChange" />
      </n-space>
    </n-card>

    <n-modal v-model:show="showDetailModal" preset="card" title="投诉详情" style="width: 700px">
      <template v-if="currentComplaint">
        <n-descriptions :column="2" bordered>
          <n-descriptions-item label="投诉人">{{ currentComplaint.username }}</n-descriptions-item>
          <n-descriptions-item label="投诉类型">{{ typeMap[currentComplaint.complaintType] }}</n-descriptions-item>
          <n-descriptions-item label="状态">
            <n-tag :type="statusMap[currentComplaint.status]?.type">{{ statusMap[currentComplaint.status]?.label }}</n-tag>
          </n-descriptions-item>
          <n-descriptions-item label="提交时间">{{ formatDate(currentComplaint.createdAt) }}</n-descriptions-item>
          <n-descriptions-item label="投诉内容" :span="2">{{ currentComplaint.content }}</n-descriptions-item>
          <n-descriptions-item v-if="currentComplaint.assignedStaffUsername" label="处理员工" :span="2">
            {{ currentComplaint.assignedStaffUsername }}
          </n-descriptions-item>
        </n-descriptions>

        <template v-if="currentComplaint.attachments?.length">
          <n-divider>附件</n-divider>
          <n-space>
            <n-image v-for="(img, idx) in currentComplaint.attachments" :key="idx" :src="img" width="100" height="100" object-fit="cover" />
          </n-space>
        </template>

        <n-divider />

        <template v-if="currentComplaint.status !== 'RESOLVED'">
          <n-space>
            <n-button
              v-if="currentComplaint.status === 'SUBMITTED' || currentComplaint.status === 'ASSIGNED'"
              type="info"
              :loading="updatingStatus"
              @click="handleUpdateStatus('IN_PROGRESS')"
            >
              开始处理
            </n-button>
            <n-button
              v-if="currentComplaint.status === 'IN_PROGRESS'"
              type="success"
              :loading="updatingStatus"
              @click="handleUpdateStatus('RESOLVED')"
            >
              标记已解决
            </n-button>
          </n-space>
        </template>

        <n-divider />

        <h4>处理进度</h4>
        <div class="message-list">
          <div
            v-for="msg in (currentComplaint.messages || [])"
            :key="msg.id"
            :class="['message-item', isStaffMessage(msg) ? 'staff' : 'user']"
          >
            <div class="msg-header">
              <n-avatar size="small">{{ (msg.senderUsername || '?').charAt(0).toUpperCase() }}</n-avatar>
              <span class="msg-sender">{{ msg.senderUsername || '-' }}</span>
              <span class="msg-time">{{ formatDate(msg.createdAt) }}</span>
            </div>
            <div class="msg-content">{{ msg.content }}</div>
          </div>
          <n-empty v-if="!currentComplaint.messages?.length" description="暂无处理记录" size="small" />
        </div>

        <div v-if="currentComplaint.status !== 'RESOLVED'" class="reply-section">
          <n-input v-model:value="replyContent" type="textarea" placeholder="追加说明..." :rows="3" />
          <n-button type="primary" :loading="sending" :disabled="!replyContent.trim()" @click="handleSendMessage">
            发送
          </n-button>
        </div>

        <template v-if="currentComplaint.status !== 'RESOLVED'">
          <n-divider />
          <n-form :model="assignForm" label-placement="left" label-width="80">
            <n-form-item label="指派员工">
              <n-select v-model:value="assignForm.staffId" placeholder="请选择员工" :options="staffOptions" style="width: 300px" />
            </n-form-item>
          </n-form>
          <n-space justify="end">
            <n-button type="primary" :loading="assigning" @click="handleAssign">确认指派</n-button>
          </n-space>
        </template>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, reactive, h, onMounted } from 'vue'
import { NButton, NTag, useMessage } from 'naive-ui'
import { getComplaints, getComplaintDetail, assignComplaint, addAdminComplaintMessage, updateAdminComplaintStatus } from '@/api/admin'
import { getUsers } from '@/api/admin'
 import dayjs from 'dayjs'

const message = useMessage()

const loading = ref(false)
const complaints = ref([])
const filters = reactive({ status: null, type: null })
const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })

const statusOptions = [
  { label: '待处理', value: 'SUBMITTED' },
  { label: '已指派', value: 'ASSIGNED' },
  { label: '处理中', value: 'IN_PROGRESS' },
  { label: '已解决', value: 'RESOLVED' }
]

const typeOptions = [
  { label: '场地问题', value: 'VENUE' },
  { label: '器材问题', value: 'EQUIPMENT' },
  { label: '课程问题', value: 'COURSE' },
  { label: '其他', value: 'OTHER' }
]

const statusMap = {
  SUBMITTED: { label: '待处理', type: 'warning' },
  ASSIGNED: { label: '已指派', type: 'info' },
  IN_PROGRESS: { label: '处理中', type: 'info' },
  RESOLVED: { label: '已解决', type: 'success' },
  CLOSED: { label: '已关闭', type: 'default' }
}

const typeMap = {
  VENUE: '场地问题',
  EQUIPMENT: '器材问题',
  COURSE: '课程问题',
  OTHER: '其他'
}

const formatDate = (date) => {
  if (!date) return '-'
  return dayjs(date).format('YYYY-MM-DD HH:mm')
}

const showDetailModal = ref(false)
const currentComplaint = ref(null)
const staffOptions = ref([])
const assignForm = reactive({ staffId: null })
const assigning = ref(false)

const replyContent = ref('')
const sending = ref(false)
const updatingStatus = ref(false)

const isStaffMessage = (msg) => {
  const role = msg?.senderRole
  return role === 'STAFF' || role === 'ADMIN'
}

const columns = [
  { title: 'ID', key: 'id', width: 80 },
  { title: '投诉人', key: 'username', render: (row) => row.username || row.userId || '-' },
  { title: '类型', key: 'complaintType', render: (row) => typeMap[row.complaintType] || row.complaintType },
  { title: '内容', key: 'content', ellipsis: { tooltip: true } },
  {
    title: '状态',
    key: 'status',
    render: (row) => h(NTag, { type: statusMap[row.status]?.type }, () => statusMap[row.status]?.label)
  },
  { title: '处理员工', key: 'assignedStaffUsername', render: (row) => row.assignedStaffUsername || '-' },
  { title: '提交时间', key: 'createdAt', width: 180, render: (row) => formatDate(row.createdAt) },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    render: (row) => h(NButton, { size: 'small', onClick: () => openDetail(row) }, () => '查看')
  }
]

const fetchComplaints = async () => {
  loading.value = true
  try {
    const res = await getComplaints({
      page: pagination.page,
      size: pagination.pageSize,
      status: filters.status || undefined,
      complaintType: filters.type || undefined
    })
    complaints.value = res.data?.items || res.data?.content || []
    pagination.itemCount = res.data?.total ?? res.data?.totalElements ?? 0
  } catch (e) {
    message.error('获取投诉列表失败')
  } finally {
    loading.value = false
  }
}

const fetchStaff = async () => {
  try {
    const res = await getUsers({ role: 'ROLE_STAFF', size: 100 })
    const rows = res.data?.items || res.data?.content || []
    staffOptions.value = rows.map(u => ({
      label: u.nickname || u.username,
      value: u.id
    }))
  } catch (e) {
    console.error(e)
  }
}

const handleSearch = () => {
  pagination.page = 1
  fetchComplaints()
}

const handlePageChange = (page) => {
  pagination.page = page
  fetchComplaints()
}

const openDetail = async (row) => {
  try {
    const res = await getComplaintDetail(row.id)
    currentComplaint.value = res.data
    assignForm.staffId = null
    replyContent.value = ''
    showDetailModal.value = true
  } catch (e) {
    message.error('获取投诉详情失败')
  }
}

const refreshCurrentDetail = async () => {
  if (!currentComplaint.value?.id) return
  const res = await getComplaintDetail(currentComplaint.value.id)
  currentComplaint.value = res.data
}

const handleSendMessage = async () => {
  if (!currentComplaint.value?.id) return
  if (!replyContent.value.trim()) return
  sending.value = true
  try {
    await addAdminComplaintMessage(currentComplaint.value.id, { content: replyContent.value })
    replyContent.value = ''
    await refreshCurrentDetail()
    message.success('发送成功')
  } catch (e) {
    message.error(e?.response?.data?.message || '发送失败')
  } finally {
    sending.value = false
  }
}

const handleUpdateStatus = async (status) => {
  if (!currentComplaint.value?.id) return
  updatingStatus.value = true
  try {
    await updateAdminComplaintStatus(currentComplaint.value.id, { status })
    await refreshCurrentDetail()
    message.success('状态更新成功')
    fetchComplaints()
  } catch (e) {
    message.error(e?.response?.data?.message || '状态更新失败')
  } finally {
    updatingStatus.value = false
  }
}

const handleAssign = async () => {
  if (!assignForm.staffId) {
    message.warning('请选择员工')
    return
  }
  assigning.value = true
  try {
    await assignComplaint(currentComplaint.value.id, { staffUserId: assignForm.staffId })
    message.success('指派成功')
    await refreshCurrentDetail()
    fetchComplaints()
  } catch (e) {
    message.error('指派失败')
  } finally {
    assigning.value = false
  }
}

onMounted(() => {
  fetchStaff()
  fetchComplaints()
})
</script>

<style scoped>
.admin-complaints { padding: 20px; }
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
  margin-left: auto;
  font-size: 12px;
  color: #999;
}
.reply-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
</style>
