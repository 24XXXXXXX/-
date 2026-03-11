<template>
  <div class="video-purchases-page">
    <n-card title="已购视频">
      <n-spin :show="loading">
        <div v-if="videos.length" class="video-grid">
          <n-card
            v-for="video in videos"
            :key="video.id"
            hoverable
            class="video-card"
            @click="goWatch(video.videoId)"
          >
            <template #cover>
              <div class="cover-wrap">
                <img :src="video.coverUrl || '/placeholder.svg'" :alt="video.title" class="cover-img" />
                <div class="play-icon">
                  <n-icon size="40"><PlayCircleOutline /></n-icon>
                </div>
                <span class="duration">{{ getDurationText(video) }}</span>
              </div>
            </template>
            <div class="video-info">
              <h3 class="title">{{ video.title }}</h3>
              <div class="coach-row">
                <n-avatar :src="video.coachAvatar" size="small" round />
                <span class="coach-name">{{ video.coachName }}</span>
              </div>
              <div class="bottom">
                <span class="purchase-time">购买于 {{ formatDate(video.purchasedAt) }}</span>
              </div>
            </div>
          </n-card>
        </div>
        <n-empty v-else description="暂无已购视频">
          <template #extra>
            <n-button type="primary" @click="$router.push('/videos')">去看看</n-button>
          </template>
        </n-empty>
      </n-spin>

      <div class="pagination-wrap" v-if="total > 0">
        <n-pagination
          v-model:page="pagination.page"
          :page-size="pagination.pageSize"
          :item-count="total"
          @update:page="fetchVideos"
        />
      </div>
    </n-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { PlayCircleOutline } from '@vicons/ionicons5'
import { getMyVideoPurchases, getVideoDetail } from '@/api/video'
import dayjs from 'dayjs'

// 用户端「已购视频」页面
//
// 页面职责：
// - 分页展示当前用户已购买的视频列表（购买记录）
// - 点击卡片跳转到视频播放/详情页（/videos/:id）
// - UI 上展示视频封面、标题、教练信息、购买时间，以及视频时长
//
// 数据流：
// onMounted -> fetchVideos -> getMyVideoPurchases({page,pageSize}) -> videos/total
// - 列表接口通常只返回购买记录摘要；为了拿到 videoUrl 再读取时长，本页对每个 videoId 额外调用 getVideoDetail
//
// 时长读取策略（前端侧读取 metadata）：
// - readVideoDurationSeconds(videoUrl)：动态创建 <video> 元素，preload=metadata，通过 loadedmetadata 获取 duration
// - 为避免重复读取，使用 durationMap/durationLoadingMap 做缓存与去重
// - 给了 8s 超时兜底：避免某些视频源无法读取 metadata 导致 Promise 挂住
//
// 资源 URL 归一化：
// - normalizeAssetUrl 兼容完整 URL 与相对路径（自动补前导 /）
// - 空值回退到 placeholder

const router = useRouter()
const loading = ref(false)
const videos = ref([])
const total = ref(0)

const durationMap = ref({})
const durationLoadingMap = ref({})

const pagination = reactive({
  page: 1,
  pageSize: 12
})

const formatDate = (date) => dayjs(date).format('YYYY-MM-DD')

const formatDuration = (seconds) => {
  const n = Number(seconds)
  if (!Number.isFinite(n) || n <= 0) return '--:--'
  const mins = Math.floor(n / 60)
  const secs = Math.floor(n % 60)
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}

const normalizeAssetUrl = (url) => {
  // 资源地址归一化：
  // - 兼容后端返回 "xxx.jpg"（无前导 /）或 "/xxx.jpg"（有前导 /）
  // - 兼容完整 URL / dataURL / blobURL
  // - 空串/空白视为无图，模板使用 placeholder 兜底
  if (url === undefined || url === null) return url
  const s = String(url).trim()
  if (!s) return null
  if (s.startsWith('http://') || s.startsWith('https://') || s.startsWith('data:') || s.startsWith('blob:')) {
    return s
  }
  if (s.startsWith('/')) return s
  return `/${s}`
}

