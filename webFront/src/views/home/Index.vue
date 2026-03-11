<template>
  <div class="home-page">
    <div class="content-wrapper">
      <!-- 轮播图 -->
      <div class="banner-section">
        <n-carousel
          v-if="banners.length"
          autoplay
          :interval="5000"
          dot-type="line"
          show-arrow
          class="banner-carousel"
        >
          <div
            v-for="banner in banners"
            :key="banner.id"
            class="banner-item"
            @click="handleBannerClick(banner)"
          >
            <img :src="banner.imageUrl" :alt="banner.title" />
          </div>
        </n-carousel>
        <n-skeleton v-else height="280px" />
      </div>
      <!-- 热门场地 -->
      <section class="section">
        <div class="section-header">
          <h2>
            <n-icon><LocationOutline /></n-icon>
            热门场地
          </h2>
          <n-button text type="primary" @click="router.push('/venues')">
            查看更多 <n-icon><ChevronForwardOutline /></n-icon>
          </n-button>
        </div>
        <n-spin :show="loading">
          <div class="card-grid">
            <div
              v-for="venue in recommendations.hotVenues"
              :key="venue.id"
              class="card"
              @click="router.push(`/venues/${venue.id}`)"
            >
              <div class="card-cover">
                <img :src="venue.coverUrl || '/placeholder.svg'" :alt="venue.name" />
                <n-tag class="card-tag" type="success" size="small">{{ venue.typeName }}</n-tag>
              </div>
              <div class="card-body">
                <h3>{{ venue.name }}</h3>
                <p class="card-desc">{{ venue.area }} · {{ venue.address }}</p>
                <div class="card-footer">
                  <span class="price">¥{{ venue.pricePerHour }}/小时</span>
                </div>
              </div>
            </div>
          </div>
        </n-spin>
      </section>

      <!-- 优质课程 -->
      <section class="section">
        <div class="section-header">
          <h2>
            <n-icon><SchoolOutline /></n-icon>
            优质课程
          </h2>
          <n-button text type="primary" @click="router.push('/courses')">
            查看更多 <n-icon><ChevronForwardOutline /></n-icon>
          </n-button>
        </div>
        <n-spin :show="loading">
          <div class="card-grid">
            <div
              v-for="course in recommendations.qualityCourses"
              :key="course.id"
              class="card"
              @click="router.push(`/courses/${course.id}`)"
            >
              <div class="card-cover">
                <img :src="course.coverUrl || '/placeholder.svg'" :alt="course.title" />
                <n-tag class="card-tag" type="info" size="small">{{ course.category }}</n-tag>
              </div>
              <div class="card-body">
                <h3>{{ course.title }}</h3>
                <p class="card-desc">教练：{{ course.coachName }}</p>
                <div class="card-footer">
                  <span class="price">¥{{ course.price }}</span>
                  <span class="meta">{{ course.durationMinutes }}分钟</span>
                </div>
              </div>
            </div>
          </div>
        </n-spin>
      </section>

      <!-- 热门视频 -->
      <section class="section">
        <div class="section-header">
          <h2>
            <n-icon><PlayCircleOutline /></n-icon>
            热门视频
          </h2>
          <n-button text type="primary" @click="router.push('/videos')">
            查看更多 <n-icon><ChevronForwardOutline /></n-icon>
          </n-button>
        </div>
        <n-spin :show="loading">
          <div v-if="hotVideos.length" class="card-grid">
            <div
              v-for="video in hotVideos"
              :key="video.id"
              class="card"
              @click="router.push(`/videos/${video.id}`)"
            >
              <div class="card-cover video-cover">
                <img :src="video.coverUrl || '/placeholder.svg'" :alt="video.title" />
                <div class="video-play-overlay">
                  <n-icon size="44" color="#fff"><PlayCircleOutline /></n-icon>
                </div>
              </div>
              <div class="card-body">
                <h3>{{ video.title }}</h3>
                <p class="card-desc">教练：{{ video.coachUsername || '专业教练' }}</p>
                <div class="card-footer">
                  <span class="price">¥{{ video.price }}</span>
                  <span class="meta">{{ video.purchaseCount || 0 }} 人购买</span>
                </div>
              </div>
            </div>
          </div>

          <n-empty v-else-if="!loading" description="暂无视频数据" />
        </n-spin>
      </section>

      <!-- 器材优惠 -->
      <section class="section">
        <div class="section-header">
          <h2>
            <n-icon><BasketballOutline /></n-icon>
            热门器材
          </h2>
          <n-button text type="primary" @click="router.push('/equipment')">
            查看更多 <n-icon><ChevronForwardOutline /></n-icon>
          </n-button>
        </div>
        <n-spin :show="loading">
          <div class="card-grid">
            <div
              v-for="item in recommendations.promotionEquipments"
              :key="item.id"
              class="card"
              @click="router.push(`/equipment/${item.id}`)"
            >
              <div class="card-cover">
                <img :src="item.coverUrl || '/placeholder.svg'" :alt="item.name" />
                <n-tag v-if="item.stock < 10" class="card-tag" type="warning" size="small">库存紧张</n-tag>
              </div>
              <div class="card-body">
                <h3>{{ item.name }}</h3>
                <p class="card-desc">{{ item.spec }}</p>
                <div class="card-footer">
                  <span class="price">¥{{ item.price }}</span>
                  <span class="meta">库存 {{ item.stock }}</span>
                </div>
              </div>
            </div>
          </div>
        </n-spin>
      </section>

      <!-- 公告 -->
      <section class="section">
        <div class="section-header">
          <h2>
            <n-icon><NewspaperOutline /></n-icon>
            社区公告
          </h2>
          <n-button text type="primary" @click="router.push('/notices')">
            查看更多 <n-icon><ChevronForwardOutline /></n-icon>
          </n-button>
        </div>
        <n-spin :show="loading">
          <n-list bordered class="notice-list">
            <n-list-item
              v-for="notice in recommendations.notices"
              :key="notice.id"
              @click="router.push(`/notices/${notice.id}`)"
              class="notice-item"
            >
              <template #prefix>
                <n-tag :type="getNoticeTagType(notice.noticeType)" size="small">
                  {{ getNoticeTypeName(notice.noticeType) }}
                </n-tag>
              </template>
              <n-thing :title="notice.title">
                <template #description>
                  {{ formatDate(notice.publishAt) }}
                </template>
              </n-thing>
            </n-list-item>
          </n-list>
        </n-spin>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getBanners, getRecommendations } from '@/api/home'
