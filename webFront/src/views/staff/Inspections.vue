<template>
  <div class="staff-inspections-page">
    <n-card title="巡检记录">
      <template #header-extra>
        <n-button type="primary" @click="$router.push('/staff/inspections/create')">上报巡检</n-button>
      </template>

      <n-spin :show="loading">
        <div v-if="inspections.length" class="inspection-list">
          <div v-for="item in inspections" :key="item.id" class="inspection-card" @click="openDetail(item)">
            <div class="inspection-header">
              <span class="inspection-time">{{ formatDate(item.createdAt) }}</span>
              <n-space align="center" :size="8">
                <n-tag :type="getStatusType(item.status)">{{ getStatusText(item.status) }}</n-tag>
                <n-button size="tiny" tertiary @click.stop="openDetail(item)">查看</n-button>
              </n-space>
            </div>
            <h4 class="inspection-title">{{ getTargetTypeText(item.targetType) }} · {{ getIssueTypeText(item.issueType) }}</h4>
            <p class="inspection-content">{{ item.content }}</p>
            <div class="inspection-location">
              <n-icon><LocationOutline /></n-icon>
              <span>{{ item.region || '-' }}</span>
            </div>
          </div>
        </div>
        <n-empty v-else description="暂无巡检记录">
          <template #extra>
            <n-button type="primary" @click="$router.push('/staff/inspections/create')">上报巡检</n-button>
          </template>
        </n-empty>
      </n-spin>

      <div class="pagination-wrap" v-if="total > 0">
        <n-pagination
          v-model:page="pagination.page"
          :page-size="pagination.pageSize"
          :item-count="total"
          @update:page="fetchInspections"
        />
      </div>
    </n-card>

    <n-modal v-model:show="showDetail" preset="card" title="巡检详情" style="width: 800px">
      <n-spin :show="detailLoading">
        <template v-if="current">
          <n-descriptions :column="2" bordered>
            <n-descriptions-item label="上报人">{{ current.staffUsername || current.staffUserId }}</n-descriptions-item>
            <n-descriptions-item label="区域">{{ current.region || '-' }}</n-descriptions-item>
            <n-descriptions-item label="对象">{{ getTargetTypeText(current.targetType) }}</n-descriptions-item>
            <n-descriptions-item label="问题类型">{{ getIssueTypeText(current.issueType) }}</n-descriptions-item>
            <n-descriptions-item label="状态">
              <n-tag :type="getStatusType(current.status)">{{ getStatusText(current.status) }}</n-tag>
            </n-descriptions-item>
            <n-descriptions-item label="上报时间">{{ formatDateTime(current.createdAt) }}</n-descriptions-item>
            <n-descriptions-item label="更新时间">{{ formatDateTime(current.updatedAt) }}</n-descriptions-item>
            <n-descriptions-item label="解决时间">{{ formatDateTime(current.resolvedAt) }}</n-descriptions-item>
            <n-descriptions-item label="内容" :span="2">{{ current.content }}</n-descriptions-item>
          </n-descriptions>

          <template v-if="current.attachments?.length">
            <n-divider>附件</n-divider>
            <n-space>
              <n-image
                v-for="(img, idx) in current.attachments"
                :key="idx"
                :src="img"
                width="120"
                height="120"
                object-fit="cover"
              />
            </n-space>
          </template>
        </template>
      </n-spin>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { LocationOutline } from '@vicons/ionicons5'
import { useMessage } from 'naive-ui'
import { getInspections, getInspectionDetail } from '@/api/staff'
import dayjs from 'dayjs'

const message = useMessage()

const loading = ref(false)
const inspections = ref([])
const total = ref(0)

const showDetail = ref(false)
const current = ref(null)
const detailLoading = ref(false)

const pagination = reactive({
  page: 1,
  pageSize: 10
})

const formatDate = (date) => dayjs(date).format('YYYY-MM-DD HH:mm')
const formatDateTime = (date) => (date ? dayjs(date).format('YYYY-MM-DD HH:mm') : '-')

const targetTypeMap = {
  VENUE: '场地',
  EQUIPMENT: '器材',
  OTHER: '其他'
}
const issueTypeMap = {
  MAINTENANCE: '维护',
  REPAIR: '维修',
  SHORTAGE: '缺少',
  DAMAGE: '损坏',
  OTHER: '其他'
}

const getTargetTypeText = (t) => targetTypeMap[t] || t || '-'
const getIssueTypeText = (t) => issueTypeMap[t] || t || '-'

const statusMap = {
  pending: { text: '待处理', type: 'warning' },
  PENDING: { text: '待处理', type: 'warning' },
  submitted: { text: '已提交', type: 'info' },
  SUBMITTED: { text: '已提交', type: 'info' },
  processing: { text: '处理中', type: 'info' },
  PROCESSING: { text: '处理中', type: 'info' },
  in_progress: { text: '处理中', type: 'info' },
  IN_PROGRESS: { text: '处理中', type: 'info' },
  resolved: { text: '已处理', type: 'success' },
  RESOLVED: { text: '已处理', type: 'success' },
  resolve: { text: '已处理', type: 'success' },
  RESOLVE: { text: '已处理', type: 'success' }
}

const getStatusText = (status) => statusMap[status]?.text || statusMap[status?.toLowerCase()]?.text || status
const getStatusType = (status) => statusMap[status]?.type || statusMap[status?.toLowerCase()]?.type || 'default'

const fetchInspections = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.pageSize
    }
    const res = await getInspections(params)
    const data = res?.data
    if (Array.isArray(data)) {
      inspections.value = data
      total.value = data.length
    } else {
      const rows = data?.items || data?.list || data?.records || []
      inspections.value = Array.isArray(rows) ? rows : []
      total.value = data?.total ?? inspections.value.length
    }
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const openDetail = async (row) => {
  if (!row?.id) return
  showDetail.value = true
  current.value = null
  detailLoading.value = true
  try {
    const res = await getInspectionDetail(row.id)
    current.value = res.data
  } catch (e) {
    message.error('获取巡检详情失败')
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => {
  fetchInspections()
})
</script>

<style scoped>
.staff-inspections-page {
  padding: 20px;
}
.inspection-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.inspection-card {
  padding: 16px;
  border: 1px solid #eee;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.inspection-card:hover {
  border-color: #d9d9d9;
}
.inspection-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.inspection-time {
  font-size: 13px;
  color: #999;
}
.inspection-title {
  margin: 0 0 8px;
  font-size: 15px;
}
.inspection-content {
  margin: 0 0 10px;
  font-size: 14px;
  color: #666;
}
.inspection-location {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #999;
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
