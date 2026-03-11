<template>
  <n-layout class="user-layout">
    <n-layout-header bordered class="header">
      <div class="header-content">
        <div class="logo" @click="router.push('/')">
          <n-icon size="28" color="#18a058">
            <BasketballOutline />
          </n-icon>
          <span class="logo-text">社区运动</span>
        </div>
        
        <n-menu
          mode="horizontal"
          :value="activeMenu"
          :options="menuOptions"
          class="nav-menu"
        />
        
        <div class="header-right">
          <template v-if="authStore.isLoggedIn">
            <n-badge :value="unreadCount" :max="99" :show="unreadCount > 0">
              <n-button quaternary circle @click="router.push('/user/messages')">
                <template #icon>
                  <n-icon><NotificationsOutline /></n-icon>
                </template>
              </n-button>
            </n-badge>
            
            <n-badge :value="cartCount" :max="99" :show="cartCount > 0">
              <n-button quaternary circle @click="router.push('/cart')">
                <template #icon>
                  <n-icon><CartOutline /></n-icon>
                </template>
              </n-button>
            </n-badge>
            
            <n-dropdown :options="userMenuOptions" @select="handleUserMenuSelect">
              <n-button quaternary>
                <n-avatar
                  v-if="authStore.avatarUrl"
                  :src="authStore.avatarUrl"
                  round
                  size="small"
                  class="user-avatar"
                />
                <n-avatar v-else round size="small" class="user-avatar">
                  {{ authStore.nickname?.charAt(0) || authStore.username?.charAt(0) || 'U' }}
                </n-avatar>
                <span class="username">{{ authStore.nickname || authStore.username }}</span>
              </n-button>
            </n-dropdown>
          </template>
          
          <template v-else>
            <n-button @click="router.push('/login')">登录</n-button>
            <n-button type="primary" @click="router.push('/register')">注册</n-button>
          </template>
        </div>
      </div>
    </n-layout-header>
    
    <n-layout-content class="content">
      <router-view />
    </n-layout-content>
    
    <n-layout-footer bordered class="footer">
      <div class="footer-content">
        <p>© 2024 社区运动场地管理系统</p>
      </div>
    </n-layout-footer>
  </n-layout>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted, h } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { getUnreadCount } from '@/api/user'
import {
  BasketballOutline,
  NotificationsOutline,
  CartOutline,
  PersonOutline,
  WalletOutline,
  HeartOutline,
  ChatbubblesOutline,
  LogOutOutline,
  SchoolOutline,
  BriefcaseOutline,
  ListOutline,
  SettingsOutline
} from '@vicons/ionicons5'
import { NIcon } from 'naive-ui'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const cartStore = useCartStore()

const unreadCount = ref(0)

const cartCount = computed(() => cartStore.totalCount)

const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/venues')) return 'venues'
  if (path.startsWith('/equipment') || path === '/cart') return 'equipment'
  if (path.startsWith('/courses')) return 'courses'
  if (path.startsWith('/videos')) return 'videos'
  if (path.startsWith('/notices')) return 'notices'
  return 'home'
})

const renderIcon = (icon) => () => h(NIcon, null, { default: () => h(icon) })

const menuOptions = [
  { label: '首页', key: 'home', path: '/' },
  { label: '运动场地', key: 'venues', path: '/venues' },
  { label: '体育器材', key: 'equipment', path: '/equipment' },
  { label: '教练课程', key: 'courses', path: '/courses' },
  { label: '教学视频', key: 'videos', path: '/videos' },
  { label: '公告', key: 'notices', path: '/notices' }
].map(item => ({
  ...item,
  label: () => h('span', { onClick: () => router.push(item.path) }, item.label)
}))