import { getVideos } from '@/api/video'
import dayjs from 'dayjs'
import {
  LocationOutline,
  SchoolOutline,
  PlayCircleOutline,
  BasketballOutline,
  NewspaperOutline,
  ChevronForwardOutline
} from '@vicons/ionicons5'

const router = useRouter()

const loading = ref(true)
const banners = ref([])
const hotVideos = ref([])
const recommendations = ref({
  hotVenues: [],
  qualityCourses: [],
  promotionEquipments: [],
  notices: []
})

function normalizeAssetUrl(url) {
  if (url === undefined || url === null) return url
  const s = String(url).trim()
  if (!s) return null
  if (s.startsWith('http://') || s.startsWith('https://') || s.startsWith('data:') || s.startsWith('blob:')) {
    return s
  }
  if (s.startsWith('/')) return s
  return `/${s}`
}

function normalizeRecommendations(raw) {
  const r = raw || {}

  const hotVenues = Array.isArray(r.hotVenues)
    ? r.hotVenues.map(v => ({
        ...(v || {}),
        coverUrl: normalizeAssetUrl(v?.coverUrl)
      }))
    : []

  const qualityCourses = Array.isArray(r.qualityCourses)
    ? r.qualityCourses
        .map(it => {
          const c = it?.course || it || {}
          return {
            ...(c || {}),
            coachName: c?.coachUsername ?? c?.coachName,
            coverUrl: normalizeAssetUrl(c?.coverUrl)
          }
        })
        .filter(x => x && x.id)
    : []

  const promotionEquipments = Array.isArray(r.equipmentDeals)
    ? r.equipmentDeals.map(e => ({
        ...(e || {}),
        coverUrl: normalizeAssetUrl(e?.coverUrl)
      }))
    : (Array.isArray(r.promotionEquipments)
        ? r.promotionEquipments.map(e => ({
            ...(e || {}),
            coverUrl: normalizeAssetUrl(e?.coverUrl)
          }))
        : [])

  const notices = Array.isArray(r.notices) ? r.notices : []

  return {
    hotVenues,
    qualityCourses,
    promotionEquipments,
    notices
  }
}

