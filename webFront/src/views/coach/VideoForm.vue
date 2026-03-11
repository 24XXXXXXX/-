<template>
  <div class="video-form-page">
    <n-card :title="isEdit ? '编辑视频' : '上传视频'">
      <n-spin :show="loading">
        <n-form
          ref="formRef"
          :model="formData"
          :rules="rules"
          label-placement="top"
          style="max-width: 600px; margin: 0 auto"
        >
          <n-form-item label="视频标题" path="title">
            <n-input v-model:value="formData.title" placeholder="请输入视频标题" />
          </n-form-item>
          <n-form-item label="视频分类" path="category">
            <n-input v-model:value="formData.category" placeholder="请输入视频分类" />
          </n-form-item>
          <n-form-item label="视频价格" path="price">
            <n-input-number v-model:value="formData.price" :min="0" placeholder="视频价格" style="width: 100%">
              <template #prefix>¥</template>
            </n-input-number>
          </n-form-item>
          <n-form-item label="视频简介" path="description">
            <n-input
              v-model:value="formData.description"
              type="textarea"
              placeholder="请输入视频简介"
              :rows="4"
            />
          </n-form-item>
          <n-form-item label="视频文件" v-if="!isEdit">
            <n-upload
              v-model:file-list="videoFileList"
              :max="1"
              accept="video/*"
              :custom-request="handleVideoUpload"
              @remove="handleVideoRemove"
            >
              <n-button>选择视频文件</n-button>
            </n-upload>
            <n-progress v-if="uploadProgress > 0 && uploadProgress < 100" :percentage="uploadProgress" />
          </n-form-item>
          <n-form-item label="视频封面">
            <n-upload
              v-model:file-list="coverList"
              :max="1"
              list-type="image-card"
              accept="image/*"
              :custom-request="handleCoverUpload"
              @remove="handleCoverRemove"
            >
              上传封面
            </n-upload>
          </n-form-item>
          <n-form-item>
            <n-space>
              <n-button type="primary" :loading="submitting" :disabled="!isEdit && !formData.videoUrl" @click="handleSubmit">
                {{ isEdit ? '保存修改' : '发布视频' }}
              </n-button>
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
import { getCoachVideoDetail, createVideo, updateVideo, uploadVideoFile } from '@/api/coach'
import { uploadFile } from '@/api/upload'

const route = useRoute()
const router = useRouter()

const isEdit = computed(() => !!route.params.id)
const loading = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const videoFileList = ref([])
const coverList = ref([])
const uploadProgress = ref(0)

const formData = reactive({
  title: '',
  category: '',
  price: null,
  description: '',
  videoUrl: '',
  coverUrl: '',
  duration: 0
})

const rules = {
  title: { required: true, message: '请输入视频标题' },
  price: { required: true, type: 'number', min: 0, message: '请输入视频价格' },
  description: { required: true, message: '请输入视频简介' }
}

const fetchDetail = async () => {
  if (!isEdit.value) return
  loading.value = true
  try {
    const res = await getCoachVideoDetail(route.params.id)
    const data = res.data
    Object.assign(formData, {
      title: data.title,
      category: data.category,
      price: data.price,
      description: data.description,
      videoUrl: data.videoUrl,
      coverUrl: data.coverUrl,
      duration: 0
    })
    if (data.coverUrl) {
      coverList.value = [{ id: 'cover', status: 'finished', url: data.coverUrl }]
    }
  } catch (e) {
    window.$message?.error('获取视频详情失败')
  } finally {
    loading.value = false
  }
}

const handleVideoUpload = async ({ file, onFinish, onError }) => {
  try {
    uploadProgress.value = 0
    const res = await uploadVideoFile(file.file, (e) => {
      if (e.total) {
        uploadProgress.value = Math.round((e.loaded / e.total) * 100)
      }
    })
    const data = res.data
    formData.videoUrl = data?.videoUrl || data
    formData.duration = 0
    file.url = formData.videoUrl
    onFinish()
    window.$message?.success('视频上传成功')
  } catch (e) {
    onError()
    window.$message?.error('视频上传失败')
  } finally {
    uploadProgress.value = 0
  }
}

const handleVideoRemove = () => {
  formData.videoUrl = ''
  formData.duration = 0
  videoFileList.value = []
}

const handleCoverUpload = async ({ file, onFinish, onError }) => {
  try {
    const res = await uploadFile('coach', file.file)
    const url = res.data?.url || res.data
    file.url = url
    formData.coverUrl = url
    onFinish()
  } catch (e) {
    onError()
    window.$message?.error('封面上传失败')
  }
}

const handleCoverRemove = () => {
  formData.coverUrl = ''
  coverList.value = []
}

const handleSubmit = async () => {
  try {
    await formRef.value?.validate()
    submitting.value = true

    const payload = {
      title: formData.title,
      category: formData.category,
      price: formData.price,
      description: formData.description,
      videoUrl: formData.videoUrl,
      coverUrl: formData.coverUrl
    }
    
    if (isEdit.value) {
      await updateVideo(route.params.id, payload)
      window.$message?.success('视频更新成功')
    } else {
      await createVideo(payload)
      window.$message?.success('视频发布成功')
    }
    router.push('/coach/videos')
  } catch (e) {
    if (e?.message) window.$message?.error(e.message)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  fetchDetail()
})
</script>

<style scoped>
.video-form-page {
  padding: 20px;
}
</style>
