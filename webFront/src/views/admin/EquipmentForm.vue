<template>
  <div class="admin-equipment-form">
    <n-card :title="isEdit ? '编辑器材' : '新增器材'">
      <template #header-extra>
        <n-button @click="$router.back()">返回</n-button>
      </template>

      <n-spin :show="loading">
        <n-form ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="100" style="max-width: 700px; margin: 0 auto">
          <n-form-item label="器材名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入器材名称" />
          </n-form-item>
          <n-form-item label="分类" path="categoryId">
            <n-select
              v-model:value="form.categoryId"
              placeholder="请选择分类"
              :options="categoryOptions"
              filterable
              :show-arrow="true"
            />
          </n-form-item>
          <n-form-item label="价格" path="price">
            <n-input-number v-model:value="form.price" :min="0" :precision="2" placeholder="请输入价格" style="width: 200px" />
            <span style="margin-left: 8px">元</span>
          </n-form-item>
          <n-form-item label="原价" path="originalPrice">
            <n-input-number v-model:value="form.originalPrice" :min="0" :precision="2" placeholder="请输入原价（可选）" style="width: 200px" />
            <span style="margin-left: 8px">元</span>
          </n-form-item>
          <n-form-item label="库存" path="stock">
            <n-input-number v-model:value="form.stock" :min="0" placeholder="请输入库存" style="width: 200px" />
          </n-form-item>
          <n-form-item label="品牌" path="brand">
            <n-input v-model:value="form.brand" placeholder="请输入品牌" />
          </n-form-item>
          <n-form-item label="规格" path="specs">
            <n-input v-model:value="form.specs" placeholder="请输入规格" />
          </n-form-item>
          <n-form-item label="描述" path="description">
            <n-input v-model:value="form.description" type="textarea" placeholder="请输入器材描述" :rows="4" />
          </n-form-item>
          <n-form-item label="状态" path="status">
            <n-select v-model:value="form.status" :options="statusOptions" style="width: 150px" />
          </n-form-item>
          <n-form-item label="封面图片">
            <n-upload
              :action="uploadAction"
              :headers="uploadHeaders"
              name="file"
              :default-upload="isEdit"
              list-type="image-card"
              v-model:file-list="fileList"
              :max="1"
              accept="image/*"
              @finish="handleUploadFinish"
              @before-upload="handleBeforeUpload"
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
import { getAdminEquipmentDetail, getCategories, createEquipment, updateEquipment, uploadEquipmentCover } from '@/api/equipment'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const auth = useAuthStore()

const isEdit = computed(() => !!route.params.id)
const loading = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const categoryOptions = ref([])
const fileList = ref([])
const pendingFile = ref(null)

const uploadAction = computed(() => isEdit.value ? `/api/equipments/${route.params.id}/cover` : '')
const uploadHeaders = computed(() => auth.accessToken ? ({ Authorization: `Bearer ${auth.accessToken}` }) : ({}))

const form = reactive({
  name: '',
  categoryId: null,
  price: null,
  originalPrice: null,
  stock: 0,
  brand: '',
  specs: '',
  description: '',
  status: 'ON_SALE'
})

const statusOptions = [
  { label: '上架', value: 'ON_SALE' },
  { label: '下架', value: 'OFF_SALE' }
]

const rules = {
  name: { required: true, message: '请输入器材名称', trigger: 'blur' },
  categoryId: { required: true, type: 'number', message: '请选择分类', trigger: 'change' },
  price: { required: true, type: 'number', message: '请输入价格', trigger: 'blur' },
  stock: { required: true, type: 'number', message: '请输入库存', trigger: 'blur' }
}

const fetchCategories = async () => {
  try {
    const res = await getCategories()
    categoryOptions.value = (res.data || []).map(c => ({ label: c.name, value: c.id }))
  } catch (e) {
    console.error(e)
  }
}

const fetchEquipment = async () => {
  if (!isEdit.value) return
  loading.value = true
  try {
    const res = await getAdminEquipmentDetail(route.params.id)
    const data = res.data || {}
    Object.assign(form, {
      name: data.name,
      categoryId: data.categoryId,
      price: data.price,
      originalPrice: data.originalPrice,
      stock: data.stock,
      brand: data.brand,
      specs: data.specs,
      description: data.description,
      status: data.status
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
    message.error('获取器材信息失败')
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
    const url = res?.url ?? res?.data?.url ?? res?.data
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
      await updateEquipment(route.params.id, data)
    } else {
      const res = await createEquipment(data)
      const equipmentId = res.data?.id
      if (equipmentId && pendingFile.value) {
        await uploadEquipmentCover(equipmentId, pendingFile.value)
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
  fetchCategories()
  fetchEquipment()
})
</script>

<style scoped>
.admin-equipment-form { padding: 20px; }
</style>
