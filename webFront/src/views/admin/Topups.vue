<template>
  <div class="admin-topups">
    <n-card title="充值审核">
      <n-space vertical :size="16">
        <n-space>
          <n-select v-model:value="filters.status" placeholder="状态" clearable style="width: 150px" :options="statusOptions" @update:value="handleSearch" />
        </n-space>

        <n-data-table :columns="columns" :data="topups" :loading="loading" :pagination="pagination" remote @update:page="handlePageChange" />
      </n-space>
    </n-card>

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
import { NButton, NSpace, NTag, useMessage, useDialog } from 'naive-ui'
import { getTopupRequests, approveTopup, rejectTopup } from '@/api/admin'

const message = useMessage()
const dialog = useDialog()

const loading = ref(false)
const topups = ref([])
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

const showRejectModal = ref(false)
const currentId = ref(null)
const rejectReason = ref('')
const rejecting = ref(false)

const columns = [
  { title: 'ID', key: 'id', width: 80 },
  { title: '申请单号', key: 'requestNo', width: 200, ellipsis: { tooltip: true } },
  { title: '用户', key: 'username', render: (row) => row.username || (row.userId != null ? `UID:${row.userId}` : '-') },
  { title: '充值金额', key: 'amount', render: (row) => (row.amount == null ? '-' : `¥${Number(row.amount).toFixed(2)}`) },
  { title: '备注', key: 'remark', ellipsis: { tooltip: true } },
  {
    title: '状态',
    key: 'status',
    render: (row) => h(NTag, { type: statusMap[row.status]?.type }, () => statusMap[row.status]?.label)
  },
  { title: '申请时间', key: 'requestedAt', width: 180 },
  {
    title: '处理',
    key: 'process',
    width: 180,
    render: (row) => {
      if (row.status === 'PENDING') {
        return h(NSpace, { size: 8 }, () => [
          h(NButton, { size: 'small', type: 'success', onClick: () => handleApprove(row) }, () => '通过'),
          h(NButton, { size: 'small', type: 'error', onClick: () => openRejectModal(row) }, () => '拒绝')
        ])
      }
      return row.processedAt || '-'
    }
  }
]

const fetchTopups = async () => {
  loading.value = true
  try {
    const res = await getTopupRequests({
      page: pagination.page,
      size: pagination.pageSize,
      status: filters.status || undefined
    })
    topups.value = res.data?.content || []
    pagination.itemCount = res.data?.totalElements || 0
  } catch (e) {
    message.error('获取充值列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  fetchTopups()
}

const handlePageChange = (page) => {
  pagination.page = page
  fetchTopups()
}

const handleApprove = (row) => {
  dialog.warning({
    title: '确认通过',
    content: `确定通过该充值申请（¥${row.amount?.toFixed(2)}）吗？`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await approveTopup(row.id)
        message.success('审核通过')
        fetchTopups()
      } catch (e) {
        message.error('操作失败')
      }
    }
  })
}

const openRejectModal = (row) => {
  currentId.value = row.id
  rejectReason.value = ''
  showRejectModal.value = true
}

const handleReject = async () => {
  if (!rejectReason.value.trim()) {
    message.warning('请输入拒绝原因')
    return
  }
  rejecting.value = true
  try {
    await rejectTopup(currentId.value, { remark: rejectReason.value })
    message.success('已拒绝')
    showRejectModal.value = false
    fetchTopups()
  } catch (e) {
    message.error('操作失败')
  } finally {
    rejecting.value = false
  }
}

onMounted(() => fetchTopups())
</script>

<style scoped>
.admin-topups { padding: 20px; }
</style>