const readVideoDurationSeconds = (videoUrl) => {
  // 读取视频时长（秒）：
  // - 通过 HTMLVideoElement 的 loadedmetadata 事件获取 duration
  // - 仅用于展示，不影响业务逻辑
  return new Promise((resolve) => {
    const src = normalizeAssetUrl(videoUrl)
    if (!src) return resolve(null)

    const el = document.createElement('video')
    el.preload = 'metadata'

    let done = false
    const cleanup = () => {
      el.removeEventListener('loadedmetadata', onLoaded)
      el.removeEventListener('error', onError)
      try {
        el.src = ''
        el.load()
      } catch (e) {
      }
    }

    const finish = (v) => {
      if (done) return
      done = true
      cleanup()
      resolve(v)
    }

    const onLoaded = () => {
      const d = el.duration
      if (!Number.isFinite(d) || d <= 0) return finish(null)
      finish(Math.floor(d))
    }

    const onError = () => finish(null)

    el.addEventListener('loadedmetadata', onLoaded)
    el.addEventListener('error', onError)

    try {
      el.src = src
      el.load()
    } catch (e) {
      finish(null)
      return
    }

    window.setTimeout(() => finish(null), 8000)
  })
}

const loadDurationsForPurchases = async (arr) => {
  // 为购买记录批量加载视频时长：
  // - 对每个 videoId 先 getVideoDetail 拿 videoUrl
  // - 再调用 readVideoDurationSeconds 读 metadata
  // 注意：这里是串行 await，会产生 N+1 请求 + 读取时长开销；当前实现偏简单直观。
  if (!Array.isArray(arr) || arr.length === 0) return
  for (const v of arr) {
    const id = v?.videoId
    if (!id) continue
    if (durationMap.value[id] !== undefined) continue
    if (durationLoadingMap.value[id]) continue

    durationLoadingMap.value = { ...durationLoadingMap.value, [id]: true }
    try {
      const detail = await getVideoDetail(id)
      const videoUrl = detail?.data?.videoUrl
      const seconds = await readVideoDurationSeconds(videoUrl)
      if (Number.isFinite(seconds) && seconds > 0) {
        durationMap.value = { ...durationMap.value, [id]: seconds }
      } else {
        durationMap.value = { ...durationMap.value, [id]: -1 }
      }
    } catch (e) {
      durationMap.value = { ...durationMap.value, [id]: -1 }
    } finally {
      const next = { ...durationLoadingMap.value }
      delete next[id]
      durationLoadingMap.value = next
    }
  }
}

const getDurationText = (video) => {
  const id = video?.videoId
  if (!id) return '--:--'
  const v = durationMap.value[id]
  if (Number.isFinite(v) && v > 0) return formatDuration(v)
  return '--:--'
}

const fetchVideos = async () => {
  // 拉取已购视频：
  // - 分页参数 page/pageSize
  // - 返回结构兼容 items/list/直接数组
  // - 字段兼容：title/videoTitle、purchasedAt/createdAt
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      pageSize: pagination.pageSize
    }
    const res = await getMyVideoPurchases(params)
    const rows = res.data?.items || res.data?.list || res.data || []
    const arr = Array.isArray(rows) ? rows : []
    videos.value = arr.map((r) => {
      const title = r.title ?? r.videoTitle
      return {
        ...r,
        title,
        coverUrl: normalizeAssetUrl(r.coverUrl),
        coachName: r.coachName || '-',
        coachAvatar: normalizeAssetUrl(r.coachAvatar),
        purchasedAt: r.purchasedAt || r.createdAt
      }
    })
    total.value = res.data?.total || videos.value.length
    loadDurationsForPurchases(videos.value)
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const goWatch = (videoId) => {
  // 进入视频播放/详情页。
  router.push(`/videos/${videoId}`)
}

onMounted(() => {
  fetchVideos()
})
</script>

<style scoped>
.video-purchases-page {
  padding: 20px;
}
.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}
.video-card {
  cursor: pointer;
}
.cover-wrap {
  position: relative;
}
.cover-img {
  width: 100%;
  height: 160px;
  object-fit: cover;
}
.play-icon {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: rgba(255, 255, 255, 0.9);
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
}
.duration {
  position: absolute;
  bottom: 8px;
  right: 8px;
  background: rgba(0, 0, 0, 0.7);
  color: #fff;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
}
.video-info {
  padding: 10px 0;
}
.title {
  font-size: 15px;
  font-weight: 500;
  margin: 0 0 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.coach-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.coach-name {
  font-size: 13px;
  color: #666;
}
.bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.purchase-time {
  font-size: 12px;
  color: #999;
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 30px;
}
</style>
