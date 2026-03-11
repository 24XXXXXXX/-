<template>
  <n-layout has-sider class="admin-layout">
    <n-layout-sider
      bordered
      collapse-mode="width"
      :collapsed-width="64"
      :width="220"
      :collapsed="collapsed"
      show-trigger
      @collapse="collapsed = true"
      @expand="collapsed = false"
      class="sider"
    >
      <div class="logo" @click="router.push('/admin/dashboard')">
        <n-icon size="24" color="#18a058">
          <SettingsOutline />
        </n-icon>
        <span v-if="!collapsed" class="logo-text">管理后台</span>
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
            <n-breadcrumb-item @click="router.push('/admin/dashboard')">管理后台</n-breadcrumb-item>
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
  SettingsOutline,
  HomeOutline,
  StatsChartOutline,
  PeopleOutline,
  SchoolOutline,
  LocationOutline,
  BasketballOutline,
  CartOutline,
  CashOutline,
  ChatbubblesOutline,
  NewspaperOutline,
  ImagesOutline,
  SearchOutline,
  ConstructOutline,
  LogOutOutline
} from '@vicons/ionicons5'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const collapsed = ref(false)

const renderIcon = (icon) => () => h(NIcon, null, { default: () => h(icon) })

const menuOptions = [
  { label: '数据看板', key: 'dashboard', icon: renderIcon(StatsChartOutline) },
  { label: '用户管理', key: 'users', icon: renderIcon(PeopleOutline) },
  { label: '教练审核', key: 'coaches', icon: renderIcon(SchoolOutline) },
  { label: '场地管理', key: 'venues', icon: renderIcon(LocationOutline) },
  { label: '器材管理', key: 'equipment', icon: renderIcon(BasketballOutline) },
  { label: '订单管理', key: 'orders', icon: renderIcon(CartOutline) },
  { label: '巡检管理', key: 'inspections', icon: renderIcon(SearchOutline) },
  {
    label: '财务审核',
    key: 'finance',
    icon: renderIcon(CashOutline),
    children: [
      { label: '充值审核', key: 'topups' },
      { label: '提现审核', key: 'withdrawals' }
    ]
  },
  { label: '投诉管理', key: 'complaints', icon: renderIcon(ChatbubblesOutline) },
  { label: '公告管理', key: 'notices', icon: renderIcon(NewspaperOutline) },
  { label: '轮播图管理', key: 'banners', icon: renderIcon(ImagesOutline) },
  { label: '系统配置', key: 'settings', icon: renderIcon(ConstructOutline) }
]

const activeMenu = computed(() => {
  const path = route.path
  if (path.includes('/admin/dashboard')) return 'dashboard'
  if (path.includes('/admin/users')) return 'users'
  if (path.includes('/admin/coaches')) return 'coaches'
  if (path.includes('/admin/venues')) return 'venues'
  if (path.includes('/admin/equipment')) return 'equipment'
  if (path.includes('/admin/orders')) return 'orders'
  if (path.includes('/admin/inspections')) return 'inspections'
  if (path.includes('/admin/topups')) return 'topups'
  if (path.includes('/admin/withdrawals')) return 'withdrawals'
  if (path.includes('/admin/complaints')) return 'complaints'
  if (path.includes('/admin/notices')) return 'notices'
  if (path.includes('/admin/banners')) return 'banners'
  if (path.includes('/admin/settings')) return 'settings'
  return 'dashboard'
})

const currentMenuLabel = computed(() => {
  const findLabel = (options, key) => {
    for (const opt of options) {
      if (opt.key === key) return opt.label
      if (opt.children) {
        const found = findLabel(opt.children, key)
        if (found) return found
      }
    }
    return null
  }
  return findLabel(menuOptions, activeMenu.value)
})

const handleMenuSelect = (key) => {
  router.push(`/admin/${key}`)
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
.admin-layout {
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
  left: 220px;
  width: calc(100% - 220px);
  box-sizing: border-box;
  z-index: 100;
  transition: left 0.3s var(--n-bezier);
  overflow: hidden;
}

.admin-layout :deep(.n-layout-sider--collapsed) ~ .n-layout .header {
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
  margin-left: 220px;
  margin-top: 60px;
  padding: 20px;
  background: linear-gradient(180deg, #f0f2f5 0%, #e8ebef 100%);
  min-height: calc(100vh - 60px);
  transition: margin-left 0.3s var(--n-bezier);
}

.admin-layout :deep(.n-layout-sider--collapsed) ~ .n-layout .content {
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
