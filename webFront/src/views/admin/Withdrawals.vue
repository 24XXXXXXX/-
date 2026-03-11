<template>
  <div class="admin-withdrawals">
    <n-card title="提现审核">
      <n-space vertical :size="16">
        <n-space>
          <n-select v-model:value="filters.status" placeholder="状态" clearable style="width: 150px" :options="statusOptions" @update:value="handleSearch" />
        </n-space>

        <n-data-table :columns="columns" :data="withdrawals" :loading="loading" :pagination="pagination" remote @update:page="handlePageChange" />
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
import { getWithdrawRequests, approveWithdraw, rejectWithdraw } from '@/api/admin'
 import dayjs from 'dayjs'

const message = useMessage()
const dialog = useDialog()

const loading = ref(false)
const withdrawals = ref([])
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

 const parseRemark = (remark) => {
   if (!remark || typeof remark !== 'string') {
     return { bankName: null, bankAccount: null, accountName: null }
   }
   const parts = remark.trim().split(/\s+/).filter(Boolean)
   return {
     bankName: parts[0] || null,
     bankAccount: parts[1] || null,
     accountName: parts.length > 2 ? parts.slice(2).join(' ') : null
   }
 }

 const formatDate = (date) => {
   if (!date) return '-'
   return dayjs(date).format('YYYY-MM-DD HH:mm')
 }

const columns = [
  { title: 'ID', key: 'id', width: 80 },
  { title: '申请单号', key: 'requestNo', width: 180 },
  { title: '申请人', key: 'coachUsername', render: (row) => row.coachUsername || row.coachUserId || '-' },
  { title: '提现金额', key: 'amount', render: (row) => `¥${(row.amount ?? 0).toFixed(2)}` },
  { title: '银行', key: 'bankName', render: (row) => parseRemark(row.remark).bankName || '-' },
  { title: '账号', key: 'bankAccount', render: (row) => parseRemark(row.remark).bankAccount || '-' },
  { title: '户名', key: 'accountName', render: (row) => parseRemark(row.remark).accountName || '-' },
  {
    title: '状态',
    key: 'status',
    render: (row) => h(NTag, { type: statusMap[row.status]?.type }, () => statusMap[row.status]?.label)
  },
  { title: '申请时间', key: 'requestedAt', width: 180, render: (row) => formatDate(row.requestedAt) },
  {
    title: '操作',
    key: 'actions',
    width: 150,
    render: (row) => {
      if (row.status !== 'PENDING') return '-'
      return h(NSpace, { size: 8 }, () => [
        h(NButton, { size: 'small', type: 'success', onClick: () => handleApprove(row) }, () => '通过'),
        h(NButton, { size: 'small', type: 'error', onClick: () => openRejectModal(row) }, () => '拒绝')
      ])
    }
  }
]

const fetchWithdrawals = async () => {
  loading.value = true
  try {
    const res = await getWithdrawRequests({
      page: pagination.page,
      size: pagination.pageSize,
      status: filters.status || undefined
    })
    withdrawals.value = res.data?.items || res.data?.content || []
    pagination.itemCount = res.data?.total ?? res.data?.totalElements ?? 0
  } catch (e) {
    message.error('获取提现列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  fetchWithdrawals()
}

const handlePageChange = (page) => {
  pagination.page = page
  fetchWithdrawals()
}

const handleApprove = (row) => {
  dialog.warning({
    title: '确认通过',
    content: `确定通过该提现申请（¥${row.amount?.toFixed(2)}）吗？`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await approveWithdraw(row.id)
        message.success('审核通过')
        fetchWithdrawals()
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
    await rejectWithdraw(currentId.value, { remark: rejectReason.value })
    message.success('已拒绝')
    showRejectModal.value = false
    fetchWithdrawals()
  } catch (e) {
    message.error('操作失败')
  } finally {
    rejecting.value = false
  }
}

onMounted(() => fetchWithdrawals())
</script>

<style scoped>
.admin-withdrawals { padding: 20px; }
</style>
