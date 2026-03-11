<template>
  <div class="admin-venues">
    <n-card title="场地管理">
      <template #header-extra>
        <n-button type="primary" @click="$router.push('/admin/venues/create')">
          <template #icon><n-icon><AddOutline /></n-icon></template>
          新增场地
        </n-button>
      </template>

      <n-space vertical :size="16">
        <n-space>
          <n-input v-model:value="filters.keyword" placeholder="搜索场地名称" clearable style="width: 200px" @keyup.enter="handleSearch" />
          <n-select v-model:value="filters.typeId" placeholder="场地类型" clearable style="width: 150px" :options="typeOptions" />
          <n-select v-model:value="filters.status" placeholder="状态" clearable style="width: 120px" :options="statusOptions" />
          <n-button type="primary" @click="handleSearch">搜索</n-button>
        </n-space>

        <n-data-table :columns="columns" :data="venues" :loading="loading" :pagination="pagination" remote @update:page="handlePageChange" />
      </n-space>
    </n-card>

    <n-modal v-model:show="showTimeslotModal" preset="card" title="生成时段" style="width: 500px">
      <n-form :model="timeslotForm" label-placement="left" label-width="100">
        <n-form-item label="日期范围">
          <n-date-picker v-model:value="timeslotForm.dateRange" type="daterange" clearable style="width: 100%" />
        </n-form-item>
        <n-form-item label="开始时间">
          <n-time-picker v-model:value="timeslotForm.startTime" format="HH:mm" :hours="[6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21]" />
        </n-form-item>
        <n-form-item label="结束时间">
          <n-time-picker v-model:value="timeslotForm.endTime" format="HH:mm" :hours="[7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22]" />
        </n-form-item>
        <n-form-item label="时段时长">
          <n-input-number v-model:value="timeslotForm.duration" :min="30" :max="180" :step="30" />
          <span style="margin-left: 8px">分钟</span>
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showTimeslotModal = false">取消</n-button>
          <n-button type="primary" :loading="generating" @click="handleGenerateTimeslots">生成</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, reactive, h, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NSpace, NTag, NImage, useMessage } from 'naive-ui'
import { AddOutline } from '@vicons/ionicons5'
import { getVenues, getVenueTypes, updateVenueStatus, generateTimeslots } from '@/api/venue'

const router = useRouter()
const message = useMessage()

const loading = ref(false)
const venues = ref([])
const typeOptions = ref([])
const filters = reactive({ keyword: '', typeId: null, status: null })
const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })

const statusOptions = [
  { label: '可用', value: 'ACTIVE' },
  { label: '维护中', value: 'MAINTENANCE' },
  { label: '已停用', value: 'DISABLED' }
]

const statusMap = {
  ACTIVE: { label: '可用', type: 'success' },
  MAINTENANCE: { label: '维护中', type: 'warning' },
  DISABLED: { label: '已停用', type: 'error' }
}

const showTimeslotModal = ref(false)
const currentVenueId = ref(null)
const generating = ref(false)
const timeslotForm = reactive({
  dateRange: null,
  startTime: null,
  endTime: null,
  duration: 60
})

const columns = [
  { title: 'ID', key: 'id', width: 60 },
  {
    title: '图片',
    key: 'coverUrl',
    width: 80,
    render: (row) => row.coverUrl ? h(NImage, { src: row.coverUrl, width: 60, height: 40, objectFit: 'cover' }) : '-'
  },
  { title: '名称', key: 'name' },
  { title: '类型', key: 'typeName' },
  { title: '区域', key: 'area' },
  { title: '地址', key: 'address' },
  { title: '价格', key: 'pricePerHour', render: (row) => `¥${row.pricePerHour ?? 0}` },
  {
    title: '状态',
    key: 'status',
    render: (row) => h(NTag, { type: statusMap[row.status]?.type }, () => statusMap[row.status]?.label)
  },
  {
    title: '操作',
    key: 'actions',
    width: 260,
    render: (row) => h(NSpace, { size: 8 }, () => [
      h(NButton, { size: 'small', onClick: () => router.push(`/admin/venues/${row.id}/edit`) }, () => '编辑'),
      h(NButton, { size: 'small', type: 'info', onClick: () => openTimeslotModal(row) }, () => '生成时段'),
      h(NButton, { 
        size: 'small', 
        type: row.status === 'ACTIVE' ? 'warning' : 'success',
        onClick: () => handleToggleStatus(row)
      }, () => row.status === 'ACTIVE' ? '停用' : '启用')
    ])
  }
]

const fetchVenues = async () => {
  loading.value = true
  try {
    const res = await getVenues({
      page: pagination.page,
      size: pagination.pageSize,
      keyword: filters.keyword || undefined,
      typeId: filters.typeId || undefined,
      status: filters.status || undefined
    })
    venues.value = res.data?.content || []
    pagination.itemCount = res.data?.totalElements || 0
  } catch (e) {
    message.error('获取场地列表失败')
  } finally {
    loading.value = false
  }
}

const fetchTypes = async () => {
  try {
    const res = await getVenueTypes()
    typeOptions.value = (res.data || []).map(t => ({ label: t.name, value: t.id }))
  } catch (e) {
    console.error(e)
  }
}

const handleSearch = () => {
  pagination.page = 1
  fetchVenues()
}

const handlePageChange = (page) => {
  pagination.page = page
  fetchVenues()
}

const handleToggleStatus = async (row) => {
  const newStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  try {
    await updateVenueStatus(row.id, newStatus)
    message.success('状态更新成功')
    fetchVenues()
  } catch (e) {
    message.error('操作失败')
  }
}

const openTimeslotModal = (row) => {
  currentVenueId.value = row.id
  timeslotForm.dateRange = null
  timeslotForm.startTime = null
  timeslotForm.endTime = null
  timeslotForm.duration = 60
  showTimeslotModal.value = true
}

const formatLocalDate = (d) => {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

const handleGenerateTimeslots = async () => {
  if (!timeslotForm.dateRange || !timeslotForm.startTime || !timeslotForm.endTime) {
    message.warning('请填写完整信息')
    return
  }

  const startTime = new Date(timeslotForm.startTime)
  const endTime = new Date(timeslotForm.endTime)
  if (startTime.getMinutes() !== 0 || endTime.getMinutes() !== 0) {
    message.warning('开始/结束时间请设置为整点（分钟为00）')
    return
  }

  generating.value = true
  try {
    const startDate = new Date(timeslotForm.dateRange[0])
    const endDate = new Date(timeslotForm.dateRange[1])

    const startHour = startTime.getHours()
    const endHour = endTime.getHours()

    let totalCreated = 0
    const cursor = new Date(startDate)
    cursor.setHours(0, 0, 0, 0)
    const end = new Date(endDate)
    end.setHours(0, 0, 0, 0)

    while (cursor.getTime() <= end.getTime()) {
      const date = formatLocalDate(cursor)
      const r = await generateTimeslots(currentVenueId.value, {
        date,
        startHour,
        endHour,
        slotMinutes: timeslotForm.duration
      })
      totalCreated += Number(r?.data?.createdCount ?? 0)
      cursor.setDate(cursor.getDate() + 1)
    }

    message.success(`时段生成成功，共生成${totalCreated}个`) 
    showTimeslotModal.value = false
  } catch (e) {
    message.error(e.response?.data?.message || '生成失败')
  } finally {
    generating.value = false
  }
}

onMounted(() => {
  fetchTypes()
  fetchVenues()
})
</script>

<style scoped>
.admin-venues { padding: 20px; }
</style>
