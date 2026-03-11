<template>
  <div class="admin-notice-form">
    <n-card :title="isEdit ? '编辑公告' : '新增公告'">
      <template #header-extra>
        <n-button @click="$router.back()">返回</n-button>
      </template>

      <n-spin :show="loading">
        <n-form ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="80" style="max-width: 800px; margin: 0 auto">
          <n-form-item label="标题" path="title">
            <n-input v-model:value="form.title" placeholder="请输入公告标题" />
          </n-form-item>
          <n-form-item label="类型" path="noticeType">
            <n-select v-model:value="form.noticeType" :options="noticeTypeOptions" placeholder="请选择公告类型" style="width: 240px" />
          </n-form-item>
          <n-form-item label="内容" path="content">
            <n-input v-model:value="form.content" type="textarea" placeholder="请输入公告内容" :rows="10" />
          </n-form-item>
          <n-form-item label="封面图片">
            <n-upload
              :action="uploadAction"
              :headers="uploadHeaders"
              list-type="image-card"
              :file-list="fileList"
              :max="1"
              accept="image/*"
              @update:file-list="handleFileListUpdate"
              @finish="handleUploadFinish"
              @remove="handleRemove"
              @before-upload="handleBeforeUpload"
            >
              点击上传
            </n-upload>
          </n-form-item>
          <n-form-item label="发布状态">
            <n-switch v-model:value="form.published" :checked-value="1" :unchecked-value="0">
              <template #checked>已发布</template>
              <template #unchecked>未发布</template>
            </n-switch>
          </n-form-item>

          <n-form-item>
            <n-space>
              <n-button type="primary" :loading="submitting" @click="handleSubmit">保存</n-button>
              <n-button @click="$router.back()">取消</n-button>
            </n-space>
          </n-form-item>
        </n-form>
      </n-spin>
    </n-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { getNoticeDetail, createNotice, updateNotice, uploadNoticeCover } from '@/api/admin'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const auth = useAuthStore()

const isEdit = computed(() => !!route.params.id)
const loading = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const fileList = ref([])
const pendingFile = ref(null)

const uploadAction = computed(() => isEdit.value ? `/api/admin/notices/${route.params.id}/cover` : '')
const uploadHeaders = computed(() => ({ Authorization: auth.accessToken ? `Bearer ${auth.accessToken}` : '' }))

const noticeTypeOptions = [
  { label: '系统公告', value: 'SYSTEM' },
  { label: '活动公告', value: 'ACTIVITY' },
  { label: '场地调整', value: 'VENUE_ADJUST' },
  { label: '政策通知', value: 'POLICY' }
]

const form = reactive({
  title: '',
  noticeType: 'SYSTEM',
  content: '',
  published: 1
})

const rules = {
  title: { required: true, message: '请输入公告标题', trigger: 'blur' },
  noticeType: { required: true, message: '请选择公告类型', trigger: 'change' },
  content: { required: true, message: '请输入公告内容', trigger: 'blur' }
}

const fetchNotice = async () => {
  if (!isEdit.value) return
  loading.value = true
  try {
    const res = await getNoticeDetail(route.params.id)
    const data = res.data || {}
    Object.assign(form, {
      title: data.title,
      noticeType: data.noticeType || 'SYSTEM',
      content: data.content,
      published: data.published ?? 1
    })
    if (data.coverUrl) {
      fileList.value = [{
        id: 'cover',
        name: 'cover',
        status: 'finished',
        url: data.coverUrl
      }]
    }
  } catch (e) {
    message.error('获取公告信息失败')
  } finally {
    loading.value = false
  }
}

const handleFileListUpdate = (list) => {
  fileList.value = Array.isArray(list) ? list : []
}

const handleBeforeUpload = ({ file }) => {
  if (!isEdit.value) {
    pendingFile.value = file.file
    fileList.value = [{ id: 'pending', name: file.name, status: 'finished', url: URL.createObjectURL(file.file) }]
    return false
  }
  return true
}

const handleUploadFinish = ({ file, event }) => {
  try {
    const res = JSON.parse(event.target.response)
    file.url = res.url || res.data?.url || res.data
    return file
  } catch (e) {
    return file
  }
}

const handleRemove = () => {
  fileList.value = []
  pendingFile.value = null
}

const handleSubmit = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateNotice(route.params.id, {
        title: form.title,
        noticeType: form.noticeType,
        content: form.content,
        published: form.published
      })
    } else {
      const res = await createNotice({
        title: form.title,
        noticeType: form.noticeType,
        content: form.content,
        published: form.published
      })
      const noticeId = res.data?.id
      if (noticeId && pendingFile.value) {
        await uploadNoticeCover(noticeId, pendingFile.value)
      }
    }
    message.success('保存成功')
    router.back()
  } catch (e) {
    message.error(e.response?.data?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => fetchNotice())
</script>

<style scoped>
.admin-notice-form { padding: 20px; }
</style>
