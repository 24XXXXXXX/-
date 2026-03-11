<template>
  <div class="video-list-page">
    <div class="page-wrapper">
      <div class="page-header">
        <h1>教学视频</h1>
        <p>随时随地学习，掌握运动技巧</p>
      </div>

      <!-- 筛选区 -->
      <n-card class="filter-card">
        <n-space>
          <n-select
            v-model:value="filters.category"
            placeholder="视频分类"
            clearable
            :options="categoryOptions"
            style="width: 150px"
            @update:value="handleSearch"
          />
          <n-input
            v-model:value="filters.keyword"
            placeholder="搜索视频名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <n-icon><SearchOutline /></n-icon>
            </template>
          </n-input>
          <n-button type="primary" @click="handleSearch">搜索</n-button>
          <n-button @click="handleReset">重置</n-button>
        </n-space>
      </n-card>

      <!-- 视频列表 -->
      <n-spin :show="loading">
        <div v-if="videos.length" class="video-grid">
          <div
            v-for="video in videos"
            :key="video.id"
            class="video-card"
            @click="goDetail(video.id)"
          >
            <div class="video-cover">
              <img :src="video.coverUrl || '/placeholder.svg'" :alt="video.title" />
              <div class="play-overlay">
                <n-icon size="48" color="#fff"><PlayCircleOutline /></n-icon>
              </div>
              <span v-if="shouldShowDuration(video)" class="video-duration">{{ getDurationText(video) }}</span>
            </div>
            <div class="video-body">
              <h3 class="video-name">{{ video.title }}</h3>
              <div class="video-coach">
                <n-icon><PersonOutline /></n-icon>
                <span>{{ video.coachUsername || '专业教练' }}</span>
              </div>
              <div class="video-footer">
                <span class="video-price">¥{{ video.price }}</span>
                <span class="video-purchases">{{ video.purchaseCount || 0 }} 人购买</span>
              </div>
            </div>
          </div>
        </div>

        <n-empty v-else-if="!loading" description="暂无视频数据" />
      </n-spin>

      <!-- 分页 -->
      <div v-if="total > 0" class="pagination">
        <n-pagination
          v-model:page="pagination.page"
          :page-size="pagination.pageSize"
          :item-count="total"
          show-size-picker
          :page-sizes="[12, 24, 36]"
          @update:page="fetchVideos"
          @update:page-size="handlePageSizeChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { SearchOutline, PlayCircleOutline, PersonOutline } from '@vicons/ionicons5'
import { getVideos, getVideoDetail, getVideoCategories } from '@/api/video'

// 公共页面「视频列表」
//
// 页面职责：
// - 展示教学视频列表（封面/标题/教练/价格/购买人数）
// - 支持筛选：分类（category）+ 关键字（keyword）
// - 分页加载，点击卡片进入视频详情/播放页（/videos/:id）
// - 在满足条件时展示视频时长（duration）
//
// 数据流：
// onMounted -> fetchCategories -> fetchVideos
// - 分类：getVideoCategories() -> categoryOptions
// - 列表：getVideos(params) -> videos/total
// - 时长：loadDurationsForVideos(list) 对每个视频 id 额外 getVideoDetail -> 读取 videoUrl metadata
//
// 时长展示规则：
// - canLoadDuration(video)：免费(价格=0) 或 已购买(video.purchased===true) 才尝试加载时长
// - durationMap/durationLoadingMap：缓存与去重，避免重复读取 metadata
// - readVideoDurationSeconds：动态创建 <video> 读取 loadedmetadata，设置 8s 超时兜底

const router = useRouter()
const loading = ref(false)
const videos = ref([])
const total = ref(0)

const categoryOptions = ref([])

const filters = reactive({
  category: null,
  keyword: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 12
})

const fetchCategories = async () => {
  // 拉取视频分类：返回结构兼容 raw 是数组 / raw.data 是数组。
  try {
    const res = await getVideoCategories()
    const raw = res?.data
    const list = Array.isArray(raw) ? raw : Array.isArray(raw?.data) ? raw.data : []
    categoryOptions.value = list
      .map((s) => (typeof s === 'string' ? s.trim() : ''))
      .filter((s) => !!s)
      .map((s) => ({ label: s, value: s }))
  } catch (e) {
    categoryOptions.value = []
  }
}

const durationMap = ref({})
const durationLoadingMap = ref({})

