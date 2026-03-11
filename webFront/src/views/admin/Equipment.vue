<template>
  <div class="admin-equipment">
    <n-card title="器材管理">
      <template #header-extra>
        <n-button type="primary" @click="$router.push('/admin/equipment/create')">
          <template #icon><n-icon><AddOutline /></n-icon></template>
          新增器材
        </n-button>
      </template>

      <n-space vertical :size="16">
        <n-space>
          <n-input v-model:value="filters.keyword" placeholder="搜索器材名称" clearable style="width: 200px" @keyup.enter="handleSearch" />
          <n-select v-model:value="filters.categoryId" placeholder="分类" clearable style="width: 150px" :options="categoryOptions" />
          <n-select v-model:value="filters.status" placeholder="状态" clearable style="width: 120px" :options="statusOptions" />
          <n-button type="primary" @click="handleSearch">搜索</n-button>
        </n-space>

        <n-data-table :columns="columns" :data="equipments" :loading="loading" :pagination="pagination" remote @update:page="handlePageChange" />
      </n-space>
    </n-card>
  </div>
</template>

<script setup>
import { ref, reactive, h, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NSpace, NTag, NImage, useMessage, useDialog } from 'naive-ui'
import { AddOutline } from '@vicons/ionicons5'
import { getAdminEquipments, getCategories, updateEquipmentStatus, deleteEquipment } from '@/api/equipment'

const router = useRouter()
const message = useMessage()
const dialog = useDialog()

const loading = ref(false)
const equipments = ref([])
const categoryOptions = ref([])
const filters = reactive({ keyword: '', categoryId: null, status: null })
const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })

const statusOptions = [
  { label: '上架', value: 'ON_SALE' },
  { label: '下架', value: 'OFF_SALE' },
  { label: '缺货', value: 'OUT_OF_STOCK' }
]

const statusMap = {
  ON_SALE: { label: '上架', type: 'success' },
  OFF_SALE: { label: '下架', type: 'default' },
  OUT_OF_STOCK: { label: '缺货', type: 'error' }
}

const columns = [
  { title: 'ID', key: 'id', width: 60 },
  {
    title: '图片',
    key: 'coverUrl',
    width: 80,
    render: (row) => row.coverUrl ? h(NImage, { src: row.coverUrl, width: 60, height: 60, objectFit: 'cover' }) : '-'
  },
  { title: '名称', key: 'name' },
  { title: '分类', key: 'categoryName' },
  { title: '价格', key: 'price', render: (row) => `¥${Number(row.price ?? 0).toFixed(2)}` },
  { title: '库存', key: 'stock' },
  { title: '销量', key: 'sales' },
  {
    title: '状态',
    key: 'status',
    render: (row) => h(NTag, { type: statusMap[row.status]?.type }, () => statusMap[row.status]?.label)
  },
  {
    title: '操作',
    key: 'actions',
    width: 220,
    render: (row) => h(NSpace, { size: 8 }, () => [
      h(NButton, { size: 'small', onClick: () => router.push(`/admin/equipment/${row.id}/edit`) }, () => '编辑'),
      h(NButton, { 
        size: 'small', 
        type: row.status === 'ON_SALE' ? 'warning' : 'success',
        onClick: () => handleToggleStatus(row)
      }, () => row.status === 'ON_SALE' ? '下架' : '上架'),
      h(NButton, { size: 'small', type: 'error', onClick: () => handleDelete(row) }, () => '删除')
    ])
  }
]

const fetchEquipments = async () => {
  loading.value = true
  try {
    const res = await getAdminEquipments({
      page: pagination.page,
      size: pagination.pageSize,
      keyword: filters.keyword || undefined,
      categoryId: filters.categoryId || undefined,
      status: filters.status || undefined
    })
    equipments.value = res.data?.content || []
    pagination.itemCount = res.data?.totalElements || 0
  } catch (e) {
    message.error('获取器材列表失败')
  } finally {
    loading.value = false
  }
}

const fetchCategories = async () => {
  try {
    const res = await getCategories()
    categoryOptions.value = (res.data || []).map(c => ({ label: c.name, value: c.id }))
  } catch (e) {
    console.error(e)
  }
}

const handleSearch = () => {
  pagination.page = 1
  fetchEquipments()
}

const handlePageChange = (page) => {
  pagination.page = page
  fetchEquipments()
}

const handleToggleStatus = async (row) => {
  const newStatus = row.status === 'ON_SALE' ? 'OFF_SALE' : 'ON_SALE'
  try {
    await updateEquipmentStatus(row.id, newStatus)
    message.success('状态更新成功')
    fetchEquipments()
  } catch (e) {
    message.error('操作失败')
  }
}

const handleDelete = (row) => {
  dialog.warning({
    title: '确认删除',
    content: `确定要删除器材"${row.name}"吗？`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteEquipment(row.id)
        message.success('删除成功')
        fetchEquipments()
      } catch (e) {
        message.error('删除失败')
      }
    }
  })
}

onMounted(() => {
  fetchCategories()
  fetchEquipments()
})
</script>

<style scoped>
.admin-equipment { padding: 20px; }
</style>
