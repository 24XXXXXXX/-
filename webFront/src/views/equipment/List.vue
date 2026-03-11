<template>
  <div class="equipment-list-page">
    <div class="page-wrapper">
      <div class="page-header">
        <h1>体育器材</h1>
        <p>优质运动装备，助力您的运动生活</p>
      </div>

      <!-- 筛选区 -->
      <n-card class="filter-card">
        <n-space>
          <n-select
            v-model:value="filters.categoryId"
            placeholder="器材分类"
            clearable
            :options="categoryOptions"
            style="width: 150px"
            @update:value="handleSearch"
          />
          <n-input
            v-model:value="filters.keyword"
            placeholder="搜索器材名称"
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

      <!-- 器材列表 -->
      <n-spin :show="loading">
        <div v-if="equipments.length" class="equipment-grid">
          <div
            v-for="item in equipments"
            :key="item.id"
            class="equipment-card"
            @click="goDetail(item.id)"
          >
            <div class="equipment-cover">
              <img :src="item.coverUrl || '/placeholder.svg'" :alt="item.name" />
              <n-tag v-if="item.stock <= 10 && item.stock > 0" class="equipment-tag" type="warning" size="small">
                库存紧张
              </n-tag>
              <n-tag v-else-if="item.stock === 0" class="equipment-tag" type="error" size="small">
                已售罄
              </n-tag>
            </div>
            <div class="equipment-body">
              <div class="equipment-category" v-if="item.categoryName">
                <n-tag type="info" size="small">{{ item.categoryName }}</n-tag>
              </div>
              <h3 class="equipment-name">{{ item.name }}</h3>
              <p class="equipment-desc">{{ item.description || '暂无描述' }}</p>
              <div class="equipment-footer">
                <span class="equipment-price">¥{{ item.price }}</span>
                <span class="equipment-stock">库存: {{ item.stock }}</span>
              </div>
            </div>
          </div>
        </div>

        <n-empty v-else-if="!loading" description="暂无器材数据" />
      </n-spin>

      <!-- 分页 -->
      <div v-if="total > 0" class="pagination">
        <n-pagination
          v-model:page="pagination.page"
          :page-size="pagination.pageSize"
          :item-count="total"
          show-size-picker
          :page-sizes="[12, 24, 36]"
          @update:page="fetchEquipments"
          @update:page-size="handlePageSizeChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { SearchOutline } from '@vicons/ionicons5'
import { getEquipments, getCategories } from '@/api/equipment'

// 公共页面「器材列表」（商城入口）
//
// 页面职责：
// - 展示器材商品列表
// - 提供筛选：器材分类（categoryId）+ 关键字（keyword）
// - 分页加载，点击卡片进入器材详情页（/equipment/:id）
//
// 数据流：
// onMounted -> fetchCategories + fetchEquipments
// - fetchCategories：getCategories() -> categoryOptions
// - fetchEquipments：getEquipments(params) -> equipments/total
//
// 参数口径与兼容：
// - 本页分页使用 page/pageSize（与部分其它模块的 page/size 不同）
// - 筛选字段为空时 delete 掉，避免后端把空串当作有效条件
//
// UI 逻辑：
// - stock<=10 显示“库存紧张”，stock==0 显示“已售罄”

const router = useRouter()
const loading = ref(false)
const equipments = ref([])
const total = ref(0)
const categoryOptions = ref([])

const filters = reactive({
  categoryId: null,
  keyword: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 12
})

const fetchCategories = async () => {
  // 拉取器材分类：用于筛选下拉框。
  try {
    const res = await getCategories()
    categoryOptions.value = (res.data || []).map(c => ({ label: c.name, value: c.id }))
  } catch (e) {
    console.error(e)
  }
}

const fetchEquipments = async () => {
  // 拉取器材列表：
  // - params 包含分页与筛选
  // - 返回结构此处以 res.data.list 为主，兼容 res.data 直接数组
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      pageSize: pagination.pageSize,
      ...filters
    }
    if (!params.categoryId) delete params.categoryId
    if (!params.keyword) delete params.keyword
    
    const res = await getEquipments(params)
    equipments.value = res.data?.list || res.data || []
    total.value = res.data?.total || equipments.value.length
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  // 搜索：回到第一页重新拉取。
  pagination.page = 1
  fetchEquipments()
}

const handleReset = () => {
  // 重置筛选：清空条件并回到第一页。
  filters.categoryId = null
  filters.keyword = ''
  pagination.page = 1
  fetchEquipments()
}

const handlePageSizeChange = (size) => {
  // 修改每页条数：回到第一页。
  pagination.pageSize = size
  pagination.page = 1
  fetchEquipments()
}

const goDetail = (id) => {
  // 跳转器材详情页。
  router.push(`/equipment/${id}`)
}

onMounted(() => {
  fetchCategories()
  fetchEquipments()
})
</script>

<style scoped>
.equipment-list-page {
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

.equipment-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.equipment-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.equipment-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.equipment-cover {
  position: relative;
  height: 180px;
  overflow: hidden;
}

.equipment-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.equipment-tag {
  position: absolute;
  top: 12px;
  right: 12px;
}

.equipment-body {
  padding: 16px;
}

.equipment-category {
  margin-bottom: 8px;
}

.equipment-name {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin: 0 0 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.equipment-desc {
  font-size: 13px;
  color: #999;
  margin: 0 0 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.equipment-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.equipment-price {
  font-size: 20px;
  font-weight: 600;
  color: #f5222d;
}

.equipment-stock {
  font-size: 12px;
  color: #999;
}

.pagination {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}
</style>
