<template>
  <n-layout has-sider class="coach-layout">
    <n-layout-sider
      bordered
      collapse-mode="width"
      :collapsed-width="64"
      :width="200"
      :collapsed="collapsed"
      show-trigger
      @collapse="collapsed = true"
      @expand="collapsed = false"
      class="sider"
    >
      <div class="logo" @click="router.push('/coach/courses')">
        <n-icon size="24" color="#18a058">
          <SchoolOutline />
        </n-icon>
        <span v-if="!collapsed" class="logo-text">教练中心</span>
      </div>
      
      <n-menu
        :collapsed="collapsed"
        :collapsed-width="64"
        :collapsed-icon-size="22"
        :options="menuOptions"
        :value="activeMenu"
        @update:value="handleMenuSelect"
      />
    </n-layout-sider>
    
    <n-layout>
      <n-layout-header bordered class="header">
        <div class="header-content">
          <n-breadcrumb>
            <n-breadcrumb-item @click="router.push('/coach/courses')">教练中心</n-breadcrumb-item>
            <n-breadcrumb-item v-if="currentMenuLabel">{{ currentMenuLabel }}</n-breadcrumb-item>
          </n-breadcrumb>
          
          <div class="header-right">
            <n-button quaternary @click="router.push('/')">
              <template #icon>
                <n-icon><HomeOutline /></n-icon>
              </template>
              返回前台
            </n-button>
            
            <n-dropdown :options="userMenuOptions" @select="handleUserMenuSelect">
              <n-button quaternary>
                <n-avatar v-if="authStore.avatarUrl" :src="authStore.avatarUrl" round size="small" class="user-avatar" />
                <n-avatar v-else :src="'/default-avatar.svg'" round size="small" class="user-avatar" />
                <span>{{ authStore.nickname || authStore.username }}</span>
              </n-button>
            </n-dropdown>
          </div>
        </div>
      </n-layout-header>
      
      <n-layout-content class="content">
        <div class="content-inner">
          <router-view />
        </div>
      </n-layout-content>
    </n-layout>
  </n-layout>
</template>

<script setup>
import { ref, computed, h, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { NIcon } from 'naive-ui'
import {
  SchoolOutline,
  HomeOutline,
  BookOutline,
  CalendarOutline,
  VideocamOutline,
  ChatbubblesOutline,
  WalletOutline,
  CashOutline,
  LogOutOutline
} from '@vicons/ionicons5'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const collapsed = ref(false)

const renderIcon = (icon) => () => h(NIcon, null, { default: () => h(icon) })

const menuOptions = [
  { label: '课程管理', key: 'courses', icon: renderIcon(BookOutline) },
  { label: '预约管理', key: 'bookings', icon: renderIcon(CalendarOutline) },
  { label: '视频管理', key: 'videos', icon: renderIcon(VideocamOutline) },
  { label: '咨询管理', key: 'consultations', icon: renderIcon(ChatbubblesOutline) },
  { label: '收入统计', key: 'earnings', icon: renderIcon(WalletOutline) },
  { label: '提现申请', key: 'withdraw', icon: renderIcon(CashOutline) }
]

const activeMenu = computed(() => {
  const path = route.path
  if (path.includes('/coach/courses')) return 'courses'
  if (path.includes('/coach/bookings')) return 'bookings'
  if (path.includes('/coach/videos')) return 'videos'
  if (path.includes('/coach/consultations')) return 'consultations'
  if (path.includes('/coach/earnings')) return 'earnings'
  if (path.includes('/coach/withdraw')) return 'withdraw'
  return 'courses'
})

const currentMenuLabel = computed(() => {
  const opt = menuOptions.find(o => o.key === activeMenu.value)
  return opt?.label || ''
})

const handleMenuSelect = (key) => {
  router.push(`/coach/${key}`)
}

const userMenuOptions = [
  { label: '退出登录', key: 'logout', icon: renderIcon(LogOutOutline) }
]

const handleUserMenuSelect = (key) => {
  if (key === 'logout') {
    authStore.logout()
    router.push('/login')
  }
}

onMounted(() => {
  if (authStore.isLoggedIn && authStore.accessToken) {
    authStore.fetchUserInfo()
  }
})
</script>

<style scoped>
.coach-layout {
  min-height: 100vh;
}

.sider {
  background: #fff;
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  z-index: 101;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  border-bottom: 1px solid #efeff5;
}

.logo-text {
  font-size: 16px;
  font-weight: 600;
  color: #18a058;
}

.header {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(8px);
  position: fixed;
  top: 0;
  right: 0;
  left: 200px;
  width: calc(100% - 200px);
  box-sizing: border-box;
  z-index: 100;
  transition: left 0.3s var(--n-bezier);
  overflow: hidden;
}

.coach-layout :deep(.n-layout-sider--collapsed) ~ .n-layout .header {
  left: 64px;
  width: calc(100% - 64px);
}

.header-content {
  height: 60px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  overflow: hidden;
}

.header-content :deep(.n-breadcrumb) {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  margin-left: 16px;
}

.user-avatar {
  margin-right: 8px;
}

.content {
  margin-left: 200px;
  margin-top: 60px;
  padding: 20px;
  background: linear-gradient(180deg, #f0f2f5 0%, #e8ebef 100%);
  min-height: calc(100vh - 60px);
  transition: margin-left 0.3s var(--n-bezier);
}

.coach-layout :deep(.n-layout-sider--collapsed) ~ .n-layout .content {
  margin-left: 64px;
}

.content-inner {
  width: 100%;
  max-width: 100%;
  min-width: 0;
}

.content-inner > * {
  width: 100%;
  min-width: 0;
}

.content-inner :deep(.n-card) {
  width: 100%;
}
</style>
