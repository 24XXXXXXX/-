<template>
  <div class="admin-banner-form">
    <n-card :title="isEdit ? '编辑轮播图' : '新增轮播图'">
      <template #header-extra>
        <n-button @click="$router.back()">返回</n-button>
      </template>

      <n-spin :show="loading">
        <n-form ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="80" style="max-width: 600px; margin: 0 auto">
          <n-form-item label="标题" path="title">
            <n-input v-model:value="form.title" placeholder="请输入轮播图标题" />
          </n-form-item>
          <n-form-item label="链接" path="linkUrl">
            <n-input v-model:value="form.linkUrl" placeholder="请输入跳转链接（可选）" />
          </n-form-item>
          <n-form-item label="排序" path="sortOrder">
            <n-input-number v-model:value="form.sortOrder" :min="0" placeholder="数字越小越靠前" style="width: 200px" />
          </n-form-item>
          <n-form-item label="状态">
            <n-switch v-model:value="form.enabled" :checked-value="1" :unchecked-value="0">
              <template #checked>启用</template>
              <template #unchecked>禁用</template>
            </n-switch>
          </n-form-item>
          <n-form-item label="图片" required>
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
            <div class="upload-tip">建议尺寸：1200 x 400 像素</div>
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
import { getBanners, createBanner, updateBanner, uploadBannerImage } from '@/api/admin'
import { useAuthStore } from '@/stores/auth'
import { uploadPhoto } from '@/api/upload'

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

const uploadAction = computed(() => isEdit.value ? `/api/admin/home-banners/${route.params.id}/image` : '')
const uploadHeaders = computed(() => ({ Authorization: auth.accessToken ? `Bearer ${auth.accessToken}` : '' }))

const form = reactive({
  title: '',
  linkUrl: '',
  sortOrder: 0,
  enabled: 1
})

const rules = {
  title: { required: true, message: '请输入轮播图标题', trigger: 'blur' }
}

const fetchBanner = async () => {
  if (!isEdit.value) return
  loading.value = true
  try {
    const res = await getBanners({})
    const banner = (res.data || []).find(b => b.id === Number(route.params.id))
    if (banner) {
      Object.assign(form, {
        title: banner.title,
        linkUrl: banner.linkUrl,
        sortOrder: banner.sortOrder,
        enabled: banner.enabled
      })
      if (banner.imageUrl) {
        fileList.value = [{
          id: 'image',
          name: 'image',
          status: 'finished',
          url: banner.imageUrl
        }]
      }
    }
  } catch (e) {
    message.error('获取轮播图信息失败')
  } finally {
    loading.value = false
  }
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

const handleFileListUpdate = (list) => {
  fileList.value = Array.isArray(list) ? list : []
}

const handleSubmit = async () => {
  await formRef.value?.validate()
  if (!isEdit.value && !pendingFile.value && !fileList.value.length) {
    message.warning('请上传轮播图图片')
    return
  }
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateBanner(route.params.id, {
        title: form.title,
        linkUrl: form.linkUrl,
        sortOrder: form.sortOrder,
        enabled: form.enabled
      })
    } else {
      let imageUrl
      if (pendingFile.value) {
        const up = await uploadPhoto('banner', pendingFile.value)
        imageUrl = up?.data?.url
      }
      const payload = {
        title: form.title,
        linkUrl: form.linkUrl,
        sortOrder: form.sortOrder,
        enabled: form.enabled,
        imageUrl,
      }
      const res = await createBanner(payload)
      const bannerId = res.data?.id
      if (bannerId && pendingFile.value) {
        pendingFile.value = null
        fileList.value = []
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

onMounted(() => fetchBanner())
</script>

<style scoped>
.admin-banner-form { padding: 20px; }
.upload-tip { font-size: 12px; color: #999; margin-top: 8px; }
</style>
