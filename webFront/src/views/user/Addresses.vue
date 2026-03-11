<template>
  <div class="addresses-page">
    <n-card title="收货地址">
      <template #header-extra>
        <n-button type="primary" @click="openAddModal">添加地址</n-button>
      </template>

      <n-spin :show="loading">
        <div v-if="addresses.length" class="address-list">
          <div v-for="addr in addresses" :key="addr.id" class="address-item">
            <div class="addr-content">
              <div class="addr-header">
                <span class="name">{{ addr.name }}</span>
                <span class="phone">{{ addr.phone }}</span>
                <n-tag v-if="addr.isDefault" type="primary" size="small">默认</n-tag>
              </div>
              <p class="addr-detail">{{ addr.province }}{{ addr.city }}{{ addr.district }}{{ addr.detail }}</p>
            </div>
            <div class="addr-actions">
              <n-button text type="primary" @click="openEditModal(addr)">编辑</n-button>
              <n-button text type="error" @click="handleDelete(addr.id)">删除</n-button>
              <n-button v-if="!addr.isDefault" text @click="setDefault(addr.id)">设为默认</n-button>
            </div>
          </div>
        </div>
        <n-empty v-else description="暂无收货地址" />
      </n-spin>
    </n-card>

    <!-- 添加/编辑地址弹窗 -->
    <n-modal v-model:show="showModal" preset="card" :title="isEdit ? '编辑地址' : '添加地址'" style="width: 500px">
      <n-form ref="formRef" :model="formData" :rules="rules" label-placement="left" label-width="80">
        <n-form-item label="收货人" path="name">
          <n-input v-model:value="formData.name" placeholder="请输入收货人姓名" />
        </n-form-item>
        <n-form-item label="手机号" path="phone">
          <n-input v-model:value="formData.phone" placeholder="请输入手机号" />
        </n-form-item>
        <n-form-item label="省份" path="province">
          <n-input v-model:value="formData.province" placeholder="省份" />
        </n-form-item>
        <n-form-item label="城市" path="city">
          <n-input v-model:value="formData.city" placeholder="城市" />
        </n-form-item>
        <n-form-item label="区县" path="district">
          <n-input v-model:value="formData.district" placeholder="区县" />
        </n-form-item>
        <n-form-item label="详细地址" path="detail">
          <n-input v-model:value="formData.detail" type="textarea" placeholder="详细地址" />
        </n-form-item>
        <n-form-item label="设为默认">
          <n-switch v-model:value="formData.isDefault" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleSave">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getAddresses, createAddress, updateAddress, deleteAddress } from '@/api/user'

// 用户端「收货地址管理」页面
//
// 页面职责：
// - 管理用户的收货地址（增/改/删/设默认）
// - 该数据会被器材商城结算页（views/equipment/Checkout.vue）用于选择收货地址
//
// 数据流：
// onMounted -> fetchAddresses -> getAddresses() -> addresses 渲染
// 用户操作：
// - 新增：createAddress(formData) -> 刷新列表
// - 编辑：updateAddress(id, formData) -> 刷新列表
// - 删除：deleteAddress(id) -> 刷新列表
// - 设默认：updateAddress(id, {isDefault:true}) -> 刷新列表
//
// 关于 isDefault：
// - “默认地址”通常要求同一用户最多只有 1 个
// - 前端只能发起请求，最终的唯一性约束与互斥更新由后端保证（例如把旧默认置为 false）

const loading = ref(false)
const saving = ref(false)
const addresses = ref([])

const showModal = ref(false)
const isEdit = ref(false)
const editId = ref(null)
const formRef = ref(null)
const formData = reactive({
  name: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: false
})

const rules = {
  name: { required: true, message: '请输入收货人姓名' },
  phone: [
    { required: true, message: '请输入手机号' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号' }
  ],
  province: { required: true, message: '请输入省份' },
  city: { required: true, message: '请输入城市' },
  district: { required: true, message: '请输入区县' },
  detail: { required: true, message: '请输入详细地址' }
}

const fetchAddresses = async () => {
  // 拉取地址列表：用于本页展示，也用于结算页进行地址选择。
  loading.value = true
  try {
    const res = await getAddresses()
    addresses.value = res.data || []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  // 重置弹窗表单：用于“新增”时初始化，也用于编辑完成后清空。
  Object.assign(formData, {
    name: '',
    phone: '',
    province: '',
    city: '',
    district: '',
    detail: '',
    isDefault: false
  })
}

const openAddModal = () => {
  // 打开“新增地址”弹窗：复用同一个 modal + form。
  isEdit.value = false
  editId.value = null
  resetForm()
  showModal.value = true
}

const openEditModal = (addr) => {
  // 打开“编辑地址”弹窗：将当前地址信息回填到 formData。
  isEdit.value = true
  editId.value = addr.id
  Object.assign(formData, {
    name: addr.name,
    phone: addr.phone,
    province: addr.province,
    city: addr.city,
    district: addr.district,
    detail: addr.detail,
    isDefault: addr.isDefault
  })
  showModal.value = true
}

const handleSave = async () => {
  // 保存（新增/编辑共用）：
  // - 先做表单校验
  // - 按 isEdit 决定调用 createAddress 或 updateAddress
  // - 成功后关闭弹窗并刷新列表
  try {
    await formRef.value?.validate()
    saving.value = true
    if (isEdit.value) {
      await updateAddress(editId.value, formData)
      window.$message?.success('地址更新成功')
    } else {
      await createAddress(formData)
      window.$message?.success('地址添加成功')
    }
    showModal.value = false
    fetchAddresses()
  } catch (e) {
    if (e?.message) window.$message?.error(e.message)
  } finally {
    saving.value = false
  }
}

const handleDelete = async (id) => {
  // 删除地址：
  // - 这是不可逆操作，通常产品上会加确认弹窗（本项目当前直接删除，仅补注释不改逻辑）
  // - 后端可能会限制：默认地址/正在使用的地址不可删（以实现为准）
  try {
    await deleteAddress(id)
    window.$message?.success('地址已删除')
    fetchAddresses()
  } catch (e) {
    window.$message?.error('删除失败')
  }
}

const setDefault = async (id) => {
  // 设为默认地址：
  // - 这里只传 {isDefault:true}
  // - 旧默认如何处理由后端实现（通常会自动取消旧默认）
  try {
    await updateAddress(id, { isDefault: true })
    window.$message?.success('已设为默认地址')
    fetchAddresses()
  } catch (e) {
    window.$message?.error('操作失败')
  }
}

onMounted(() => {
  fetchAddresses()
})
</script>

<style scoped>
.addresses-page {
  padding: 20px;
  max-width: 800px;
}
.address-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.address-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border: 1px solid #eee;
  border-radius: 8px;
}
.addr-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.addr-header .name {
  font-weight: 500;
}
.addr-header .phone {
  color: #666;
}
.addr-detail {
  margin: 0;
  color: #666;
  font-size: 14px;
}
.addr-actions {
  display: flex;
  gap: 8px;
}
</style>
