<template>
  <div class="register-page">
    <div class="register-container">
      <div class="register-header">
        <n-icon size="36" color="#18a058">
          <BasketballOutline />
        </n-icon>
        <h1>创建新账号</h1>
      </div>

      <n-form ref="formRef" :model="form" :rules="rules" class="register-form" :show-label="false">
        <n-form-item path="username">
          <n-input v-model:value="form.username" placeholder="用户名（4-20位字母数字）">
            <template #prefix>
              <n-icon><PersonOutline /></n-icon>
            </template>
          </n-input>
        </n-form-item>

        <n-form-item path="password">
          <n-input
            v-model:value="form.password"
            type="password"
            placeholder="密码（至少6位）"
            show-password-on="click"
          >
            <template #prefix>
              <n-icon><LockClosedOutline /></n-icon>
            </template>
          </n-input>
        </n-form-item>

        <n-form-item path="confirmPassword">
          <n-input
            v-model:value="form.confirmPassword"
            type="password"
            placeholder="确认密码"
            show-password-on="click"
          >
            <template #prefix>
              <n-icon><LockClosedOutline /></n-icon>
            </template>
          </n-input>
        </n-form-item>

        <n-form-item path="phone">
          <n-input v-model:value="form.phone" placeholder="手机号（选填）">
            <template #prefix>
              <n-icon><CallOutline /></n-icon>
            </template>
          </n-input>
        </n-form-item>

        <n-form-item path="email">
          <n-input v-model:value="form.email" placeholder="邮箱（选填，用于找回密码）">
            <template #prefix>
              <n-icon><MailOutline /></n-icon>
            </template>
          </n-input>
        </n-form-item>

        <n-button type="primary" block :loading="loading" @click="handleRegister">
          注册
        </n-button>

        <div class="login-link">
          已有账号？
          <n-button text type="primary" size="small" @click="router.push('/login')">
            立即登录
          </n-button>
        </div>
      </n-form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { registerApi } from '@/api/auth'
import {
  BasketballOutline,
  PersonOutline,
  LockClosedOutline,
  CallOutline,
  MailOutline
} from '@vicons/ionicons5'

const router = useRouter()
const message = useMessage()

const formRef = ref(null)
const loading = ref(false)

const form = ref({
  username: '',
  password: '',
  confirmPassword: '',
  phone: '',
  email: ''
})

const validatePasswordSame = (rule, value) => {
  return value === form.value.password
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 20, message: '用户名长度为4-20位', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名只能包含字母、数字和下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validatePasswordSame, message: '两次输入的密码不一致', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ]
}

// 英文错误消息翻译映射
const errorMsgMap = {
  'username already exists': '用户名已经被注册',
  'phone already exists': '手机号已经被注册',
  'email already exists': '邮箱已经被注册',
  'Username already exists': '用户名已经被注册',
  'Phone already exists': '手机号已经被注册',
  'Email already exists': '邮箱已经被注册'
}

const translateErrorMsg = (msg) => {
  if (!msg) return '注册失败，请稍后重试'
  // 精确匹配
  if (errorMsgMap[msg]) return errorMsgMap[msg]
  // 模糊匹配
  const lowerMsg = msg.toLowerCase()
  if (lowerMsg.includes('username') && lowerMsg.includes('exist')) return '用户名已经被注册'
  if (lowerMsg.includes('phone') && lowerMsg.includes('exist')) return '手机号已经被注册'
  if (lowerMsg.includes('email') && lowerMsg.includes('exist')) return '邮箱已经被注册'
  return msg
}

const handleRegister = async () => {
  try {
    await formRef.value?.validate()
  } catch (e) {
    return
  }

  loading.value = true
  try {
    await registerApi({
      username: form.value.username,
      password: form.value.password,
      phone: form.value.phone || undefined,
      email: form.value.email || undefined
    })

    message.success('注册成功，请登录')
    router.push('/login')
  } catch (e) {
    const rawMsg = e?.response?.data?.msg || e?.response?.data?.message || ''
    const msg = translateErrorMsg(rawMsg)
    message.error(msg)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 16px;
}

.register-container {
  position: relative;
  width: 100%;
  max-width: 360px;
  background: #fff;
  border-radius: 12px;
  padding: 24px 32px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.25);
}

.register-header {
  text-align: center;
  margin-bottom: 16px;
}

.register-header h1 {
  font-size: 18px;
  color: #333;
  margin: 10px 0 0;
}

.register-form :deep(.n-form-item) {
  margin-bottom: 14px;
}

.login-link {
  text-align: center;
  margin-top: 14px;
  color: #666;
  font-size: 13px;
}
</style>
