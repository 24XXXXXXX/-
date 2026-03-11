<template>
  <div class="admin-users">
    <n-card title="用户管理">
      <template #header-extra>
        <n-button type="primary" @click="$router.push('/admin/users/create')">
          <template #icon><n-icon><AddOutline /></n-icon></template>
          新增用户
        </n-button>
      </template>

      <n-space vertical :size="16">
        <n-space>
          <n-input v-model:value="filters.keyword" placeholder="搜索用户名/手机号" clearable style="width: 200px" @keyup.enter="handleSearch" />
          <n-select v-model:value="filters.role" placeholder="角色" clearable style="width: 120px" :options="roleOptions" />
          <n-select v-model:value="filters.status" placeholder="状态" clearable style="width: 120px" :options="statusOptions" />
          <n-button type="primary" @click="handleSearch">搜索</n-button>
          <n-button @click="handleReset">重置</n-button>
        </n-space>

        <n-data-table :columns="columns" :data="users" :loading="loading" :pagination="pagination" remote @update:page="handlePageChange" />
      </n-space>
    </n-card>

    <n-modal v-model:show="showRoleModal" preset="dialog" title="分配角色">
      <n-checkbox-group v-model:value="selectedRoles">
        <n-space vertical>
          <n-checkbox value="USER">普通用户</n-checkbox>
          <n-checkbox value="COACH">教练</n-checkbox>
          <n-checkbox value="STAFF">员工</n-checkbox>
          <n-checkbox value="ADMIN">管理员</n-checkbox>
        </n-space>
      </n-checkbox-group>
      <template #action>
        <n-button @click="showRoleModal = false">取消</n-button>
        <n-button type="primary" :loading="roleLoading" @click="handleSaveRoles">保存</n-button>
      </template>
    </n-modal>

    <n-modal v-model:show="showPasswordModal" preset="dialog" title="重置密码">
      <n-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules">
        <n-form-item label="新密码" path="password">
          <n-input v-model:value="passwordForm.password" type="password" placeholder="请输入新密码" />
        </n-form-item>
        <n-form-item label="确认密码" path="confirmPassword">
          <n-input v-model:value="passwordForm.confirmPassword" type="password" placeholder="请再次输入密码" />
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="showPasswordModal = false">取消</n-button>
        <n-button type="primary" :loading="passwordLoading" @click="handleResetPassword">确定</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, reactive, h, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NSpace, NTag, useMessage } from 'naive-ui'
import { AddOutline } from '@vicons/ionicons5'
import { getUsers, updateUserStatus, updateUserRoles, resetUserPassword } from '@/api/admin'

const router = useRouter()
const message = useMessage()

const loading = ref(false)
const users = ref([])
const filters = reactive({ keyword: '', role: null, status: null })
const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })

const roleOptions = [
  { label: '普通用户', value: 'USER' },
  { label: '教练', value: 'COACH' },
  { label: '员工', value: 'STAFF' },
  { label: '管理员', value: 'ADMIN' }
]
const statusOptions = [
  { label: '正常', value: 1 },
  { label: '禁用', value: 0 }
]

const showRoleModal = ref(false)
const selectedRoles = ref([])
const currentUserId = ref(null)
const roleLoading = ref(false)

const showPasswordModal = ref(false)
const passwordFormRef = ref(null)
const passwordForm = reactive({ password: '', confirmPassword: '' })
const passwordLoading = ref(false)
const passwordRules = {
  password: { required: true, message: '请输入新密码', trigger: 'blur' },
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: (rule, value) => value === passwordForm.password, message: '两次密码不一致', trigger: 'blur' }
  ]
}

const roleMap = {
  USER: { label: '用户', type: 'default' },
  COACH: { label: '教练', type: 'info' },
  STAFF: { label: '员工', type: 'warning' },
  ADMIN: { label: '管理员', type: 'error' }
}

const columns = [
  { title: 'ID', key: 'id', width: 80 },
  { title: '用户名', key: 'username' },
  { title: '手机号', key: 'phone' },
  { title: '邮箱', key: 'email' },
  {
    title: '角色',
    key: 'roles',
    render: (row) => h(NSpace, { size: 4 }, () => 
      (row.roles || []).map(role => h(NTag, { size: 'small', type: roleMap[role]?.type || 'default' }, () => roleMap[role]?.label || role))
    )
  },
  {
    title: '状态',
    key: 'status',
    render: (row) => h(NTag, { type: row.status === 1 ? 'success' : 'error' }, () => row.status === 1 ? '正常' : '禁用')
  },
  { title: '注册时间', key: 'createdAt', width: 180 },
  {
    title: '操作',
    key: 'actions',
    width: 280,
    render: (row) => h(NSpace, { size: 8 }, () => [
      h(NButton, { size: 'small', onClick: () => router.push(`/admin/users/${row.id}/edit`) }, () => '编辑'),
      h(NButton, { size: 'small', type: row.status === 1 ? 'error' : 'success', onClick: () => handleToggleStatus(row) }, () => row.status === 1 ? '禁用' : '启用'),
      h(NButton, { size: 'small', type: 'info', onClick: () => openRoleModal(row) }, () => '角色'),
      h(NButton, { size: 'small', type: 'warning', onClick: () => openPasswordModal(row) }, () => '重置密码')
    ])
  }
]

const fetchUsers = async () => {
  loading.value = true
  try {
    const res = await getUsers({
      page: pagination.page,
      size: pagination.pageSize,
      keyword: filters.keyword || undefined,
      role: filters.role || undefined,
      status: filters.status
    })
    users.value = res.data?.content || []
    pagination.itemCount = res.data?.totalElements || 0
  } catch (e) {
    message.error('获取用户列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  fetchUsers()
}

const handleReset = () => {
  filters.keyword = ''
  filters.role = null
  filters.status = null
  handleSearch()
}

const handlePageChange = (page) => {
  pagination.page = page
  fetchUsers()
}

const handleToggleStatus = async (row) => {
  try {
    await updateUserStatus(row.id, { status: row.status === 1 ? 0 : 1 })
    message.success('操作成功')
    fetchUsers()
  } catch (e) {
    message.error('操作失败')
  }
}

const openRoleModal = (row) => {
  currentUserId.value = row.id
  selectedRoles.value = row.roles || []
  showRoleModal.value = true
}

const handleSaveRoles = async () => {
  roleLoading.value = true
  try {
    await updateUserRoles(currentUserId.value, { roleCodes: selectedRoles.value })
    message.success('角色更新成功')
    showRoleModal.value = false
    fetchUsers()
  } catch (e) {
    message.error('角色更新失败')
  } finally {
    roleLoading.value = false
  }
}

const openPasswordModal = (row) => {
  currentUserId.value = row.id
  passwordForm.password = ''
  passwordForm.confirmPassword = ''
  showPasswordModal.value = true
}

const handleResetPassword = async () => {
  await passwordFormRef.value?.validate()
  passwordLoading.value = true
  try {
    await resetUserPassword(currentUserId.value, { newPassword: passwordForm.password })
    message.success('密码重置成功')
    showPasswordModal.value = false
  } catch (e) {
    message.error('密码重置失败')
  } finally {
    passwordLoading.value = false
  }
}

onMounted(() => fetchUsers())
</script>

<style scoped>
.admin-users { padding: 20px; }
</style>
