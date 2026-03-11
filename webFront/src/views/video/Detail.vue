<template>
  <div class="video-detail">
    <div class="back-btn-wrap">
      <n-button text @click="router.back()">
        <template #icon><n-icon><ArrowBackOutline /></n-icon></template>
        返回
      </n-button>
    </div>
    <n-spin :show="loading">
      <n-card v-if="video">
        <div class="detail-content">
          <div class="video-player">
            <div v-if="!isPurchased" class="preview-wrap">
              <img :src="video.coverUrl || '/placeholder.svg'" :alt="video.title" class="preview-img" />
              <div class="preview-overlay">
                <n-icon size="60"><LockClosedOutline /></n-icon>
                <p>购买后可观看完整视频</p>
              </div>
            </div>
            <video
              v-else
              ref="videoRef"
              :src="video.videoUrl"
              :poster="video.coverUrl"
              controls
              @loadedmetadata="handleLoadedMetadata"
              class="video-element"
            />
          </div>
          <div class="video-info">
            <h1 class="title">{{ video.title }}</h1>
            <div class="meta-row">
              <span class="views">{{ video.purchaseCount || 0 }} 人购买</span>
              <span class="duration">时长: {{ formatDuration(displayDuration) }}</span>
            </div>
            <div class="coach-info">
              <n-avatar v-if="false" size="large" round />
              <div class="coach-text">
                <span class="coach-name">{{ video.coachUsername }}</span>
                <span class="coach-title">{{ video.coachTitle || '认证教练' }}</span>
              </div>
            </div>
            <p class="desc">{{ video.description }}</p>
            <n-divider />
            <div class="action-row">
              <div class="price-wrap" v-if="!isPurchased">
                <span class="price">¥{{ video.price }}</span>
                <n-button type="primary" size="large" @click="handlePurchase">
                  立即购买
                </n-button>
              </div>
              <n-tag v-else type="success" size="large">已购买</n-tag>
              <n-button :type="isFavorited ? 'warning' : 'default'" @click="toggleFavorite">
                <template #icon><n-icon><component :is="isFavorited ? Heart : HeartOutline" /></n-icon></template>
                {{ isFavorited ? '已收藏' : '收藏' }}
              </n-button>
            </div>
          </div>
        </div>
      </n-card>
    </n-spin>

    <!-- 购买确认弹窗 -->
    <n-modal v-model:show="showPurchaseModal" preset="card" title="确认购买" style="width: 400px">
      <div v-if="video">
        <n-descriptions :column="1" label-placement="left">
          <n-descriptions-item label="视频">{{ video.title }}</n-descriptions-item>
          <n-descriptions-item label="教练">{{ video.coachUsername }}</n-descriptions-item>
          <n-descriptions-item label="购买人数">{{ video.purchaseCount || 0 }}</n-descriptions-item>
          <n-descriptions-item label="价格">
            <span class="modal-price">¥{{ video.price }}</span>
          </n-descriptions-item>
        </n-descriptions>
        <n-alert type="info" style="margin-top: 16px">
          购买后可永久观看此视频，费用将从钱包余额扣除
        </n-alert>
      </div>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showPurchaseModal = false">取消</n-button>
          <n-button type="primary" :loading="purchasing" @click="confirmPurchase">确认购买</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { HeartOutline, Heart, LockClosedOutline, ArrowBackOutline } from '@vicons/ionicons5'
import { getVideoDetail, purchaseVideo } from '@/api/video'
import { addFavorite, removeFavorite, checkFavorite } from '@/api/favorite'
import { useAuthStore } from '@/stores/auth'

// 公共页面「视频详情/播放」
//
// 页面职责：
// - 展示视频基本信息（标题、教练、简介、价格、购买人数、时长）
// - 对未购买用户：展示封面预览 + “购买后可观看”遮罩
// - 对已购买用户：展示 <video> 播放器，并通过 loadedmetadata 读取真实时长
// - 提供“立即购买”入口（purchaseVideo）与“收藏/取消收藏”（favorite 模块）
//
// 数据流：
// onMounted ->
// - fetchDetail：getVideoDetail(id) -> video + isPurchased(video.purchased)
// - checkFavoriteStatus：checkFavorite({type,targetId}) -> isFavorited（登录用户维度）
//
// 资金敏感点：
// - purchaseVideo 会触发钱包扣费/购买记录写入；必须由后端保证幂等与余额校验
// - 前端仅负责弹窗确认、按钮 loading、错误提示与购买成功后的状态刷新

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const video = ref(null)
const isPurchased = ref(false)
const isFavorited = ref(false)
const videoRef = ref(null)
const actualDurationSeconds = ref(null)

const showPurchaseModal = ref(false)
const purchasing = ref(false)

const formatDuration = (seconds) => {
  const n = Number(seconds)
  if (!Number.isFinite(n) || n <= 0) return '--:--'
  const mins = Math.floor(n / 60)
  const secs = Math.floor(n % 60)
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}

