<template>
  <div class="course-form-page">
    <n-card :title="isEdit ? '编辑课程' : '发布课程'">
      <n-spin :show="loading">
        <n-form
          ref="formRef"
          :model="formData"
          :rules="rules"
          label-placement="top"
          style="max-width: 600px; margin: 0 auto"
        >
          <n-form-item label="课程名称" path="name">
            <n-input v-model:value="formData.name" placeholder="请输入课程名称" />
          </n-form-item>
          <n-form-item label="课程类型" path="type">
            <n-input v-model:value="formData.type" placeholder="请输入课程类型" />
          </n-form-item>
          <n-form-item label="课程价格" path="price">
            <n-input-number v-model:value="formData.price" :min="0" placeholder="课程价格" style="width: 100%">
              <template #prefix>¥</template>
            </n-input-number>
          </n-form-item>
          <n-form-item label="课程时长" path="duration">
            <n-input-number v-model:value="formData.duration" :min="30" placeholder="课程时长" style="width: 100%">
              <template #suffix>分钟</template>
            </n-input-number>
          </n-form-item>
          <n-form-item label="课程简介" path="description">
            <n-input
              v-model:value="formData.description"
              type="textarea"
              placeholder="请输入课程简介"
              :rows="4"
            />
          </n-form-item>
          <n-form-item label="课程详情" path="content">
            <n-input
              v-model:value="formData.content"
              type="textarea"
              placeholder="请输入课程详细内容"
              :rows="6"
            />
          </n-form-item>
          <n-form-item label="课程封面">
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
              <n-button type="primary" :loading="submitting" @click="handleSubmit">
                {{ isEdit ? '保存修改' : '发布课程' }}
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
import { getCoachCourseDetail, createCourse, updateCourse } from '@/api/coach'
import { uploadFile } from '@/api/upload'

const route = useRoute()
const router = useRouter()

const isEdit = computed(() => !!route.params.id)
const loading = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const coverList = ref([])

const formData = reactive({
  name: '',
  type: '',
  price: null,
  duration: 60,
  description: '',
  content: '',
  coverUrl: ''
})

const rules = {
  name: { required: true, message: '请输入课程名称' },
  type: { required: true, message: '请输入课程类型' },
  price: { required: true, type: 'number', min: 0, message: '请输入课程价格' },
  duration: { required: true, type: 'number', min: 30, message: '请输入课程时长' },
  description: { required: true, message: '请输入课程简介' }
}

const fetchDetail = async () => {
  if (!isEdit.value) return
  loading.value = true
  try {
    const res = await getCoachCourseDetail(route.params.id)
    const data = res.data
    Object.assign(formData, {
      name: data.title,
      type: data.category,
      price: data.price,
      duration: data.durationMinutes,
      description: data.outline,
      content: '',
      coverUrl: data.coverUrl
    })
    if (data.coverUrl) {
      coverList.value = [{ id: 'cover', status: 'finished', url: data.coverUrl }]
    }
  } catch (e) {
    window.$message?.error('获取课程详情失败')
  } finally {
    loading.value = false
  }
}

const handleCoverUpload = async ({ file, onFinish, onError }) => {
  try {
    const res = await uploadFile('course', file.file)
    const url = res.data?.url || res.data
    file.url = url
    formData.coverUrl = url
    onFinish()
  } catch (e) {
    onError()
    window.$message?.error('上传失败')
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
      title: formData.name,
      category: formData.type,
      price: formData.price,
      durationMinutes: formData.duration,
      outline: formData.content || formData.description,
      coverUrl: formData.coverUrl
    }
    
    if (isEdit.value) {
      await updateCourse(route.params.id, payload)
      window.$message?.success('课程更新成功')
    } else {
      await createCourse(payload)
      window.$message?.success('课程发布成功')
    }
    router.push('/coach/courses')
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
.course-form-page {
  padding: 20px;
}
</style>