const userMenuOptions = computed(() => {
  const options = [
    { label: '个人中心', key: 'profile', icon: renderIcon(PersonOutline) },
    { label: '我的钱包', key: 'wallet', icon: renderIcon(WalletOutline) },
    { label: '我的订单', key: 'orders', icon: renderIcon(ListOutline) },
    { label: '我的场地预约', key: 'venue-bookings', icon: renderIcon(ListOutline) },
    { label: '我的课程预约', key: 'course-bookings', icon: renderIcon(ListOutline) },
    { label: '我的教学视频', key: 'video-purchases', icon: renderIcon(ListOutline) },
    { label: '我的投诉', key: 'complaints', icon: renderIcon(ListOutline) },
    { label: '我的收藏', key: 'favorites', icon: renderIcon(HeartOutline) },
    { label: '我的咨询', key: 'consultations', icon: renderIcon(ChatbubblesOutline) },
    { type: 'divider', key: 'd1' }
  ]
  
  if (!authStore.isCoach) {
    options.push({ label: '教练认证申请', key: 'coach-apply', icon: renderIcon(SchoolOutline) })
  }

  if (authStore.isCoach) {
    options.push({ label: '教练中心', key: 'coach', icon: renderIcon(SchoolOutline) })
  }
  
  if (authStore.isStaff) {
    options.push({ label: '员工中心', key: 'staff', icon: renderIcon(BriefcaseOutline) })
  }
  
  if (authStore.isAdmin) {
    options.push({ label: '管理后台', key: 'admin', icon: renderIcon(SettingsOutline) })
  }
  
  if (authStore.isCoach || authStore.isStaff || authStore.isAdmin) {
    options.push({ type: 'divider', key: 'd2' })
  }
  
  options.push({ label: '退出登录', key: 'logout', icon: renderIcon(LogOutOutline) })
  
  return options
})

const handleUserMenuSelect = (key) => {
  switch (key) {
    case 'profile':
      router.push('/user/profile')
      break
    case 'wallet':
      router.push('/user/wallet')
      break
    case 'orders':
      router.push('/user/orders')
      break
    case 'venue-bookings':
      router.push('/user/bookings')
      break
    case 'course-bookings':
      router.push('/user/course-bookings')
      break
    case 'video-purchases':
      router.push('/user/video-purchases')
      break
    case 'complaints':
      router.push('/user/complaints')
      break
    case 'favorites':
      router.push('/user/favorites')
      break
    case 'consultations':
      router.push('/user/consultations')
      break
    case 'coach-apply':
      router.push('/coach/apply')
      break
    case 'coach':
      router.push('/coach/courses')
      break
    case 'staff':
      router.push('/staff/complaints')
      break
    case 'admin':
      router.push('/admin/dashboard')
      break
    case 'logout':
      authStore.logout()
      router.push('/login')
      break
  }
}

const fetchUnreadCount = async () => {
  if (!authStore.isLoggedIn || !authStore.accessToken) return
  try {
    const res = await getUnreadCount()
    unreadCount.value = Number(res.data?.count ?? 0) || 0
  } catch (e) {
    // 401 错误会被 http 拦截器处理，这里静默忽略
    console.debug('Failed to fetch unread count:', e?.response?.status)
  }
}

const handleUnreadCountUpdated = (evt) => {
  const c = evt?.detail?.count
  if (c === undefined || c === null) return
  unreadCount.value = Number(c) || 0
}

onMounted(() => {
  window.addEventListener('unread-count-updated', handleUnreadCountUpdated)
  // 确保 token 存在后再请求需要认证的接口
  if (authStore.isLoggedIn && authStore.accessToken) {
    authStore.fetchUserInfo()
    fetchUnreadCount()
    cartStore.fetchCart()
  }
})

onUnmounted(() => {
  window.removeEventListener('unread-count-updated', handleUnreadCountUpdated)
})
</script>

<style scoped>
.user-layout {
  min-height: 100vh;
}

.header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(8px);
}

.header-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 16px;
  height: 60px;
  display: flex;
  align-items: center;
  gap: 24px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  flex-shrink: 0;
}

.logo-text {
  font-size: 18px;
  font-weight: 600;
  color: #18a058;
}

.nav-menu {
  flex: 1;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.user-avatar {
  margin-right: 8px;
}

.username {
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.content {
  margin-top: 60px;
  min-height: calc(100vh - 60px - 56px);
  background: linear-gradient(180deg, #f0f2f5 0%, #e8ebef 100%);
}

.footer {
  background: #fff;
}

.footer-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 14px 16px;
  text-align: center;
  color: #999;
  font-size: 13px;
}
</style>
