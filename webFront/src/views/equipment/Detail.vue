<template>
  <div class="equipment-detail">
    <div class="back-btn-wrap">
      <n-button text @click="router.back()">
        <template #icon><n-icon><ArrowBackOutline /></n-icon></template>
        返回
      </n-button>
    </div>
    <n-spin :show="loading">
      <n-card v-if="equipment">
        <div class="detail-content">
          <div class="left">
            <img :src="equipment.coverUrl || '/placeholder.svg'" :alt="equipment.name" class="main-img" />
          </div>
          <div class="right">
            <h1 class="title">{{ equipment.name }}</h1>
            <p class="desc">{{ equipment.description }}</p>
            <div class="price-row">
              <span class="price">¥{{ equipment.price }}</span>
              <span class="stock">库存: {{ equipment.stock }}</span>
            </div>
            <n-divider />
            <div class="action-row">
              <n-input-number v-model:value="quantity" :min="1" :max="equipment.stock" style="width: 120px" />
              <n-button type="primary" :disabled="equipment.stock <= 0" @click="handleAddCart">
                <template #icon><n-icon><CartOutline /></n-icon></template>
                加入购物车
              </n-button>
              <n-button :type="isFavorited ? 'warning' : 'default'" @click="toggleFavorite">
                <template #icon><n-icon><component :is="isFavorited ? Heart : HeartOutline" /></n-icon></template>
                {{ isFavorited ? '已收藏' : '收藏' }}
              </n-button>
            </div>
          </div>
        </div>
      </n-card>

      <!-- 评价区域 -->
      <n-card title="用户评价" class="reviews-card">
        <div v-if="reviews.length">
          <div v-for="review in reviews" :key="review.id" class="review-item">
            <div class="review-header">
              <n-avatar :src="review.userAvatar" size="small" />
              <span class="username">{{ review.username }}</span>
              <n-rate :value="review.rating" readonly size="small" />
              <span class="time">{{ formatDate(review.createdAt) }}</span>
            </div>
            <p class="review-content">{{ review.content }}</p>
          </div>
          <div class="pagination-wrap" v-if="reviewTotal > 5">
            <n-pagination
              v-model:page="reviewPage"
              :page-size="5"
              :item-count="reviewTotal"
              @update:page="fetchReviews"
            />
          </div>
        </div>
        <n-empty v-else description="暂无评价" />
      </n-card>
    </n-spin>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CartOutline, HeartOutline, Heart, ArrowBackOutline } from '@vicons/ionicons5'
import { getEquipmentDetail, getEquipmentReviews } from '@/api/equipment'
import { addFavorite, removeFavorite, checkFavorite } from '@/api/favorite'
import { useCartStore } from '@/stores/cart'
import { useAuthStore } from '@/stores/auth'
import dayjs from 'dayjs'

// 公共页面「器材详情」
//
// 页面职责：
// - 展示器材商品详情（封面/名称/描述/价格/库存）
// - 支持加入购物车（cartStore.addItem）
// - 支持收藏/取消收藏（favorite 模块）
// - 展示用户评价（分页）
//
// 数据流：
// onMounted ->
// - fetchDetail：getEquipmentDetail(id) -> equipment
// - fetchReviews：getEquipmentReviews(id, {page,size}) -> reviews/reviewTotal
// - checkFavoriteStatus：checkFavorite({type,targetId}) -> isFavorited（登录用户维度）
//
// 登录态边界：
// - 加入购物车/收藏属于“用户能力”，未登录时提示并跳转登录
// - 详情与评价通常是公共可见
//
// UI 交互要点：
// - quantity 通过 n-input-number 绑定，并限制 min=1、max=equipment.stock
// - stock<=0 时禁用“加入购物车”按钮

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()
const authStore = useAuthStore()

const loading = ref(false)
const equipment = ref(null)
const quantity = ref(1)
const isFavorited = ref(false)
const reviews = ref([])
const reviewTotal = ref(0)
const reviewPage = ref(1)

const formatDate = (date) => dayjs(date).format('YYYY-MM-DD HH:mm')

const fetchDetail = async () => {
  // 拉取器材详情：id 来自路由 params。
  loading.value = true
  try {
    const res = await getEquipmentDetail(route.params.id)
    equipment.value = res.data
  } catch (e) {
    window.$message?.error('获取器材详情失败')
  } finally {
    loading.value = false
  }
}

const fetchReviews = async () => {
  // 拉取评价列表（分页）：
  // - 这里固定 size=5（见模板 n-pagination）
  // - 返回结构兼容 items/list/直接数组
  try {
    const res = await getEquipmentReviews(route.params.id, { page: reviewPage.value, size: 5 })
    reviews.value = res.data?.items || res.data?.list || res.data || []
    reviewTotal.value = res.data?.total ?? reviews.value.length
  } catch (e) {
    console.error(e)
  }
}

const checkFavoriteStatus = async () => {
  // 收藏状态是“用户维度”的信息：未登录不查。
  if (!authStore.isLoggedIn) return
  try {
    const res = await checkFavorite({ type: 'equipment', targetId: route.params.id })
    isFavorited.value = res.data?.favorited || false
  } catch (e) {
    console.error(e)
  }
}

const handleAddCart = async () => {
  // 加入购物车：
  // - 未登录：提示并跳转
  // - 具体加购逻辑封装在 cartStore.addItem 内部（通常会调用后端购物车接口）
  if (!authStore.isLoggedIn) {
    window.$message?.warning('请先登录')
    router.push('/login')
    return
  }
  const success = await cartStore.addItem(equipment.value.id, quantity.value)
  if (success) {
    window.$message?.success('已加入购物车')
  } else {
    window.$message?.error('加入购物车失败')
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
      await removeFavorite({ type: 'equipment', targetId: route.params.id })
      isFavorited.value = false
      window.$message?.success('已取消收藏')
    } else {
      await addFavorite({ type: 'equipment', targetId: route.params.id })
      isFavorited.value = true
      window.$message?.success('收藏成功')
    }
  } catch (e) {
    window.$message?.error('操作失败')
  }
}

onMounted(() => {
  fetchDetail()
  fetchReviews()
  checkFavoriteStatus()
})
</script>

<style scoped>
.equipment-detail {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}
.back-btn-wrap {
  margin-bottom: 16px;
}
.detail-content {
  display: flex;
  gap: 40px;
}
.left {
  flex: 0 0 400px;
}
.main-img {
  width: 100%;
  border-radius: 8px;
}
.right {
  flex: 1;
}
.title {
  font-size: 24px;
  margin: 0 0 16px;
}
.desc {
  color: #666;
  line-height: 1.6;
  margin-bottom: 20px;
}
.price-row {
  display: flex;
  align-items: center;
  gap: 20px;
}
.price {
  font-size: 28px;
  color: #f5222d;
  font-weight: 600;
}
.stock {
  color: #999;
}
.action-row {
  display: flex;
  gap: 16px;
  align-items: center;
}
.reviews-card {
  margin-top: 20px;
}
.review-item {
  padding: 16px 0;
  border-bottom: 1px solid #eee;
}
.review-item:last-child {
  border-bottom: none;
}
.review-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.username {
  font-weight: 500;
}
.time {
  color: #999;
  font-size: 12px;
  margin-left: auto;
}
.review-content {
  margin: 0;
  color: #333;
  line-height: 1.6;
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
