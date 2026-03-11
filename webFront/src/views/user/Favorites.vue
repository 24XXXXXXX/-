<template>
  <div class="favorites-page">
    <n-card title="我的收藏">
      <n-tabs v-model:value="activeTab" @update:value="handleTabChange">
        <n-tab-pane name="venue" tab="场地" />
        <n-tab-pane name="equipment" tab="器材" />
        <n-tab-pane name="course" tab="课程" />
        <n-tab-pane name="video" tab="视频" />
      </n-tabs>

      <n-spin :show="loading">
        <div v-if="favorites.length" class="favorites-grid">
          <n-card
            v-for="item in favorites"
            :key="item.id"
            hoverable
            class="favorite-card"
            @click="goDetail(item)"
          >
            <template #cover>
              <img :src="item.coverUrl || '/placeholder.svg'" :alt="item.name || item.title" class="cover-img" />
            </template>
            <div class="favorite-info">
              <h3 class="name">{{ item.name || item.title }}</h3>
              <p class="desc">{{ item.description }}</p>
              <div class="bottom">
                <span class="price" v-if="item.price">¥{{ item.price }}</span>
                <n-button text type="error" @click.stop="handleRemove(item)">
                  <n-icon><HeartDislikeOutline /></n-icon> 取消收藏
                </n-button>
              </div>
            </div>
          </n-card>
        </div>
        <n-empty v-else description="暂无收藏" />
      </n-spin>

      <div class="pagination-wrap" v-if="total > 0">
        <n-pagination
          v-model:page="pagination.page"
          :page-size="pagination.pageSize"
          :item-count="total"
          @update:page="fetchFavorites"
        />
      </div>
    </n-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { HeartDislikeOutline } from '@vicons/ionicons5'
import { getFavorites, removeFavorite } from '@/api/favorite'

// 用户端「我的收藏」页面
//
// 页面职责：
// - 按 targetType（场地/器材/课程/视频）分 tab 展示收藏列表
// - 支持分页
// - 支持取消收藏（removeFavorite），取消后刷新列表
// - 点击卡片跳转到对应的详情页（场地详情/器材详情/课程详情/视频详情）
//
// 数据流：
// onMounted -> fetchFavorites -> getFavorites(params) -> favorites/total -> template 渲染
// tab/pagination 变化 -> fetchFavorites
//
// targetType 口径：
// - 本页直接使用 tab name（venue/equipment/course/video）作为 targetType
// - 后端通常会做枚举校验，因此前端保持与后端一致非常重要

const router = useRouter()
const loading = ref(false)
const favorites = ref([])
const total = ref(0)
const activeTab = ref('venue')

const pagination = reactive({
  page: 1,
  pageSize: 12
})

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

const fetchFavorites = async () => {
  // 拉取收藏列表：
  // - 分页参数 page/size
  // - targetType：由 activeTab 决定
  // - 返回结构兼容 items/list/直接数组
  loading.value = true
  try {
    const params = {
      targetType: activeTab.value,
      page: pagination.page,
      size: pagination.pageSize
    }
    const res = await getFavorites(params)
    const rows = res.data?.items || res.data?.list || res.data || []
    favorites.value = (Array.isArray(rows) ? rows : []).map((r) => ({
      ...r,
      coverUrl: normalizeAssetUrl(r?.coverUrl)
    }))
    total.value = res.data?.total ?? favorites.value.length
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const handleTabChange = () => {
  // 切换 tab：回到第一页并刷新。
  pagination.page = 1
  fetchFavorites()
}

const goDetail = (item) => {
  // 跳转到对应详情页：根据当前 tab 选择不同路由前缀。
  // 注意：这里假设后端返回的收藏条目包含 targetId。
  const routes = {
    venue: `/venues/${item.targetId}`,
    equipment: `/equipment/${item.targetId}`,
    course: `/courses/${item.targetId}`,
    video: `/videos/${item.targetId}`
  }
  router.push(routes[activeTab.value])
}

const handleRemove = async (item) => {
  // 取消收藏：
  // - removeFavorite 通常期望幂等（重复取消也不应报错），但仍以后端实现为准
  // - 成功后刷新列表
  try {
    await removeFavorite({ type: activeTab.value, targetId: item.targetId })
    window.$message?.success('已取消收藏')
    fetchFavorites()
  } catch (e) {
    window.$message?.error('操作失败')
  }
}

onMounted(() => {
  fetchFavorites()
})
</script>

<style scoped>
.favorites-page {
  padding: 20px;
}
.favorites-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 20px;
  margin-top: 20px;
}
.favorite-card {
  cursor: pointer;
}
.cover-img {
  width: 100%;
  height: 150px;
  object-fit: cover;
}
.favorite-info {
  padding: 10px 0;
}
.name {
  font-size: 15px;
  font-weight: 500;
  margin: 0 0 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.desc {
  font-size: 13px;
  color: #666;
  margin: 0 0 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.price {
  color: #f5222d;
  font-size: 16px;
  font-weight: 600;
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 30px;
}
</style>