const normalizeAssetUrl = (url) => {
  // 资源 URL 归一化：兼容完整 URL 与相对路径（补 /）。
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
  // 读取视频时长（秒）：通过 <video preload=metadata> 的 loadedmetadata 获取 duration。
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
      } catch (e) {}
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

const canLoadDuration = (video) => {
  // canLoadDuration：决定是否尝试展示时长。
  // - 免费视频：允许展示
  // - 付费视频：只对已购用户展示（避免泄露/避免无意义请求）
  if (!video) return false
  const price = Number(video.price)
  if (Number.isFinite(price) && price === 0) return true
  return video.purchased === true
}

const loadDurationsForVideos = async (arr) => {
  // 批量加载列表视频的时长：
  // - 对每个 id 额外 getVideoDetail 才能拿到 videoUrl
  // - 这是典型 N+1 模式，当前实现强调“简单直观”与“按需加载（仅免费/已购）”
  if (!Array.isArray(arr) || arr.length === 0) return
  for (const v of arr) {
    const id = v?.id
    if (!id) continue
    if (!canLoadDuration(v)) continue
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

const formatDuration = (seconds) => {
  const n = Number(seconds)
  if (!Number.isFinite(n) || n <= 0) return '--:--'
  const mins = Math.floor(n / 60)
  const secs = Math.floor(n % 60)
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}

const fetchVideos = async () => {
  // 拉取视频列表：
  // - params 合并分页与筛选
  // - 返回结构兼容 items/list/直接数组
  // - 拉取后会触发 loadDurationsForVideos（按规则加载时长）
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.pageSize,
      ...filters
    }
    if (!params.category) delete params.category
    if (!params.keyword) delete params.keyword
    
    const res = await getVideos(params)
    const rows = res.data?.items || res.data?.list || res.data || []
    const arr = Array.isArray(rows) ? rows : []
    videos.value = arr.map((r) => ({
      ...r,
      coverUrl: normalizeAssetUrl(r.coverUrl)
    }))
    total.value = res.data?.total ?? videos.value.length
    loadDurationsForVideos(videos.value)
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const shouldShowDuration = (video) => {
  if (!canLoadDuration(video)) return false
  const id = video?.id
  if (!id) return false
  const v = durationMap.value[id]
  return Number.isFinite(v) && v > 0
}

const getDurationText = (video) => {
  const id = video?.id
  if (!id) return '--:--'
  const v = durationMap.value[id]
  if (Number.isFinite(v) && v > 0) return formatDuration(v)
  return '--:--'
}

const handleSearch = () => {
  // 搜索：回到第一页重新拉取。
  pagination.page = 1
  fetchVideos()
}

const handleReset = () => {
  // 重置筛选：清空条件并回到第一页。
  filters.category = null
  filters.keyword = ''
  pagination.page = 1
  fetchVideos()
}

const handlePageSizeChange = (size) => {
  // 修改每页条数：回到第一页。
  pagination.pageSize = size
  pagination.page = 1
  fetchVideos()
}

const goDetail = (id) => {
  // 跳转视频详情/播放页。
  router.push(`/videos/${id}`)
}

onMounted(() => {
  fetchCategories().finally(() => {
    fetchVideos()
  })
})
</script>

<style scoped>
.video-list-page {
  min-height: 100%;
  background: #f5f7f9;
}

.page-wrapper {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 16px;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h1 {
  font-size: 24px;
  font-weight: 600;
  color: #333;
  margin: 0 0 8px;
}

.page-header p {
  color: #666;
  margin: 0;
}

.filter-card {
  margin-bottom: 24px;
}

.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.video-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.video-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.video-cover {
  position: relative;
  height: 180px;
  overflow: hidden;
}

.video-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.play-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.3);
  opacity: 0;
  transition: opacity 0.2s;
}

.video-card:hover .play-overlay {
  opacity: 1;
}

.video-duration {
  position: absolute;
  bottom: 8px;
  right: 8px;
  background: rgba(0, 0, 0, 0.7);
  color: #fff;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}

.video-body {
  padding: 16px;
}

.video-name {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin: 0 0 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.video-coach {
  font-size: 13px;
  color: #666;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.video-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.video-price {
  font-size: 20px;
  font-weight: 600;
  color: #f5222d;
}

.video-purchases {
  font-size: 12px;
  color: #999;
}

.pagination {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}
</style>
