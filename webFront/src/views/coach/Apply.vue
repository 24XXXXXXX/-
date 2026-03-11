<template>
  <div class="coach-apply-page">
    <n-card title="教练认证申请">
      <n-spin :show="loading">
        <!-- 已有申请 -->
        <div v-if="application" class="application-status">
          <n-result
            v-if="application.auditStatus === 'PENDING'"
            status="info"
            title="申请审核中"
            description="您的教练认证申请正在审核中，请耐心等待"
          />
          <n-result
            v-else-if="application.auditStatus === 'APPROVED' && authStore.isCoach"
            status="success"
            title="认证通过"
            description="恭喜您已成为认证教练，可以开始发布课程了"
          >
            <template #footer>
              <n-button type="primary" @click="goCoachCenter">进入教练中心</n-button>
            </template>
          </n-result>
          <n-result
            v-else-if="application.auditStatus === 'APPROVED' && !authStore.isCoach"
            status="warning"
            title="教练权限未生效"
            description="您的认证记录为通过，但当前账号未具备教练角色权限（可能被管理员撤销）。如需继续使用教练功能，请联系管理员处理或退出重新登录后再试"
          />
          <n-result
            v-else-if="application.auditStatus === 'REJECTED'"
            status="error"
            title="认证未通过"
            :description="application.auditRemark || '您的申请未通过审核'"
          >
            <template #footer>
              <n-button type="primary" @click="reapply">重新申请</n-button>
            </template>
          </n-result>
        </div>

        <!-- 申请表单 -->
        <n-form
          v-else
          ref="formRef"
          :model="formData"
          :rules="rules"
          label-placement="top"
          style="max-width: 600px; margin: 0 auto"
        >
          <n-form-item label="真实姓名" path="realName">
            <n-input v-model:value="formData.realName" placeholder="请输入真实姓名" />
          </n-form-item>
          <n-form-item label="身份证号" path="idCard">
            <n-input v-model:value="formData.idCard" placeholder="请输入身份证号" />
          </n-form-item>
          <n-form-item label="联系电话" path="phone">
            <n-input v-model:value="formData.phone" placeholder="请输入联系电话" />
          </n-form-item>
          <n-form-item label="专业领域" path="specialty">
            <n-select
              v-model:value="formData.specialty"
              :options="specialtyOptions"
              placeholder="请选择专业领域"
              multiple
            />
          </n-form-item>
          <n-form-item label="从业年限" path="experience">
            <n-input-number v-model:value="formData.experience" :min="0" placeholder="从业年限" style="width: 100%">
              <template #suffix>年</template>
            </n-input-number>
          </n-form-item>
          <n-form-item label="个人简介" path="introduction">
            <n-input
              v-model:value="formData.introduction"
              type="textarea"
              placeholder="请介绍您的专业背景和教学经验"
              :rows="4"
            />
          </n-form-item>
          <n-form-item label="资质证书">
            <n-upload
              v-model:file-list="certFileList"
              :max="5"
              list-type="image-card"
              accept="image/*"
              :custom-request="handleUpload"
            >
              上传证书
            </n-upload>
            <p class="upload-tip">请上传相关资质证书照片，最多5张</p>
          </n-form-item>
          <n-form-item>
            <n-button type="primary" :loading="submitting" @click="handleSubmit">提交申请</n-button>
          </n-form-item>
        </n-form>
      </n-spin>
    </n-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { submitCoachApplication, getMyCoachApplication } from '@/api/coach'
import { uploadPhoto } from '@/api/upload'

const loading = ref(false)
const submitting = ref(false)
const application = ref(null)
const formRef = ref(null)
const certFileList = ref([])

const router = useRouter()
const authStore = useAuthStore()

const specialtyOptions = [
  { label: '健身', value: '健身' },
  { label: '瑜伽', value: '瑜伽' },
  { label: '游泳', value: '游泳' },
  { label: '篮球', value: '篮球' },
  { label: '羽毛球', value: '羽毛球' },
  { label: '乒乓球', value: '乒乓球' },
  { label: '网球', value: '网球' },
  { label: '其他', value: '其他' }
]

const formData = reactive({
  realName: '',
  idCard: '',
  phone: '',
  specialty: [],
  experience: null,
  introduction: ''
})

const rules = {
  realName: { required: true, message: '请输入真实姓名' },
  idCard: [
    { required: true, message: '请输入身份证号' },
    { pattern: /^\d{17}[\dXx]$/, message: '请输入正确的身份证号' }
  ],
  phone: [
    { required: true, message: '请输入联系电话' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号' }
  ],
  specialty: { required: true, type: 'array', min: 1, message: '请选择专业领域' },
  experience: { required: true, type: 'number', message: '请输入从业年限' },
  introduction: [
    { required: true, message: '请输入个人简介' },
    { min: 20, message: '个人简介至少20个字符' }
  ]
}

const fetchApplication = async () => {
  loading.value = true
  try {
    try {
      await authStore.fetchUserInfo()
    } catch (e) {
    }

    const res = await getMyCoachApplication()
    application.value = res.data

    if (application.value?.auditStatus === 'APPROVED') {
      try {
        await authStore.refreshTokenPair()
      } catch (e) {
      }

      try {
        await authStore.fetchUserInfo()
      } catch (e) {
      }
    }
  } catch (e) {
    // 没有申请记录
    application.value = null
  } finally {
    loading.value = false
  }
}

const handleUpload = async ({ file, onFinish, onError }) => {
  try {
    const res = await uploadPhoto('coach', file.file)
    file.url = res.data?.url || res.data
    onFinish()
  } catch (e) {
    onError()
    window.$message?.error('上传失败')
  }
}

const handleSubmit = async () => {
  try {
    await formRef.value?.validate()
    submitting.value = true
    
    const certificates = certFileList.value
      .filter(f => f.status === 'finished' && f.url)
      .map(f => f.url)
    
    await submitCoachApplication({
      specialty: Array.isArray(formData.specialty) ? formData.specialty.join('、') : (formData.specialty || ''),
      intro: formData.introduction,
      certFiles: certificates
    })
    
    window.$message?.success('申请已提交，请等待审核')
    fetchApplication()
  } catch (e) {
    if (e?.message) window.$message?.error(e.message)
  } finally {
    submitting.value = false
  }
}

const reapply = () => {
  application.value = null
}

const goCoachCenter = async () => {
  try {
    await authStore.refreshTokenPair()
  } catch (e) {
  }

  if (!authStore.isCoach) {
    window.$message?.warning('教练权限尚未生效，请退出重新登录后再试')
    return
  }

  router.push('/coach/courses')
}

onMounted(() => {
  fetchApplication()
})
</script>

<style scoped>
.coach-apply-page {
  padding: 20px;
}
.application-status {
  padding: 40px 0;
}
.upload-tip {
  margin: 8px 0 0;
  font-size: 12px;
  color: #999;
}
</style>
