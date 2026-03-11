<template>
  <div class="coach-video-preview">
    <n-card title="视频预览" :bordered="false">
      <n-spin :show="loading">
        <div v-if="video" class="wrap">
          <video
            class="video"
            :src="video.videoUrl"
            :poster="video.coverUrl"
            controls
          />

          <div class="meta">
            <h2 class="title">{{ video.title }}</h2>
            <n-tag :type="video.status === 'ON_SALE' ? 'success' : 'default'" size="small">
              {{ video.status === 'ON_SALE' ? '已上架' : '已下架' }}
            </n-tag>
            <p class="desc">{{ video.description }}</p>
          </div>
        </div>
        <n-empty v-else description="暂无数据" />
      </n-spin>
    </n-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getCoachVideoDetail } from '@/api/coach'

const route = useRoute()

const loading = ref(false)
const video = ref(null)

const fetchDetail = async () => {
  loading.value = true
  try {
    const res = await getCoachVideoDetail(route.params.id)
    video.value = res.data
  } catch (e) {
    window.$message?.error('获取视频失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchDetail()
})
</script>

<style scoped>
.coach-video-preview {
  padding: 20px;
}

.wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.video {
  width: 100%;
  max-width: 900px;
  aspect-ratio: 16/9;
  background: #000;
  border-radius: 8px;
  margin: 0 auto;
}

.meta {
  width: 100%;
  max-width: 900px;
  margin: 0 auto;
}

.title {
  margin: 0 0 8px;
}

.desc {
  margin: 12px 0 0;
  color: #666;
  line-height: 1.6;
}
</style>