const displayDuration = computed(() => {
  // 展示时长：优先使用 <video> 读到的实际时长，其次使用后端返回的 video.duration。
  return actualDurationSeconds.value || video.value?.duration || 0
})

const handleLoadedMetadata = () => {
  // 读取播放器的实际时长：避免后端 duration 字段缺失/不准。
  const el = videoRef.value
  const d = el?.duration
  if (!Number.isFinite(d) || d <= 0) return
  actualDurationSeconds.value = Math.floor(d)
}

const fetchDetail = async () => {
  // 拉取视频详情：
  // - isPurchased 来自后端字段 purchased（用户维度），用于决定是否展示播放器
  loading.value = true
  try {
    const res = await getVideoDetail(route.params.id)
    video.value = res.data
    isPurchased.value = res.data?.purchased || false
  } catch (e) {
    window.$message?.error('获取视频详情失败')
  } finally {
    loading.value = false
  }
}

const checkFavoriteStatus = async () => {
  // 收藏状态是“用户维度”的信息：未登录不查。
  if (!authStore.isLoggedIn) return
  try {
    const res = await checkFavorite({ type: 'video', targetId: route.params.id })
    isFavorited.value = res.data?.favorited || false
  } catch (e) {
    console.error(e)
  }
}

const toggleFavorite = async () => {
  // 收藏/取消收藏：
  // - 未登录：提示并跳转
  // - 已登录：根据 isFavorited 调用 addFavorite/removeFavorite
  if (!authStore.isLoggedIn) {
    window.$message?.warning('请先登录')
    router.push('/login')
    return
  }
  try {
    if (isFavorited.value) {
      await removeFavorite({ type: 'video', targetId: route.params.id })
      isFavorited.value = false
      window.$message?.success('已取消收藏')
    } else {
      await addFavorite({ type: 'video', targetId: route.params.id })
      isFavorited.value = true
      window.$message?.success('收藏成功')
    }
  } catch (e) {
    window.$message?.error('操作失败')
  }
}

const handlePurchase = () => {
  // 打开购买确认弹窗：仅登录用户可购买。
  if (!authStore.isLoggedIn) {
    window.$message?.warning('请先登录')
    router.push('/login')
    return
  }
  showPurchaseModal.value = true
}

const confirmPurchase = async () => {
  // 确认购买：
  // - purchaseVideo(videoId) 由后端做余额校验、幂等、购买记录写入
  // - 成功后更新 isPurchased，并重新拉取详情（拿到可播放的 videoUrl 等）
  purchasing.value = true
  try {
    const res = await purchaseVideo(video.value.id)
    // 检查响应体中的业务错误码
    if (res.data?.code && res.data.code !== 200) {
      window.$message?.error(res.data?.msg || res.data?.message || '购买失败')
      return
    }
    window.$message?.success('购买成功')
    showPurchaseModal.value = false
    isPurchased.value = true
    fetchDetail()
  } catch (e) {
    window.$message?.error(e?.response?.data?.msg || e?.response?.data?.message || '购买失败，请检查钱包余额')
  } finally {
    purchasing.value = false
  }
}

onMounted(() => {
  fetchDetail()
  checkFavoriteStatus()
})
</script>

<style scoped>
.video-detail {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}
.back-btn-wrap {
  margin-bottom: 16px;
}
.detail-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.video-player {
  width: 100%;
  max-width: 800px;
  margin: 0 auto;
}
.preview-wrap {
  position: relative;
  border-radius: 8px;
  overflow: hidden;
}
.preview-img {
  width: 100%;
  aspect-ratio: 16/9;
  object-fit: cover;
}
.preview-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #fff;
}
.preview-overlay p {
  margin-top: 12px;
  font-size: 16px;
}
.video-element {
  width: 100%;
  aspect-ratio: 16/9;
  border-radius: 8px;
  background: #000;
}
.video-info {
  max-width: 800px;
  margin: 0 auto;
  width: 100%;
}
.title {
  font-size: 24px;
  margin: 0 0 12px;
}
.meta-row {
  display: flex;
  gap: 20px;
  color: #999;
  font-size: 14px;
  margin-bottom: 16px;
}
.coach-info {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.coach-text {
  display: flex;
  flex-direction: column;
}
.coach-name {
  font-weight: 500;
}
.coach-title {
  font-size: 12px;
  color: #999;
}
.desc {
  color: #666;
  line-height: 1.6;
}
.action-row {
  display: flex;
  align-items: center;
  gap: 20px;
}
.price-wrap {
  display: flex;
  align-items: center;
  gap: 16px;
}
.price {
  font-size: 28px;
  color: #f5222d;
  font-weight: 600;
}
.modal-price {
  color: #f5222d;
  font-weight: 600;
}
</style>
