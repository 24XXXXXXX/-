<template>
  <div class="notice-list">
    <n-card title="公告列表">
      <n-spin :show="loading">
        <div v-if="notices.length" class="notices">
          <div
            v-for="notice in notices"
            :key="notice.id"
            class="notice-item"
            @click="goDetail(notice.id)"
          >
            <div class="notice-header">
              <n-tag v-if="notice.isTop" type="error" size="small">置顶</n-tag>
              <h3 class="notice-title">{{ notice.title }}</h3>
            </div>
            <p class="notice-summary">{{ notice.summary || notice.content?.substring(0, 100) }}</p>
            <span class="notice-time">{{ formatDate(notice.publishedAt || notice.createdAt) }}</span>
          </div>
        </div>
        <n-empty v-else description="暂无公告" />
      </n-spin>

      <div class="pagination-wrap" v-if="total > 0">
        <n-pagination
          v-model:page="pagination.page"
          :page-size="pagination.pageSize"
          :item-count="total"
          @update:page="fetchNotices"
        />
      </div>
    </n-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getNotices } from '@/api/home'
import dayjs from 'dayjs'

const router = useRouter()
const loading = ref(false)
const notices = ref([])
const total = ref(0)

const pagination = reactive({
  page: 1,
  pageSize: 10
})

const formatDate = (date) => dayjs(date).format('YYYY-MM-DD')

const fetchNotices = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      pageSize: pagination.pageSize
    }
    const res = await getNotices(params)
    notices.value = res.data?.list || res.data || []
    total.value = res.data?.total || notices.value.length
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const goDetail = (id) => {
  router.push(`/notices/${id}`)
}

onMounted(() => {
  fetchNotices()
})
</script>

<style scoped>
.notice-list {
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;
}
.notices {
  display: flex;
  flex-direction: column;
}
.notice-item {
  padding: 20px 0;
  border-bottom: 1px solid #eee;
  cursor: pointer;
  transition: all 0.2s;
}
.notice-item:hover {
  background: #fafafa;
  padding-left: 10px;
}
.notice-item:last-child {
  border-bottom: none;
}
.notice-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.notice-title {
  margin: 0;
  font-size: 17px;
}
.notice-summary {
  margin: 0 0 10px;
  color: #666;
  font-size: 14px;
  line-height: 1.6;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.notice-time {
  font-size: 13px;
  color: #999;
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
