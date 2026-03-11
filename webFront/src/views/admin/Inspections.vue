<template>
  <div class="admin-inspections">
    <n-card title="巡检管理">
      <n-space vertical :size="16">
        <n-space align="center">
          <n-select v-model:value="filters.status" placeholder="状态" clearable style="width: 130px" :options="statusOptions" />
          <n-select v-model:value="filters.targetType" placeholder="对象" clearable style="width: 130px" :options="targetTypeOptions" />
          <n-input v-model:value="filters.region" placeholder="区域" clearable style="width: 160px" @keyup.enter="handleSearch" />
          <n-button type="primary" @click="handleSearch">搜索</n-button>
          <n-button @click="handleReset">重置</n-button>
        </n-space>

        <n-data-table
          :columns="columns"
          :data="rows"
          :loading="loading"
          :pagination="pagination"
          remote
          @update:page="handlePageChange"
        />
      </n-space>
    </n-card>

    <n-modal v-model:show="showDetail" preset="card" title="巡检详情" style="width: 800px">
      <template v-if="current">
        <n-descriptions :column="2" bordered>
          <n-descriptions-item label="上报人">{{ current.staffUsername || current.staffUserId }}</n-descriptions-item>
          <n-descriptions-item label="区域">{{ current.region || '-' }}</n-descriptions-item>
          <n-descriptions-item label="对象">{{ getTargetTypeText(current.targetType) }}</n-descriptions-item>
          <n-descriptions-item label="问题类型">{{ getIssueTypeText(current.issueType) }}</n-descriptions-item>
          <n-descriptions-item label="状态">
            <n-tag :type="statusMap[current.status]?.type">{{ statusMap[current.status]?.text }}</n-tag>
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

        <n-divider />

        <n-space>
          <n-button
            v-if="current.status === 'SUBMITTED'"
            type="info"
            :loading="updating"
            @click="updateStatus('IN_PROGRESS')"
          >
            开始处理
          </n-button>
          <n-button
            v-if="current.status !== 'RESOLVED'"
            type="success"
            :loading="updating"
            @click="updateStatus('RESOLVED')"
          >
            标记已解决
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, reactive, h, onMounted } from 'vue'
import { NButton, NTag, useMessage } from 'naive-ui'
import dayjs from 'dayjs'
import { getInspections, getInspectionDetail, updateInspectionStatus } from '@/api/admin'

const message = useMessage()

const loading = ref(false)
const rows = ref([])

const filters = reactive({
  status: null,
  targetType: null,
  region: ''
})

const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })

const showDetail = ref(false)
const current = ref(null)
const updating = ref(false)

const statusOptions = [
  { label: '待处理', value: 'SUBMITTED' },
  { label: '处理中', value: 'IN_PROGRESS' },
  { label: '已解决', value: 'RESOLVED' }
]

const targetTypeOptions = [
  { label: '场地', value: 'VENUE' },
  { label: '器材', value: 'EQUIPMENT' },
  { label: '其他', value: 'OTHER' }
]

const statusMap = {
  SUBMITTED: { text: '待处理', type: 'warning' },
  IN_PROGRESS: { text: '处理中', type: 'info' },
  RESOLVED: { text: '已解决', type: 'success' }
}

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

const formatDateTime = (t) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-')

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getInspections({
      page: pagination.page,
      size: pagination.pageSize,
      status: filters.status || undefined,
      targetType: filters.targetType || undefined,
      region: filters.region?.trim() || undefined
    })

    rows.value = res.data?.items || res.data?.list || res.data || []
    pagination.itemCount = res.data?.total ?? rows.value.length
  } catch (e) {
    message.error('获取巡检列表失败')
  } finally {
    loading.value = false
  }
}

const openDetail = async (row) => {
  showDetail.value = true
  current.value = null
  try {
    const res = await getInspectionDetail(row.id)
    current.value = res.data
  } catch (e) {
    message.error('获取巡检详情失败')
  }
}

const updateStatus = async (status) => {
  if (!current.value?.id) return
  updating.value = true
  try {
    const res = await updateInspectionStatus(current.value.id, { status })
    current.value = res.data
    message.success('操作成功')
    fetchList()
  } catch (e) {
    message.error(e?.response?.data?.message || '操作失败')
  } finally {
    updating.value = false
  }
}

const columns = [
  { title: 'ID', key: 'id', width: 70 },
  { title: '上报人', key: 'staffUsername', render: (r) => r.staffUsername || r.staffUserId },
  { title: '区域', key: 'region', width: 120 },
  { title: '对象', key: 'targetType', width: 100, render: (r) => getTargetTypeText(r.targetType) },
  { title: '问题类型', key: 'issueType', width: 100, render: (r) => getIssueTypeText(r.issueType) },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render: (r) => h(NTag, { type: statusMap[r.status]?.type || 'default' }, () => statusMap[r.status]?.text || r.status)
  },
  { title: '上报时间', key: 'createdAt', width: 170, render: (r) => formatDateTime(r.createdAt) },
  {
    title: '操作',
    key: 'actions',
    width: 120,
    render: (row) => h(NButton, { size: 'small', onClick: () => openDetail(row) }, () => '查看')
  }
]

const handleSearch = () => {
  pagination.page = 1
  fetchList()
}

const handleReset = () => {
  filters.status = null
  filters.targetType = null
  filters.region = ''
  pagination.page = 1
  fetchList()
}

const handlePageChange = (page) => {
  pagination.page = page
  fetchList()
}

onMounted(() => {
  fetchList()
})
</script>
