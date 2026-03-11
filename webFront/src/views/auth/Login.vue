<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-header">
        <n-icon size="36" color="#18a058">
          <BasketballOutline />
        </n-icon>
        <h1>社区运动场地管理系统</h1>
      </div>

      <n-form ref="formRef" :model="form" :rules="rules" class="login-form" :show-label="false">
        <n-form-item path="username">
          <n-input
            v-model:value="form.username"
            placeholder="用户名"
            @keyup.enter="handleLogin"
          >
            <template #prefix>
              <n-icon><PersonOutline /></n-icon>
            </template>
          </n-input>
        </n-form-item>

        <n-form-item path="password">
          <n-input
            v-model:value="form.password"
            type="password"
            placeholder="密码"
            show-password-on="click"
            @keyup.enter="handleLogin"
          >
            <template #prefix>
              <n-icon><LockClosedOutline /></n-icon>
            </template>
          </n-input>
        </n-form-item>

        <div class="form-actions">
          <n-checkbox v-model:checked="rememberMe" size="small">记住我</n-checkbox>
          <n-button
            text
            type="primary"
            size="small"
            @click="router.push({ path: '/forgot-password', query: { username: form.username } })"
          >
            忘记密码？
          </n-button>
        </div>

        <n-button
          type="primary"
          block
          :loading="loading"
          @click="handleLogin"
        >
          登录
        </n-button>

        <div class="register-link">
          还没有账号？
          <n-button text type="primary" size="small" @click="router.push('/register')">
            立即注册
          </n-button>
        </div>
      </n-form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'
import { BasketballOutline, PersonOutline, LockClosedOutline } from '@vicons/ionicons5'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const authStore = useAuthStore()

const formRef = ref(null)
const loading = ref(false)
const rememberMe = ref(true)

const form = ref({
  username: '',
  password: ''
})

const rules = {
  username: {
    required: true,
    message: '请输入用户名',
    trigger: 'blur'
  },
  password: {
    required: true,
    message: '请输入密码',
    trigger: 'blur'
  }
}

function getOrCreateDeviceId() {
  const key = 'deviceId'
  let deviceId = localStorage.getItem(key)
  if (!deviceId) {
    deviceId = `${Date.now()}_${Math.random().toString(16).slice(2)}`
    localStorage.setItem(key, deviceId)
  }
  return deviceId
}

const handleLogin = async () => {
  try {
    await formRef.value?.validate()
  } catch (e) {
    return
  }

  loading.value = true
  try {
    const deviceId = getOrCreateDeviceId()
    await authStore.login({
      username: form.value.username,
      password: form.value.password,
      deviceId
    })

    message.success('登录成功')

    // 跳转到之前的页面或首页
    const redirect = route.query.redirect || '/'
    router.push(redirect)
  } catch (e) {
    const msg = e?.response?.data?.message || '登录失败，请检查用户名和密码'
    message.error(msg)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 16px;
}

.login-container {
  position: relative;
  width: 100%;
  max-width: 360px;
  background: #fff;
  border-radius: 12px;
  padding: 28px 32px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.25);
}

.login-header {
  text-align: center;
  margin-bottom: 20px;
}

.login-header h1 {
  font-size: 18px;
  color: #333;
  margin: 10px 0 0;
}

.login-form :deep(.n-form-item) {
  margin-bottom: 16px;
}

.form-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  margin-top: -4px;
}

.register-link {
  text-align: center;
  margin-top: 16px;
  color: #666;
  font-size: 13px;
}
</style>
