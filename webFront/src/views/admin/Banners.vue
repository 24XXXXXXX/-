<template>
  <div class="admin-banners">
    <n-card title="轮播图管理">
      <template #header-extra>
        <n-button type="primary" @click="$router.push('/admin/banners/create')">
          <template #icon><n-icon><AddOutline /></n-icon></template>
          新增轮播图
        </n-button>
      </template>

      <n-space vertical :size="16">
        <n-space>
          <n-select v-model:value="filters.enabled" placeholder="状态" clearable style="width: 130px" :options="enabledOptions" @update:value="handleSearch" />
        </n-space>

        <n-data-table :columns="columns" :data="banners" :loading="loading" />
      </n-space>
    </n-card>
  </div>
</template>

<script setup>
import { ref, reactive, h, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NSpace, NTag, NImage, useMessage, useDialog } from 'naive-ui'
import { AddOutline } from '@vicons/ionicons5'
import { getBanners, updateBannerEnabled, deleteBanner } from '@/api/admin'

const router = useRouter()
const message = useMessage()
const dialog = useDialog()

const loading = ref(false)
const banners = ref([])
const filters = reactive({ enabled: null })

const enabledOptions = [
  { label: '已启用', value: 1 },
  { label: '已禁用', value: 0 }
]

const columns = [
  { title: 'ID', key: 'id', width: 60 },
  {
    title: '图片',
    key: 'imageUrl',
    width: 150,
    render: (row) => row.imageUrl ? h(NImage, { src: row.imageUrl, width: 120, height: 60, objectFit: 'cover' }) : '-'
  },
  { title: '标题', key: 'title' },
  { title: '链接', key: 'linkUrl', ellipsis: { tooltip: true } },
  { title: '排序', key: 'sortOrder', width: 80 },
  {
    title: '状态',
    key: 'enabled',
    render: (row) => h(NTag, { type: row.enabled === 1 ? 'success' : 'default' }, () => row.enabled === 1 ? '已启用' : '已禁用')
  },
  {
    title: '操作',
    key: 'actions',
    width: 200,
    render: (row) => h(NSpace, { size: 8 }, () => [
      h(NButton, { size: 'small', onClick: () => router.push(`/admin/banners/${row.id}/edit`) }, () => '编辑'),
      h(NButton, { 
        size: 'small', 
        type: row.enabled === 1 ? 'warning' : 'success',
        onClick: () => handleToggleEnabled(row)
      }, () => row.enabled === 1 ? '禁用' : '启用'),
      h(NButton, { size: 'small', type: 'error', onClick: () => handleDelete(row) }, () => '删除')
    ])
  }
]

const fetchBanners = async () => {
  loading.value = true
  try {
    const res = await getBanners({
      enabled: filters.enabled === null ? undefined : filters.enabled
    })
    banners.value = res.data || []
  } catch (e) {
    message.error('获取轮播图列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  fetchBanners()
}

const handleToggleEnabled = async (row) => {
  try {
    await updateBannerEnabled(row.id, { enabled: row.enabled === 1 ? 0 : 1 })
    message.success('操作成功')
    fetchBanners()
  } catch (e) {
    message.error('操作失败')
  }
}

const handleDelete = (row) => {
  dialog.warning({
    title: '确认删除',
    content: '确定要删除该轮播图吗？',
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteBanner(row.id)
        message.success('删除成功')
        fetchBanners()
      } catch (e) {
        message.error('删除失败')
      }
    }
  })
}

onMounted(() => fetchBanners())
</script>

<style scoped>
.admin-banners { padding: 20px; }
</style>
