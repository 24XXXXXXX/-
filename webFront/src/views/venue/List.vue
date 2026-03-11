<template>
  <div class="venue-list-page">
    <div class="page-wrapper">
      <div class="page-header">
        <h1>运动场地</h1>
        <p>选择您喜欢的场地，开始运动吧</p>
      </div>

      <!-- 筛选区 -->
      <n-card class="filter-card">
        <n-space>
          <n-select
            v-model:value="filters.typeId"
            placeholder="场地类型"
            clearable
            :options="typeOptions"
            style="width: 150px"
            @update:value="handleFilter"
          />
          <n-input
            v-model:value="filters.keyword"
            placeholder="搜索场地名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleFilter"
          >
            <template #prefix>
              <n-icon><SearchOutline /></n-icon>
            </template>
          </n-input>
          <n-button type="primary" @click="handleFilter">搜索</n-button>
          <n-button @click="handleReset">重置</n-button>
        </n-space>
      </n-card>

      <!-- 场地列表 -->
      <n-spin :show="loading">
        <div v-if="venues.length" class="venue-grid">
          <div
            v-for="venue in venues"
            :key="venue.id"
            class="venue-card"
            @click="router.push(`/venues/${venue.id}`)"
          >
            <div class="venue-cover">
              <img :src="venue.coverUrl || '/placeholder.svg'" :alt="venue.name" />
              <n-tag class="venue-tag" :type="getStatusType(venue.status)" size="small">
                {{ getStatusText(venue.status) }}
              </n-tag>
            </div>
            <div class="venue-body">
              <div class="venue-type">
                <n-tag type="success" size="small">{{ venue.typeName }}</n-tag>
              </div>
              <h3 class="venue-name">{{ venue.name }}</h3>
              <p class="venue-location">
                <n-icon><LocationOutline /></n-icon>
                {{ venue.area }} · {{ venue.address }}
              </p>
              <p class="venue-spec">{{ venue.spec }} · {{ venue.openTimeDesc }}</p>
              <div class="venue-footer">
                <span class="venue-price">¥{{ venue.pricePerHour }}<small>/小时</small></span>
                <span class="venue-clicks">{{ venue.clickCount }} 次浏览</span>
              </div>
            </div>
          </div>
        </div>

        <n-empty v-else-if="!loading" description="暂无场地数据" />
      </n-spin>

      <!-- 分页 -->
      <div v-if="total > 0" class="pagination">
        <n-pagination
          v-model:page="pagination.page"
          :page-size="pagination.size"
          :item-count="total"
          show-size-picker
          :page-sizes="[12, 24, 36]"
          @update:page="fetchVenues"
          @update:page-size="handlePageSizeChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getVenueTypes, getVenues } from '@/api/venue'
import { SearchOutline, LocationOutline } from '@vicons/ionicons5'

// 公共页面「场地列表」
//
// 页面职责：
// - 展示对用户可见的场地列表（通常只展示 ACTIVE/可预约场地）
// - 提供筛选条件：场地类型（typeId）+ 关键字（keyword）
// - 分页加载（page/size），点击卡片进入场地详情页
//
// 数据流：
// onMounted -> fetchTypes + fetchVenues
// - fetchTypes：getVenueTypes() -> typeOptions
// - fetchVenues：getVenues(params) -> venues/total
//
// 参数口径：
// - 后端分页常用 page/size
// - 筛选字段：typeId、keyword
// - status 固定传 ACTIVE：避免把维护中/停用场地暴露给普通用户

const router = useRouter()

const loading = ref(false)
const venues = ref([])
const total = ref(0)
const typeOptions = ref([])

const filters = ref({
  typeId: null,
  keyword: ''
})

const pagination = ref({
  page: 1,
  size: 12
})

const fetchTypes = async () => {
  // 拉取场地类型，用于下拉选择。
  try {
    const res = await getVenueTypes()
    typeOptions.value = (res.data || []).map(t => ({
      label: t.name,
      value: t.id
    }))
  } catch (e) {
    console.error('Failed to fetch venue types:', e)
  }
}

const fetchVenues = async () => {
  // 拉取场地列表：
  // - 如果筛选字段为空，使用 undefined 让 axios 不传该字段（避免后端误判为空串）
  // - status 固定 ACTIVE，体现“公共可预约列表”的定位
  loading.value = true
  try {
    const res = await getVenues({
      page: pagination.value.page,
      size: pagination.value.size,
      typeId: filters.value.typeId || undefined,
      keyword: filters.value.keyword || undefined,
      status: 'ACTIVE'
    })
    venues.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    console.error('Failed to fetch venues:', e)
  } finally {
    loading.value = false
  }
}

const handleFilter = () => {
  // 点击“搜索”或筛选条件变化：回到第一页重新拉取。
  pagination.value.page = 1
  fetchVenues()
}

const handleReset = () => {
  // 重置筛选条件：清空 typeId/keyword，并回到第一页。
  filters.value.typeId = null
  filters.value.keyword = ''
  pagination.value.page = 1
  fetchVenues()
}

const handlePageSizeChange = (size) => {
  // 修改每页条数：回到第一页。
  pagination.value.size = size
  pagination.value.page = 1
  fetchVenues()
}

const getStatusType = (status) => {
  // 场地状态 -> Tag 类型（仅用于 UI）。
  const map = {
    ACTIVE: 'success',
    MAINTENANCE: 'warning',
    DISABLED: 'error'
  }
  return map[status] || 'default'
}

const getStatusText = (status) => {
  // 场地状态 -> 文案。
  const map = {
    ACTIVE: '可预约',
    MAINTENANCE: '维护中',
    DISABLED: '已停用'
  }
  return map[status] || status
}

onMounted(() => {
  fetchTypes()
  fetchVenues()
})
</script>

<style scoped>
.venue-list-page {
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

.venue-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.venue-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.venue-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.venue-cover {
  position: relative;
  height: 180px;
  overflow: hidden;
}

.venue-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.venue-tag {
  position: absolute;
  top: 12px;
  right: 12px;
}

.venue-body {
  padding: 16px;
}

.venue-type {
  margin-bottom: 8px;
}

.venue-name {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin: 0 0 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.venue-location {
  font-size: 13px;
  color: #666;
  margin: 0 0 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.venue-spec {
  font-size: 13px;
  color: #999;
  margin: 0 0 12px;
}

.venue-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.venue-price {
  font-size: 20px;
  font-weight: 600;
  color: #f5222d;
}

.venue-price small {
  font-size: 12px;
  font-weight: normal;
  color: #999;
}

.venue-clicks {
  font-size: 12px;
  color: #999;
}

.pagination {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}
</style>
