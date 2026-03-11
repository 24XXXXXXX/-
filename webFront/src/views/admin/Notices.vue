<template>
  <div class="admin-notices">
    <n-card title="公告管理">
      <template #header-extra>
        <n-button type="primary" @click="$router.push('/admin/notices/create')">
          <template #icon><n-icon><AddOutline /></n-icon></template>
          新增公告
        </n-button>
      </template>

      <n-space vertical :size="16">
        <n-space>
          <n-input v-model:value="filters.keyword" placeholder="搜索标题" clearable style="width: 200px" @keyup.enter="handleSearch" />
          <n-select v-model:value="filters.published" placeholder="发布状态" clearable style="width: 130px" :options="publishedOptions" />
          <n-button type="primary" @click="handleSearch">搜索</n-button>
        </n-space>

        <n-data-table :columns="columns" :data="notices" :loading="loading" :pagination="pagination" remote @update:page="handlePageChange" />
      </n-space>
    </n-card>
  </div>
</template>

<script setup>
import { ref, reactive, h, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NSpace, NTag, NImage, useMessage, useDialog } from 'naive-ui'
import { AddOutline } from '@vicons/ionicons5'
import { getNotices, updateNoticePublished, deleteNotice } from '@/api/admin'

const router = useRouter()
const message = useMessage()
const dialog = useDialog()

const loading = ref(false)
const notices = ref([])
const filters = reactive({ keyword: '', published: null })
const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })

const publishedOptions = [
  { label: '已发布', value: 1 },
  { label: '未发布', value: 0 }
]

const columns = [
  { title: 'ID', key: 'id', width: 60 },
  {
    title: '封面',
    key: 'coverUrl',
    width: 80,
    render: (row) => row.coverUrl ? h(NImage, { src: row.coverUrl, width: 60, height: 40, objectFit: 'cover' }) : '-'
  },
  { title: '标题', key: 'title', ellipsis: { tooltip: true } },
  {
    title: '状态',
    key: 'published',
    render: (row) => h(NTag, { type: row.published === 1 ? 'success' : 'default' }, () => row.published === 1 ? '已发布' : '未发布')
  },
  { title: '发布时间', key: 'publishAt', width: 180 },
  {
    title: '操作',
    key: 'actions',
    width: 220,
    render: (row) => h(NSpace, { size: 8 }, () => [
      h(NButton, { size: 'small', onClick: () => router.push(`/admin/notices/${row.id}/edit`) }, () => '编辑'),
      h(NButton, { 
        size: 'small', 
        type: row.published === 1 ? 'warning' : 'success',
        onClick: () => handleTogglePublished(row)
      }, () => row.published === 1 ? '取消发布' : '发布'),
      h(NButton, { size: 'small', type: 'error', onClick: () => handleDelete(row) }, () => '删除')
    ])
  }
]

const fetchNotices = async () => {
  loading.value = true
  try {
    const res = await getNotices({
      page: pagination.page,
      size: pagination.pageSize,
      keyword: filters.keyword || undefined,
      published: filters.published === null ? undefined : filters.published
    })
    notices.value = res.data?.items || res.data?.content || res.data?.records || []
    pagination.itemCount = res.data?.total || res.data?.totalElements || 0
  } catch (e) {
    message.error('获取公告列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  fetchNotices()
}

const handlePageChange = (page) => {
  pagination.page = page
  fetchNotices()
}

const handleTogglePublished = async (row) => {
  try {
    await updateNoticePublished(row.id, { published: row.published === 1 ? 0 : 1 })
    message.success('操作成功')
    fetchNotices()
  } catch (e) {
    message.error('操作失败')
  }
}

const handleDelete = (row) => {
  dialog.warning({
    title: '确认删除',
    content: `确定要删除公告"${row.title}"吗？`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteNotice(row.id)
        message.success('删除成功')
        fetchNotices()
      } catch (e) {
        message.error('删除失败')
      }
    }
  })
}

onMounted(() => fetchNotices())
</script>

<style scoped>
.admin-notices { padding: 20px; }
</style>
