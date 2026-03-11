<template>
  <div class="admin-user-form">
    <n-card :title="isEdit ? '编辑用户' : '新增用户'">
      <template #header-extra>
        <n-button @click="$router.back()">返回</n-button>
      </template>

      <n-spin :show="loading">
        <n-form ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="100" style="max-width: 600px; margin: 0 auto">
          <n-form-item label="用户名" path="username">
            <n-input v-model:value="form.username" placeholder="请输入用户名" :disabled="isEdit" />
          </n-form-item>
          <n-form-item v-if="!isEdit" label="密码" path="password">
            <n-input v-model:value="form.password" type="password" placeholder="请输入密码" />
          </n-form-item>
          <n-form-item label="手机号" path="phone">
            <n-input v-model:value="form.phone" placeholder="请输入手机号" />
          </n-form-item>
          <n-form-item label="邮箱" path="email">
            <n-input v-model:value="form.email" placeholder="请输入邮箱" />
          </n-form-item>
          <n-form-item label="昵称" path="nickname">
            <n-input v-model:value="form.nickname" placeholder="请输入昵称" />
          </n-form-item>
          <n-form-item label="角色" path="roles">
            <n-checkbox-group v-model:value="form.roles">
              <n-space>
                <n-checkbox value="USER">普通用户</n-checkbox>
                <n-checkbox value="COACH">教练</n-checkbox>
                <n-checkbox value="STAFF">员工</n-checkbox>
                <n-checkbox value="ADMIN">管理员</n-checkbox>
              </n-space>
            </n-checkbox-group>
          </n-form-item>
          <n-form-item label="状态" path="status">
            <n-switch v-model:value="form.status" :checked-value="1" :unchecked-value="0">
              <template #checked>启用</template>
              <template #unchecked>禁用</template>
            </n-switch>
          </n-form-item>

          <n-divider v-if="form.roles?.includes('STAFF')">员工信息</n-divider>
          <template v-if="form.roles?.includes('STAFF')">
            <n-form-item label="姓名" path="staffProfile.realName">
              <n-input v-model:value="form.staffProfile.realName" placeholder="请输入姓名" />
            </n-form-item>
            <n-form-item label="部门" path="staffProfile.department">
              <n-input v-model:value="form.staffProfile.department" placeholder="请输入部门" />
            </n-form-item>
            <n-form-item label="职位" path="staffProfile.position">
              <n-input v-model:value="form.staffProfile.position" placeholder="请输入职位" />
            </n-form-item>
            <n-form-item label="区域" path="staffProfile.region">
              <n-input v-model:value="form.staffProfile.region" placeholder="请输入区域" />
            </n-form-item>
          </template>

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
import { getUserDetail, createUser, updateUser, updateStaffProfile, updateUserRoles } from '@/api/admin'

const route = useRoute()
const router = useRouter()
const message = useMessage()

const isEdit = computed(() => !!route.params.id)
const loading = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const form = reactive({
  username: '',
  password: '',
  phone: '',
  email: '',
  nickname: '',
  roles: ['USER'],
  status: 1,
  staffProfile: { realName: '', department: '', position: '', region: '' }
})

const rules = {
  username: { required: true, message: '请输入用户名', trigger: 'blur' },
  password: { required: !isEdit.value, message: '请输入密码', trigger: 'blur' },
  phone: { pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' },
  email: { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
}

const fetchUser = async () => {
  if (!isEdit.value) return
  loading.value = true
  try {
    const res = await getUserDetail(route.params.id)
    const data = res.data || {}
    Object.assign(form, {
      username: data.username,
      phone: data.phone,
      email: data.email,
      nickname: data.nickname,
      roles: data.roles || ['USER'],
      status: data.status,
      staffProfile: {
        realName: data.staffRealName || '',
        department: data.staffDepartment || '',
        position: data.staffPosition || '',
        region: data.staffRegion || ''
      }
    })
  } catch (e) {
    message.error('获取用户信息失败')
  } finally {
    loading.value = false
  }
}

const handleSubmit = async () => {
  await formRef.value?.validate()
  if (!Array.isArray(form.roles) || form.roles.length === 0) {
    message.warning('请至少选择一个角色')
    return
  }
  submitting.value = true
  try {
    const data = {
      username: form.username,
      phone: form.phone,
      email: form.email,
      nickname: form.nickname,
      status: form.status
    }
    if (!isEdit.value) {
      data.password = form.password
      const payload = {
        ...data,
        roleCodes: form.roles
      }
      if (form.roles?.includes('STAFF')) {
        payload.staffRealName = form.staffProfile.realName
        payload.staffDepartment = form.staffProfile.department
        payload.staffPosition = form.staffProfile.position
        payload.staffRegion = form.staffProfile.region
      }
      await createUser(payload)
    } else {
      await updateUser(route.params.id, data)
      await updateUserRoles(route.params.id, { roleCodes: form.roles })
      if (form.roles?.includes('STAFF')) {
        await updateStaffProfile(route.params.id, {
          realName: form.staffProfile.realName,
          department: form.staffProfile.department,
          position: form.staffProfile.position,
          region: form.staffProfile.region
        })
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

onMounted(() => fetchUser())
</script>

<style scoped>
.admin-user-form { padding: 20px; }
</style>
