<template>
  <div class="inspection-form-page">
    <n-card title="上报巡检">
      <n-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-placement="top"
        style="max-width: 600px; margin: 0 auto"
      >
        <n-form-item label="巡检对象" path="targetType">
          <n-select v-model:value="formData.targetType" :options="targetTypeOptions" placeholder="请选择巡检对象" />
        </n-form-item>
        <n-form-item v-if="formData.targetType === 'VENUE'" label="选择场地" path="venueId">
          <n-select
            v-model:value="formData.venueId"
            :options="venueOptions"
            placeholder="请选择场地"
            filterable
          />
        </n-form-item>
        <n-form-item v-if="formData.targetType === 'EQUIPMENT'" label="选择器材" path="equipmentId">
          <n-select
            v-model:value="formData.equipmentId"
            :options="equipmentOptions"
            placeholder="请选择器材"
            filterable
          />
        </n-form-item>
        <n-form-item label="问题类型" path="issueType">
          <n-select v-model:value="formData.issueType" :options="issueTypeOptions" placeholder="请选择问题类型" />
        </n-form-item>
        <n-form-item label="巡检内容" path="content">
          <n-input
            v-model:value="formData.content"
            type="textarea"
            placeholder="请详细描述巡检情况"
            :rows="5"
          />
        </n-form-item>
        <n-form-item label="现场照片">
          <n-upload
            v-model:file-list="fileList"
            :max="5"
            list-type="image-card"
            accept="image/*"
            :custom-request="handleUpload"
          >
            上传照片
          </n-upload>
          <p class="upload-tip">最多上传5张照片</p>
        </n-form-item>
        <n-form-item>
          <n-space>
            <n-button type="primary" :loading="submitting" @click="handleSubmit">提交</n-button>
            <n-button @click="$router.back()">取消</n-button>
          </n-space>
        </n-form-item>
      </n-form>
    </n-card>
  </div>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { useRouter } from 'vue-router'
import { createInspection } from '@/api/staff'
import { uploadFile } from '@/api/upload'
import { getVenues } from '@/api/venue'
import { getEquipments } from '@/api/equipment'

const router = useRouter()
const submitting = ref(false)
const formRef = ref(null)
const fileList = ref([])
const venueOptions = ref([])
const equipmentOptions = ref([])

const targetTypeOptions = [
  { label: '场地', value: 'VENUE' },
  { label: '器材', value: 'EQUIPMENT' },
  { label: '其他', value: 'OTHER' }
]

const issueTypeOptions = [
  { label: '维护', value: 'MAINTENANCE' },
  { label: '维修', value: 'REPAIR' },
  { label: '缺少', value: 'SHORTAGE' },
  { label: '损坏', value: 'DAMAGE' },
  { label: '其他', value: 'OTHER' }
]

const formData = reactive({
  targetType: null,
  venueId: null,
  equipmentId: null,
  issueType: null,
  content: ''
})

const rules = {
  targetType: { required: true, message: '请选择巡检对象' },
  venueId: {
    trigger: ['change', 'blur'],
    validator: (_rule, value) => {
      if (formData.targetType === 'VENUE' && (value === null || value === undefined || value === '')) {
        return new Error('请选择场地')
      }
      return true
    }
  },
  equipmentId: {
    trigger: ['change', 'blur'],
    validator: (_rule, value) => {
      if (formData.targetType === 'EQUIPMENT' && (value === null || value === undefined || value === '')) {
        return new Error('请选择器材')
      }
      return true
    }
  },
  issueType: { required: true, message: '请选择问题类型' },
  content: [
    { required: true, message: '请输入巡检内容' },
    { min: 10, message: '巡检内容至少10个字符' }
  ]
}

const fetchVenueOptions = async () => {
  if (venueOptions.value.length) return
  try {
    const res = await getVenues({ page: 1, size: 100, status: 'ACTIVE' })
    const rows = res.data?.items || res.data?.content || []
    venueOptions.value = rows.map(v => ({
      label: [v.name, v.area].filter(Boolean).join(' - '),
      value: v.id
    }))
  } catch (e) {
    window.$message?.error('获取场地列表失败')
  }
}

const fetchEquipmentOptions = async () => {
  if (equipmentOptions.value.length) return
  try {
    const res = await getEquipments({ page: 1, size: 100 })
    const rows = res.data?.items || res.data?.content || []
    equipmentOptions.value = rows.map(v => ({
      label: [v.name, v.categoryName].filter(Boolean).join(' - '),
      value: v.id
    }))
  } catch (e) {
    window.$message?.error('获取器材列表失败')
  }
}

watch(
  () => formData.targetType,
  async (val) => {
    if (val === 'VENUE') {
      formData.equipmentId = null
      await fetchVenueOptions()
    } else if (val === 'EQUIPMENT') {
      formData.venueId = null
      await fetchEquipmentOptions()
    } else {
      formData.venueId = null
      formData.equipmentId = null
    }
  }
)

const handleUpload = async ({ file, onFinish, onError }) => {
  try {
    const res = await uploadFile('inspection', file.file)
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
    
    const attachments = fileList.value
      .filter(f => f.status === 'finished' && f.url)
      .map(f => f.url)
    
    const payload = {
      targetType: formData.targetType,
      issueType: formData.issueType,
      content: formData.content,
      attachments
    }
    if (formData.targetType === 'VENUE') {
      payload.venueId = formData.venueId
    }
    if (formData.targetType === 'EQUIPMENT') {
      payload.equipmentId = formData.equipmentId
    }
    await createInspection(payload)
    
    window.$message?.success('巡检上报成功')
    router.push('/staff/inspections')
  } catch (e) {
    const msg = e?.response?.data?.message || e?.message || '提交失败'
    window.$message?.error(msg)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.inspection-form-page {
  padding: 20px;
}
.upload-tip {
  margin: 8px 0 0;
  font-size: 12px;
  color: #999;
}
</style>
