<template>
  <div class="notice-detail">
    <n-spin :show="loading">
      <n-card v-if="notice">
        <template #header>
          <n-button text @click="$router.back()">
            <n-icon><ArrowBackOutline /></n-icon> 返回
          </n-button>
        </template>

        <article class="notice-article">
          <div v-if="notice.coverUrl" class="notice-cover">
            <img :src="notice.coverUrl" :alt="notice.title" />
          </div>
          <h1 class="notice-title">{{ notice.title }}</h1>
          <div class="notice-meta">
            <span class="publish-time">发布时间: {{ formatDate(notice.publishedAt || notice.createdAt) }}</span>
          </div>
          <n-divider />
          <div class="notice-content" v-html="notice.content"></div>
        </article>
      </n-card>
    </n-spin>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowBackOutline } from '@vicons/ionicons5'
import { getNoticeDetail } from '@/api/home'
import dayjs from 'dayjs'

const route = useRoute()
const loading = ref(false)
const notice = ref(null)

const normalizeAssetUrl = (url) => {
  if (url === undefined || url === null) return url
  const s = String(url).trim()
  if (!s) return null
  if (s.startsWith('http://') || s.startsWith('https://') || s.startsWith('data:') || s.startsWith('blob:')) return s
  if (s.startsWith('/')) return s
  return `/${s}`
}

const normalizeNotice = (raw) => {
  const n = raw || {}
  const coverUrl = normalizeAssetUrl(n.coverUrl)
  let content = n.content
  if (typeof content === 'string' && content) {
    content = content
      .replace(/<img([^>]*?)\ssrc=["'](\/upload\/[^"']+)["']/gi, '<img$1 src="$2"')
      .replace(/<img([^>]*?)\ssrc=["'](upload\/[^"']+)["']/gi, '<img$1 src="/$2"')
  }
  return {
    ...n,
    coverUrl,
    content
  }
}

const formatDate = (date) => dayjs(date).format('YYYY-MM-DD HH:mm')

const fetchDetail = async () => {
  loading.value = true
  try {
    const res = await getNoticeDetail(route.params.id)
    notice.value = normalizeNotice(res.data)
  } catch (e) {
    window.$message?.error('获取公告详情失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchDetail()
})
</script>

<style scoped>
.notice-detail {
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;
}
.notice-article {
  padding: 20px 0;
}
.notice-cover {
  width: 100%;
  margin: 0 auto 16px;
}
.notice-cover img {
  width: 100%;
  max-height: 360px;
  object-fit: cover;
  display: block;
  border-radius: 8px;
}
.notice-title {
  font-size: 24px;
  margin: 0 0 16px;
  text-align: center;
}
.notice-meta {
  text-align: center;
  color: #999;
  font-size: 14px;
}
.notice-content {
  line-height: 1.8;
  font-size: 15px;
  color: #333;
}
.notice-content :deep(img) {
  max-width: 100%;
  height: auto;
}
.notice-content :deep(p) {
  margin: 1em 0;
}
</style>
