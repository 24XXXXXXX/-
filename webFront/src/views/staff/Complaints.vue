<template>
  <div class="staff-complaints-page">
    <n-card title="投诉处理">
      <n-tabs v-model:value="activeTab" @update:value="handleTabChange">
        <n-tab-pane name="ASSIGNED" tab="待处理" />
        <n-tab-pane name="IN_PROGRESS" tab="处理中" />
        <n-tab-pane name="RESOLVED" tab="已解决" />
      </n-tabs>

      <n-spin :show="loading">
        <div v-if="complaints.length" class="complaint-list">
          <div
            v-for="complaint in complaints"
            :key="complaint.id"
            class="complaint-item"
            @click="goDetail(complaint.id)"
          >
            <div class="complaint-header">
              <n-tag :type="getTypeTagType(complaint.complaintType)" size="small">{{ getTypeText(complaint.complaintType) }}</n-tag>
              <span class="complaint-time">{{ formatDate(complaint.createdAt) }}</span>
              <n-tag :type="getStatusType(complaint.status)">{{ getStatusText(complaint.status) }}</n-tag>
            </div>
            <h4 class="complaint-title">{{ complaint.complaintNo }}</h4>
            <div class="complaint-user">
              <n-avatar size="small">{{ (complaint.username || '').slice(0, 1) }}</n-avatar>
              <span>{{ complaint.username }}</span>
            </div>
          </div>
        </div>
        <n-empty v-else description="暂无投诉" />
      </n-spin>

      <div class="pagination-wrap" v-if="total > 0">
        <n-pagination
          v-model:page="pagination.page"
          :page-size="pagination.pageSize"
          :item-count="total"
          @update:page="fetchComplaints"
        />
      </div>
    </n-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getStaffComplaints } from '@/api/staff'
import dayjs from 'dayjs'

const router = useRouter()
const loading = ref(false)
const complaints = ref([])
const total = ref(0)
const activeTab = ref('ASSIGNED')

const pagination = reactive({
  page: 1,
  pageSize: 10
})

const formatDate = (date) => dayjs(date).format('YYYY-MM-DD HH:mm')

const typeMap = {
  VENUE: { text: '场地问题', type: 'info' },
  EQUIPMENT: { text: '器材问题', type: 'warning' },
  COURSE: { text: '课程问题', type: 'primary' },
  OTHER: { text: '其他问题', type: 'default' }
}

const getTypeText = (type) => typeMap[type]?.text || type
const getTypeTagType = (type) => typeMap[type]?.type || 'default'

const statusMap = {
  SUBMITTED: { text: '待处理', type: 'warning' },
  ASSIGNED: { text: '待处理', type: 'warning' },
  IN_PROGRESS: { text: '处理中', type: 'info' },
  RESOLVED: { text: '已解决', type: 'success' }
}

const getStatusText = (status) => statusMap[status]?.text || status
const getStatusType = (status) => statusMap[status]?.type || 'default'

const fetchComplaints = async () => {
  loading.value = true
  try {
    const params = {
      status: activeTab.value,
      page: pagination.page,
      size: pagination.pageSize
    }
    const res = await getStaffComplaints(params)
    complaints.value = res.data?.items || res.data?.list || res.data || []
    total.value = res.data?.total ?? complaints.value.length
  } catch (e) {
    console.error(e)
    complaints.value = []
    total.value = 0
    const msg = e?.response?.data?.message || e?.message || '获取投诉列表失败'
    window.$message?.error(msg)
  } finally {
    loading.value = false
  }
}

const handleTabChange = () => {
  pagination.page = 1
  fetchComplaints()
}

const goDetail = (id) => {
  router.push(`/staff/complaints/${id}`)
}

onMounted(() => {
  fetchComplaints()
})
</script>

<style scoped>
.staff-complaints-page {
  padding: 20px;
}
.complaint-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 20px;
}
.complaint-item {
  padding: 16px;
  border: 1px solid #eee;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.complaint-item:hover {
  border-color: #18a058;
}
.complaint-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.complaint-time {
  font-size: 12px;
  color: #999;
  margin-left: auto;
  margin-right: 10px;
}
.complaint-title {
  margin: 0 0 8px;
  font-size: 15px;
}
.complaint-content {
  margin: 0 0 10px;
  font-size: 14px;
  color: #666;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.complaint-user {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #666;
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
