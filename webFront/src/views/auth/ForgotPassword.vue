<template>
  <div class="forgot-page">
    <div class="forgot-container">
      <div class="forgot-header">
        <n-icon size="36" color="#18a058">
          <BasketballOutline />
        </n-icon>
        <h1>找回密码</h1>
        <p>{{ step === 1 ? '请输入您的注册邮箱（可选填账号）' : '请输入验证码和新密码' }}</p>
      </div>

      <!-- 步骤1：发送验证码 -->
      <n-form v-if="step === 1" ref="emailFormRef" :model="emailForm" :rules="emailRules" class="forgot-form" :show-label="false">
        <n-form-item path="username">
          <n-input
            v-model:value="emailForm.username"
            placeholder="登录账号（可选）"
            @keyup.enter="handleSendCode"
          >
            <template #prefix>
              <n-icon><PersonOutline /></n-icon>
            </template>
          </n-input>
        </n-form-item>

        <n-form-item path="email">
          <n-input
            v-model:value="emailForm.email"
            placeholder="注册时使用的邮箱"
            @keyup.enter="handleSendCode"
          >
            <template #prefix>
              <n-icon><MailOutline /></n-icon>
            </template>
          </n-input>
        </n-form-item>

        <n-button type="primary" block :loading="sendingCode" @click="handleSendCode">
          发送验证码
        </n-button>
      </n-form>

      <!-- 步骤2：重置密码 -->
      <n-form v-else ref="resetFormRef" :model="resetForm" :rules="resetRules" class="forgot-form" :show-label="false">
        <n-form-item>
          <n-input :value="emailForm.email" disabled>
            <template #prefix>
              <n-icon><MailOutline /></n-icon>
            </template>
          </n-input>
        </n-form-item>

        <n-form-item path="verificationCode">
          <n-input v-model:value="resetForm.verificationCode" placeholder="邮箱验证码">
            <template #prefix>
              <n-icon><KeyOutline /></n-icon>
            </template>
          </n-input>
        </n-form-item>

        <n-form-item path="newPassword">
          <n-input
            v-model:value="resetForm.newPassword"
            type="password"
            placeholder="新密码（至少6位）"
            show-password-on="click"
          >
            <template #prefix>
              <n-icon><LockClosedOutline /></n-icon>
            </template>
          </n-input>
        </n-form-item>

        <n-form-item path="confirmPassword">
          <n-input
            v-model:value="resetForm.confirmPassword"
            type="password"
            placeholder="确认新密码"
            show-password-on="click"
          >
            <template #prefix>
              <n-icon><LockClosedOutline /></n-icon>
            </template>
          </n-input>
        </n-form-item>

        <n-space vertical :size="10">
          <n-button type="primary" block :loading="resetting" @click="handleReset">
            重置密码
          </n-button>
          <n-button block :disabled="countdown > 0" @click="handleSendCode">
            {{ countdown > 0 ? `重新发送验证码 (${countdown}s)` : '重新发送验证码' }}
          </n-button>
        </n-space>
      </n-form>

      <div class="back-link">
        <n-button text type="primary" size="small" @click="router.push('/login')">
          <template #icon>
            <n-icon><ArrowBackOutline /></n-icon>
          </template>
          返回登录
        </n-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useMessage } from 'naive-ui'
import { sendPasswordResetCode, confirmPasswordReset } from '@/api/auth'
import {
  BasketballOutline,
  MailOutline,
  PersonOutline,
  KeyOutline,
  LockClosedOutline,
  ArrowBackOutline
} from '@vicons/ionicons5'

const router = useRouter()
const route = useRoute()
const message = useMessage()

const step = ref(1)
const sendingCode = ref(false)
const resetting = ref(false)
const countdown = ref(0)
let countdownTimer = null

const emailFormRef = ref(null)
const resetFormRef = ref(null)

const emailForm = ref({
  username: '',
  email: ''
})

const resetForm = ref({
  verificationCode: '',
  newPassword: '',
  confirmPassword: ''
})

const emailRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ]
}

onMounted(() => {
  const u = route?.query?.username
  if (typeof u === 'string' && u.trim()) {
    emailForm.value.username = u.trim()
  }
})

const validatePasswordSame = (rule, value) => {
  return value === resetForm.value.newPassword
}

const resetRules = {
  verificationCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validatePasswordSame, message: '两次输入的密码不一致', trigger: 'blur' }
  ]
}

const startCountdown = () => {
  countdown.value = 60
  countdownTimer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(countdownTimer)
    }
  }, 1000)
}

const handleSendCode = async () => {
  if (step.value === 1) {
    try {
      await emailFormRef.value?.validate()
    } catch (e) {
      return
    }
  }

  sendingCode.value = true
  try {
    await sendPasswordResetCode({
      username: emailForm.value.username,
      email: emailForm.value.email
    })
    message.success('验证码已发送到您的邮箱')
    step.value = 2
    startCountdown()
  } catch (e) {
    const msg = e?.response?.data?.message || '发送失败，请检查邮箱是否正确'
    message.error(msg)
  } finally {
    sendingCode.value = false
  }
}

const handleReset = async () => {
  try {
    await resetFormRef.value?.validate()
  } catch (e) {
    return
  }

  resetting.value = true
  try {
    await confirmPasswordReset({
      email: emailForm.value.email,
      verificationCode: resetForm.value.verificationCode,
      newPassword: resetForm.value.newPassword
    })
    message.success('密码重置成功，请使用新密码登录')
    router.push('/login')
  } catch (e) {
    const msg = e?.response?.data?.message || '重置失败，请检查验证码是否正确'
    message.error(msg)
  } finally {
    resetting.value = false
  }
}

onUnmounted(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
})
</script>

<style scoped>
.forgot-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 16px;
}

.forgot-container {
  position: relative;
  width: 100%;
  max-width: 360px;
  background: #fff;
  border-radius: 12px;
  padding: 24px 32px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.25);
}

.forgot-header {
  text-align: center;
  margin-bottom: 16px;
}

.forgot-header h1 {
  font-size: 18px;
  color: #333;
  margin: 10px 0 4px;
}

.forgot-header p {
  color: #999;
  font-size: 13px;
  margin: 0;
}

.forgot-form :deep(.n-form-item) {
  margin-bottom: 14px;
}

.back-link {
  text-align: center;
  margin-top: 14px;
}
</style>
