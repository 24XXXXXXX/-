<template>
  <div class="admin-coach-applications">
    <n-card title="教练认证审核">
      <n-space vertical :size="16">
        <n-space>
          <n-select v-model:value="filters.status" placeholder="状态" clearable style="width: 150px" :options="statusOptions" @update:value="handleSearch" />
        </n-space>

        <n-data-table :columns="columns" :data="applications" :loading="loading" :pagination="pagination" remote @update:page="handlePageChange" />
      </n-space>
    </n-card>

    <n-modal v-model:show="showDetailModal" preset="card" title="申请详情" style="width: 700px">
      <template v-if="currentApp">
        <n-descriptions :column="2" bordered>
          <n-descriptions-item label="申请人">{{ currentApp.username || (currentApp.userId ? `UID:${currentApp.userId}` : '-') }}</n-descriptions-item>
          <n-descriptions-item label="专业领域">{{ currentApp.specialty || '-' }}</n-descriptions-item>
          <n-descriptions-item label="状态">{{ statusMap[currentApp.auditStatus || currentApp.status]?.label || (currentApp.auditStatus || currentApp.status) }}</n-descriptions-item>
          <n-descriptions-item label="申请时间">{{ currentApp.createdAt }}</n-descriptions-item>
          <n-descriptions-item label="自我介绍" :span="2">{{ currentApp.intro || currentApp.introduction || '-' }}</n-descriptions-item>
        </n-descriptions>

        <n-divider>资质证明</n-divider>
        <n-space v-if="currentApp.certFiles?.length">
          <n-image v-for="(img, idx) in currentApp.certFiles" :key="idx" :src="img" width="150" height="150" object-fit="cover" />
        </n-space>
        <n-empty v-else description="暂无资质证明" />

        <template v-if="(currentApp.auditStatus || currentApp.status) === 'PENDING'">
          <n-divider />
          <n-space justify="end">
            <n-button type="error" @click="showRejectModal = true">拒绝</n-button>
            <n-button type="primary" :loading="approving" @click="handleApprove">通过</n-button>
          </n-space>
        </template>
      </template>
    </n-modal>

    <n-modal v-model:show="showRejectModal" preset="dialog" title="拒绝原因">
      <n-input v-model:value="rejectReason" type="textarea" placeholder="请输入拒绝原因" :rows="3" />
      <template #action>
        <n-button @click="showRejectModal = false">取消</n-button>
        <n-button type="error" :loading="rejecting" @click="handleReject">确定拒绝</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, reactive, h, onMounted } from 'vue'
import { NButton, NTag, useMessage } from 'naive-ui'
import { getCoachApplications, approveCoachApplication, rejectCoachApplication } from '@/api/admin'

const message = useMessage()

const loading = ref(false)
const applications = ref([])
const filters = reactive({ status: null })
const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })

const statusOptions = [
  { label: '待审核', value: 'PENDING' },
  { label: '已通过', value: 'APPROVED' },
  { label: '已拒绝', value: 'REJECTED' }
]

const statusMap = {
  PENDING: { label: '待审核', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已拒绝', type: 'error' }
}

const showDetailModal = ref(false)
const currentApp = ref(null)
const approving = ref(false)

const showRejectModal = ref(false)
const rejectReason = ref('')
const rejecting = ref(false)

const columns = [
  { title: 'ID', key: 'id', width: 80 },
  { title: '申请人', key: 'username', render: (row) => row.username || (row.userId ? `UID:${row.userId}` : '-') },
  { title: '专业领域', key: 'specialty' },
  {
    title: '状态',
    key: 'auditStatus',
    render: (row) => h(NTag, { type: statusMap[row.auditStatus]?.type }, () => statusMap[row.auditStatus]?.label)
  },
  { title: '申请时间', key: 'createdAt', width: 180 },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    render: (row) => h(NButton, { size: 'small', onClick: () => openDetail(row) }, () => '查看')
  }
]

const fetchApplications = async () => {
  loading.value = true
  try {
    const res = await getCoachApplications({
      page: pagination.page,
      size: pagination.pageSize,
      status: filters.status || undefined
    })
    applications.value = res.data?.items || res.data?.content || []
    pagination.itemCount = res.data?.total ?? res.data?.totalElements ?? 0
  } catch (e) {
    message.error('获取申请列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  fetchApplications()
}

const handlePageChange = (page) => {
  pagination.page = page
  fetchApplications()
}

const openDetail = (row) => {
  currentApp.value = row
  showDetailModal.value = true
}

const handleApprove = async () => {
  approving.value = true
  try {
    await approveCoachApplication(currentApp.value.id)
    message.success('审核通过')
    showDetailModal.value = false
    fetchApplications()
  } catch (e) {
    message.error('操作失败')
  } finally {
    approving.value = false
  }
}

const handleReject = async () => {
  if (!rejectReason.value.trim()) {
    message.warning('请输入拒绝原因')
    return
  }
  rejecting.value = true
  try {
    await rejectCoachApplication(currentApp.value.id, { remark: rejectReason.value })
    message.success('已拒绝')
    showRejectModal.value = false
    showDetailModal.value = false
    rejectReason.value = ''
    fetchApplications()
  } catch (e) {
    message.error('操作失败')
  } finally {
    rejecting.value = false
  }
}

onMounted(() => fetchApplications())
</script>

<style scoped>
.admin-coach-applications { padding: 20px; }
</style>
