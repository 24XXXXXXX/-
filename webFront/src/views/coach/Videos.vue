<template>
  <div class="coach-videos-page">
    <n-card title="我的视频">
      <template #header-extra>
        <n-button type="primary" @click="$router.push('/coach/videos/create')">上传视频</n-button>
      </template>

      <n-spin :show="loading">
        <div v-if="videos.length" class="video-list">
          <div v-for="video in videos" :key="video.id" class="video-card">
            <div class="video-cover" @click="$router.push(`/coach/videos/${video.id}/preview`)" style="cursor: pointer">
              <img :src="video.coverUrl || '/placeholder.svg'" />
            </div>
            <div class="video-info">
              <div class="video-header">
                <h3>{{ video.title }}</h3>
                <n-tag :type="video.status === 'ON_SALE' ? 'success' : 'default'" size="small">
                  {{ video.status === 'ON_SALE' ? '已上架' : '已下架' }}
                </n-tag>
              </div>
              <p class="video-desc">{{ video.description }}</p>
              <div class="video-meta">
                <span class="price">¥{{ video.price }}</span>
                <span class="stats">{{ video.purchaseCount || 0 }} 人购买</span>
              </div>
            </div>
            <div class="video-actions">
              <n-button text type="primary" @click="$router.push(`/coach/videos/${video.id}/edit`)">编辑</n-button>
              <n-button
                text
                :type="video.status === 'ON_SALE' ? 'warning' : 'success'"
                @click="toggleStatus(video)"
              >
                {{ video.status === 'ON_SALE' ? '下架' : '上架' }}
              </n-button>
            </div>
          </div>
        </div>
        <n-empty v-else description="暂无视频">
          <template #extra>
            <n-button type="primary" @click="$router.push('/coach/videos/create')">上传视频</n-button>
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
import { getCoachVideos, updateVideoStatus } from '@/api/coach'

const loading = ref(false)
const videos = ref([])
const total = ref(0)

const pagination = reactive({
  page: 1,
  pageSize: 10
})

const fetchVideos = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.pageSize
    }
    const res = await getCoachVideos(params)
    videos.value = res.data?.items || res.data?.list || res.data || []
    total.value = res.data?.total ?? videos.value.length
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const toggleStatus = async (video) => {
  try {
    const newStatus = video.status === 'ON_SALE' ? 'OFF_SALE' : 'ON_SALE'
    await updateVideoStatus(video.id, newStatus)
    video.status = newStatus
    window.$message?.success('状态已更新')
  } catch (e) {
    window.$message?.error('操作失败')
  }
}

onMounted(() => {
  fetchVideos()
})
</script>

<style scoped>
.coach-videos-page {
  padding: 20px;
}
.video-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.video-card {
  display: flex;
  gap: 16px;
  padding: 16px;
  border: 1px solid #eee;
  border-radius: 8px;
}
.video-cover {
  position: relative;
  flex-shrink: 0;
}
.video-cover img {
  width: 180px;
  height: 100px;
  object-fit: cover;
  border-radius: 4px;
}
.duration {
  position: absolute;
  bottom: 4px;
  right: 4px;
  background: rgba(0, 0, 0, 0.7);
  color: #fff;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
}
.video-info {
  flex: 1;
}
.video-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.video-header h3 {
  margin: 0;
  font-size: 16px;
}
.video-desc {
  margin: 0 0 10px;
  font-size: 14px;
  color: #666;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.video-meta {
  display: flex;
  gap: 20px;
}
.price {
  color: #f5222d;
  font-weight: 600;
}
.stats {
  color: #999;
  font-size: 13px;
}
.video-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  justify-content: center;
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
