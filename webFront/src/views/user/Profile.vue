<template>
  <div class="profile-page">
    <n-card title="个人资料">
      <n-spin :show="loading">
        <div class="profile-content">
          <div class="avatar-section">
            <n-upload
              :show-file-list="false"
              :custom-request="handleAvatarUpload"
              accept="image/*"
            >
              <n-avatar :src="userInfo.avatarUrl || '/default-avatar.svg'" :size="100" round />
              <div class="avatar-overlay">
                <n-icon><CameraOutline /></n-icon>
              </div>
            </n-upload>
            <p class="avatar-tip">点击更换头像</p>
          </div>

          <n-form
            ref="formRef"
            :model="formData"
            :rules="rules"
            label-placement="left"
            label-width="100"
            style="max-width: 500px"
          >
            <n-form-item label="用户名">
              <n-input :value="userInfo.username" disabled />
            </n-form-item>
            <n-form-item label="昵称" path="nickname">
              <n-input v-model:value="formData.nickname" placeholder="请输入昵称" />
            </n-form-item>
            <n-form-item label="手机号" path="phone">
              <n-input v-model:value="formData.phone" placeholder="请输入手机号" />
            </n-form-item>
            <n-form-item label="邮箱" path="email">
              <n-input v-model:value="formData.email" placeholder="请输入邮箱" />
            </n-form-item>
            <n-form-item label="性别" path="gender">
              <n-radio-group v-model:value="formData.gender">
                <n-radio value="male">男</n-radio>
                <n-radio value="female">女</n-radio>
                <n-radio value="unknown">保密</n-radio>
              </n-radio-group>
            </n-form-item>
            <n-form-item>
              <n-button type="primary" :loading="saving" @click="handleSave">保存修改</n-button>
            </n-form-item>
          </n-form>
        </div>
      </n-spin>
    </n-card>

    <n-card title="修改密码" style="margin-top: 20px">
      <n-form
        ref="pwdFormRef"
        :model="pwdForm"
        :rules="pwdRules"
        label-placement="left"
        label-width="100"
        style="max-width: 500px"
      >
        <n-form-item label="当前密码" path="oldPassword">
          <n-input v-model:value="pwdForm.oldPassword" type="password" placeholder="请输入当前密码" />
        </n-form-item>
        <n-form-item label="新密码" path="newPassword">
          <n-input v-model:value="pwdForm.newPassword" type="password" placeholder="请输入新密码" />
        </n-form-item>
        <n-form-item label="确认密码" path="confirmPassword">
          <n-input v-model:value="pwdForm.confirmPassword" type="password" placeholder="请再次输入新密码" />
        </n-form-item>
        <n-form-item>
          <n-button type="primary" :loading="changingPwd" @click="handleChangePwd">修改密码</n-button>
        </n-form-item>
      </n-form>
    </n-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { CameraOutline } from '@vicons/ionicons5'
import { getMe, updateMe, uploadAvatar, changePassword } from '@/api/user'
import { useAuthStore } from '@/stores/auth'

// 用户端「个人资料」页面
//
// 页面职责：
// - 展示并编辑用户资料（昵称/手机号/邮箱/性别），用户名只读
// - 支持上传头像（n-upload custom-request -> uploadAvatar）
// - 支持修改密码（需要输入旧密码，属于安全敏感操作）
//
// 数据流：
// onMounted -> fetchUserInfo -> getMe() -> userInfo
// - 把 userInfo 回填到 formData，作为可编辑表单
//
// 与全局登录态（authStore）的联动：
// - 头像上传成功后：同时更新 userInfo 与 authStore（让 Header/侧边栏头像立即刷新）
// - 基本资料保存成功后：updateMe(formData) 后也写回 authStore
//   注意：authStore 只是前端缓存，最终以服务端数据为准

const authStore = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const changingPwd = ref(false)

const userInfo = ref({})
const formRef = ref(null)
const formData = reactive({
  nickname: '',
  phone: '',
  email: '',
  gender: 'unknown'
})

const rules = {
  phone: { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' },
  email: { type: 'email', message: '请输入正确的邮箱', trigger: 'blur' }
}

const pwdFormRef = ref(null)
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const pwdRules = {
  oldPassword: { required: true, message: '请输入当前密码' },
  newPassword: [
    { required: true, message: '请输入新密码' },
    { min: 6, message: '密码至少6位' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码' },
    {
      validator: (rule, value) => value === pwdForm.newPassword,
      message: '两次密码不一致',
      trigger: 'blur'
    }
  ]
}

const fetchUserInfo = async () => {
  // 拉取当前登录用户资料。
  // 说明：这里兼容了后端可能返回 {data:{...}} 或直接 {...} 的两种结构。
  loading.value = true
  try {
    const res = await getMe()
    const data = res?.data?.data || res?.data || {}
    userInfo.value = data
    formData.nickname = userInfo.value.nickname || ''
    formData.phone = userInfo.value.phone || ''
    formData.email = userInfo.value.email || ''
    formData.gender = userInfo.value.gender || 'unknown'
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const handleAvatarUpload = async ({ file }) => {
  // 头像上传：
  // - n-upload 使用 custom-request 接管上传流程
  // - uploadAvatar(file) 通常是 multipart/form-data 上传
  // - 成功后把新 avatarUrl 写回 userInfo + authStore
  try {
    const res = await uploadAvatar(file.file)
    const avatarUrl = res.data?.url || res.data
    userInfo.value.avatarUrl = avatarUrl
    authStore.updateUserInfo({ avatarUrl })
    window.$message?.success('头像更新成功')
  } catch (e) {
    window.$message?.error('头像上传失败')
  }
}

const handleSave = async () => {
  // 保存基本资料：
  // - 先校验表单（手机号/邮箱格式）
  // - updateMe 属于“修改个人信息”接口，后端会做登录态校验
  // - 保存成功后同步更新 authStore（提升前端体验）
  try {
    await formRef.value?.validate()
    saving.value = true
    await updateMe(formData)
    window.$message?.success('保存成功')
    // 更新store中的用户信息
    authStore.updateUserInfo(formData)
  } catch (e) {
    if (e?.message) window.$message?.error(e.message)
  } finally {
    saving.value = false
  }
}

const handleChangePwd = async () => {
  // 修改密码：安全敏感操作。
  // - 前端只提交 oldPassword/newPassword
  // - confirmPassword 仅用于前端校验“两次输入一致”，不提交给后端
  // - 成功后清空密码输入框
  try {
    await pwdFormRef.value?.validate()
    changingPwd.value = true
    await changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    window.$message?.success('密码修改成功')
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
  } catch (e) {
    window.$message?.error(e?.response?.data?.message || '密码修改失败')
  } finally {
    changingPwd.value = false
  }
}

onMounted(() => {
  fetchUserInfo()
})
</script>

<style scoped>
.profile-page {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
}
.profile-content {
  display: flex;
  flex-direction: column;
  gap: 30px;
}
.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
}
.avatar-section :deep(.n-upload) {
  position: relative;
  cursor: pointer;
}
.avatar-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 24px;
  opacity: 0;
  transition: opacity 0.3s;
}
.avatar-section :deep(.n-upload):hover .avatar-overlay {
  opacity: 1;
}
.avatar-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #999;
}
</style>