function normalizeHotVideos(raw) {
  const rows = raw?.items || raw?.list || raw || []
  const arr = Array.isArray(rows) ? rows : []
  const mapped = arr
    .map((r) => ({
      ...(r || {}),
      coverUrl: normalizeAssetUrl(r?.coverUrl)
    }))
    .filter((x) => x && x.id)

  mapped.sort((a, b) => {
    const ap = Number(a?.purchaseCount || 0)
    const bp = Number(b?.purchaseCount || 0)
    if (bp !== ap) return bp - ap
    const aid = Number(a?.id || 0)
    const bid = Number(b?.id || 0)
    return bid - aid
  })

  return mapped
}

const fetchData = async () => {
  loading.value = true
  try {
    const [bannersRes, recRes, videoRes] = await Promise.all([
      getBanners(),
      getRecommendations(),
      getVideos({ page: 1, size: 30 })
    ])
    banners.value = bannersRes.data || []
    recommendations.value = normalizeRecommendations(recRes.data)
    hotVideos.value = normalizeHotVideos(videoRes?.data).slice(0, 6)
  } catch (e) {
    console.error('Failed to fetch home data:', e)
  } finally {
    loading.value = false
  }
}

const handleBannerClick = (banner) => {
  if (banner.linkUrl) {
    if (banner.linkUrl.startsWith('http')) {
      window.open(banner.linkUrl, '_blank')
    } else {
      router.push(banner.linkUrl)
    }
  }
}

const formatDate = (date) => {
  return dayjs(date).format('YYYY-MM-DD')
}

const getNoticeTypeName = (type) => {
  const map = {
    ACTIVITY: '活动',
    SYSTEM: '系统',
    VENUE_ADJUST: '场地',
    POLICY: '政策'
  }
  return map[type] || '公告'
}

const getNoticeTagType = (type) => {
  const map = {
    ACTIVITY: 'success',
    SYSTEM: 'info',
    VENUE_ADJUST: 'warning',
    POLICY: 'error'
  }
  return map[type] || 'default'
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.home-page {
  min-height: 100%;
}

.content-wrapper {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 16px;
}

.banner-section {
  margin-bottom: 24px;
  border-radius: 8px;
  overflow: hidden;
}

.banner-carousel {
  height: 280px;
}

.banner-item {
  height: 280px;
  cursor: pointer;
  background: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;
}

.banner-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.section {
  margin-bottom: 32px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-header h2 {
  font-size: 20px;
  font-weight: 600;
  color: #333;
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}

.card {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.card-cover {
  position: relative;
  height: 160px;
  overflow: hidden;
}

.card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.card-tag {
  position: absolute;
  top: 8px;
  left: 8px;
}

.card-body {
  padding: 12px;
}

.card-body h3 {
  font-size: 16px;
  font-weight: 500;
  color: #333;
  margin: 0 0 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-desc {
  font-size: 13px;
  color: #999;
  margin: 0 0 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.price {
  font-size: 16px;
  font-weight: 600;
  color: #f5222d;
}

.meta {
  font-size: 12px;
  color: #999;
}

.notice-list {
  background: #fff;
}

.notice-item {
  cursor: pointer;
  transition: background 0.2s;
}

.notice-item:hover {
  background: #f5f7f9;
}

.video-cover {
  position: relative;
}

.video-play-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.18);
  opacity: 0;
  transition: opacity 0.2s;
}

.card:hover .video-play-overlay {
  opacity: 1;
}
</style>
