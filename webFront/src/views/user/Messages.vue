<template>
  <div class="messages-page">
    <n-card title="消息中心">
      <template #header-extra>
        <n-button text type="primary" @click="handleMarkAllRead" :disabled="unreadCount === 0">
          全部已读
        </n-button>
      </template>

      <n-spin :show="loading">
        <div v-if="messages.length" class="message-list">
          <div
            v-for="msg in messages"
            :key="msg.id"
            :class="['message-item', { unread: !msg.isRead }]"
            @click="handleRead(msg)"
          >
            <div class="msg-icon">
              <n-icon size="24" :color="msg.isRead ? '#999' : '#18a058'">
                <component :is="getIcon(msg.type)" />
              </n-icon>
            </div>
            <div class="msg-content">
              <h4 class="msg-title">{{ msg.title }}</h4>
              <p class="msg-body">{{ msg.content }}</p>
              <span class="msg-time">{{ formatDate(msg.createdAt) }}</span>
            </div>
            <n-badge v-if="!msg.isRead" dot />
          </div>
        </div>
        <n-empty v-else description="暂无消息" />
      </n-spin>

      <div class="pagination-wrap" v-if="total > 0">
        <n-pagination
          v-model:page="pagination.page"
          :page-size="pagination.pageSize"
          :item-count="total"
          @update:page="fetchMessages"
        />
      </div>
    </n-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import {
  NotificationsOutline,
  CartOutline,
  CalendarOutline,
  ChatbubbleOutline,
  InformationCircleOutline
} from '@vicons/ionicons5'
import { getMessages, markMessageRead, markAllRead, getUnreadCount } from '@/api/user'
import dayjs from 'dayjs'

// 用户端「消息中心」页面
//
// 页面职责：
// - 分页展示用户消息（系统通知/订单/预约/投诉等）
// - 支持“单条标记已读”与“一键全部已读”
// - 在页面顶部展示 unreadCount，并把变化通过 window 事件广播给全局（例如 Header 徽标）
//
// 数据流：
// onMounted -> fetchMessages + fetchUnreadCount
// - 列表接口：getMessages({page,pageSize})
// - 未读数接口：getUnreadCount()
//
// 字段兼容与归一化：
// - 消息类型字段可能是 type/msgType
// - 已读字段可能是 readFlag 或 isRead
// - normalizeMessageItem 会统一得到：type(小写) + readFlag(0/1) + isRead(boolean)

const loading = ref(false)
const messages = ref([])
const total = ref(0)
const unreadCount = ref(0)

const pagination = reactive({
  page: 1,
  pageSize: 20
})

const formatDate = (date) => dayjs(date).format('YYYY-MM-DD HH:mm')

const iconMap = {
  system: NotificationsOutline,
  order: CartOutline,
  booking: CalendarOutline,
  complaint: ChatbubbleOutline,
  default: InformationCircleOutline
}

// iconMap：消息类型 -> 图标组件。
// 这里的 key 使用小写，配合 normalizeMessageItem 统一大小写。

const getIcon = (type) => iconMap[type] || iconMap.default

const emitUnreadCount = (count) => {
  // emitUnreadCount：通过浏览器事件把未读数变化广播出去。
  // 常见用途：Header 或侧边栏订阅该事件更新红点徽标。
  try {
    window.dispatchEvent(new CustomEvent('unread-count-updated', { detail: { count } }))
  } catch (e) {
  }
}

const normalizeMessageItem = (m) => {
  // normalizeMessageItem：对后端返回的消息对象做字段归一化，减少模板的条件判断。
  if (!m || typeof m !== 'object') return m
  const msgType = (m.type ?? m.msgType ?? 'default')
  const rf = m.readFlag ?? (m.isRead ? 1 : 0)
  return {
    ...m,
    type: String(msgType).toLowerCase(),
    readFlag: rf,
    isRead: rf === 1,
  }
}

const fetchMessages = async () => {
  // 拉取消息列表：
  // - 分页参数 page/pageSize
  // - 返回结构兼容 items/list/content
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      pageSize: pagination.pageSize
    }
    const res = await getMessages(params)
    const rows = res.data?.items || res.data?.list || res.data?.content || []
    messages.value = Array.isArray(rows) ? rows.map(normalizeMessageItem) : []
    total.value = res.data?.total ?? res.data?.totalElements ?? messages.value.length
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const fetchUnreadCount = async () => {
  // 拉取未读数：
  // - 兼容后端返回 {count:xx} 或直接 number。
  try {
    const res = await getUnreadCount()
    unreadCount.value = Number(res.data?.count ?? res.data ?? 0) || 0
    emitUnreadCount(unreadCount.value)
  } catch (e) {
    console.error(e)
  }
}

const handleRead = async (msg) => {
  // 单条标记已读：
  // - 先判断本地状态，避免重复调用
  // - 成功后直接“就地更新” msg.isRead/readFlag，并维护 unreadCount
  if (!msg.isRead) {
    try {
      await markMessageRead(msg.id)
      msg.isRead = true
      msg.readFlag = 1
      unreadCount.value = Math.max(0, unreadCount.value - 1)
      emitUnreadCount(unreadCount.value)
    } catch (e) {
      console.error(e)
    }
  }
}

const handleMarkAllRead = async () => {
  // 全部标记已读：
  // - 调用 markAllRead 后，把当前列表全部置为已读
  // - 同时把 unreadCount 清零并广播
  try {
    await markAllRead()
    messages.value.forEach(msg => {
      msg.isRead = true
      msg.readFlag = 1
    })
    unreadCount.value = 0
    emitUnreadCount(0)
    window.$message?.success('已全部标记为已读')
  } catch (e) {
    window.$message?.error('操作失败')
  }
}

onMounted(() => {
  fetchMessages()
  fetchUnreadCount()
})
</script>

<style scoped>
.messages-page {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
}
.message-list {
  display: flex;
  flex-direction: column;
}
.message-item {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 16px;
  border-bottom: 1px solid #eee;
  cursor: pointer;
  transition: background 0.2s;
}
.message-item:hover {
  background: #fafafa;
}
.message-item.unread {
  background: #f0fdf4;
}
.message-item:last-child {
  border-bottom: none;
}
.msg-icon {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  border-radius: 50%;
}
.msg-content {
  flex: 1;
  min-width: 0;
}
.msg-title {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 500;
}
.msg-body {
  margin: 0 0 8px;
  font-size: 14px;
  color: #666;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.msg-time {
  font-size: 12px;
  color: #999;
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
