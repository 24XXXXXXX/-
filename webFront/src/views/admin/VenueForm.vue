<template>
  <div class="admin-venue-form">
    <n-card :title="isEdit ? '编辑场地' : '新增场地'">
      <template #header-extra>
        <n-button @click="$router.back()">返回</n-button>
      </template>

      <n-spin :show="loading">
        <n-form ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="100" style="max-width: 700px; margin: 0 auto">
          <n-form-item label="场地名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入场地名称" />
          </n-form-item>
          <n-form-item label="场地类型" path="typeId">
            <n-select
              v-model:value="form.typeId"
              placeholder="请选择场地类型"
              :options="typeOptions"
              filterable
              :show-arrow="true"
            />
          </n-form-item>
          <n-form-item label="地址" path="address">
            <n-input v-model:value="form.address" placeholder="请输入场地地址" />
          </n-form-item>
          <n-form-item label="区域" path="area">
            <n-input v-model:value="form.area" placeholder="请输入场地区域，如A区" />
          </n-form-item>
          <n-form-item label="规格" path="spec">
            <n-input v-model:value="form.spec" placeholder="请输入规格，如标准/半场等" />
          </n-form-item>
          <n-form-item label="开放时间" path="openTimeDesc">
            <n-input v-model:value="form.openTimeDesc" placeholder="请输入开放时间描述" />
          </n-form-item>
          <n-form-item label="价格(元/小时)" path="pricePerHour">
            <n-input-number v-model:value="form.pricePerHour" :min="0" placeholder="请输入价格" style="width: 200px" />
          </n-form-item>
          <n-form-item label="联系电话" path="contactPhone">
            <n-input v-model:value="form.contactPhone" placeholder="请输入联系电话" />
          </n-form-item>
          <n-form-item label="描述" path="description">
            <n-input v-model:value="form.description" type="textarea" placeholder="请输入场地描述" :rows="4" />
          </n-form-item>
          <n-form-item label="状态" path="status">
            <n-select v-model:value="form.status" :options="statusOptions" style="width: 150px" />
          </n-form-item>
          <n-form-item label="场地图片">
            <n-upload
              :action="uploadAction"
              :headers="uploadHeaders"
              name="files"
              :default-upload="isEdit"
              list-type="image-card"
              v-model:file-list="fileList"
              :max="5"
              accept="image/*"
              @finish="handleUploadFinish"
            >
              点击上传
            </n-upload>
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
import { getVenueDetail, getVenueTypes, createVenue, updateVenue, uploadVenuePhotos } from '@/api/venue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const auth = useAuthStore()

const isEdit = computed(() => !!route.params.id)
const loading = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const typeOptions = ref([])
const fileList = ref([])

const uploadAction = computed(() => isEdit.value ? `/api/venues/${route.params.id}/photos` : '')
const uploadHeaders = computed(() => auth.accessToken ? ({ Authorization: `Bearer ${auth.accessToken}` }) : ({}))

const form = reactive({
  name: '',
  typeId: null,
  area: '',
  address: '',
  spec: '',
  openTimeDesc: '',
  pricePerHour: 0,
  contactPhone: '',
  description: '',
  status: 'ACTIVE'
})

const statusOptions = [
  { label: '可用', value: 'ACTIVE' },
  { label: '维护中', value: 'MAINTENANCE' },
  { label: '已停用', value: 'DISABLED' }
]

const rules = {
  name: { required: true, message: '请输入场地名称', trigger: 'blur' },
  typeId: { required: true, type: 'number', message: '请选择场地类型', trigger: 'change' },
  address: { required: true, message: '请输入场地地址', trigger: 'blur' }
}

const fetchTypes = async () => {
  try {
    const res = await getVenueTypes()
    typeOptions.value = (res.data || []).map(t => ({ label: t.name, value: t.id }))
  } catch (e) {
    console.error(e)
  }
}

const fetchVenue = async () => {
  if (!isEdit.value) return
  loading.value = true
  try {
    const res = await getVenueDetail(route.params.id)
    const data = res.data || {}
    Object.assign(form, {
      name: data.name,
      typeId: data.typeId,
      area: data.area,
      address: data.address,
      spec: data.spec,
      openTimeDesc: data.openTimeDesc,
      pricePerHour: data.pricePerHour,
      contactPhone: data.contactPhone,
      description: data.description,
      status: data.status
    })
    const urls = Array.isArray(data.coverUrls) ? data.coverUrls : (data.coverUrl ? [data.coverUrl] : [])
    if (urls.length) {
      fileList.value = urls.map((url, idx) => ({
        id: idx,
        name: `photo-${idx}`,
        status: 'finished',
        url
      }))
    }
  } catch (e) {
    message.error('获取场地信息失败')
  } finally {
    loading.value = false
  }
}

const handleUploadFinish = ({ file, event }) => {
  try {
    const res = JSON.parse(event.target.response)
    const urls = res?.data ?? res
    const url = Array.isArray(urls) ? urls[0] : urls
    file.url = url
    return file
  } catch (e) {
    return file
  }
}

const handleSubmit = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    const data = { ...form }
    
    if (isEdit.value) {
      await updateVenue(route.params.id, data)
    } else {
      const res = await createVenue(data)
      const venueId = res.data?.id
      // 上传图片
      if (venueId && fileList.value.length) {
        const files = fileList.value.map(f => f.file).filter(Boolean)
        if (files.length) {
          await uploadVenuePhotos(venueId, files)
        }
      }
    }
    message.success('保存成功')
    router.back()
  } catch (e) {
    message.error(e.response?.data?.message || e.response?.data?.msg || '保存失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  fetchTypes()
  fetchVenue()
})
</script>

<style scoped>
.admin-venue-form { padding: 20px; }
</style>
