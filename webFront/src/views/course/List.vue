<template>
  <div class="course-list-page">
    <div class="page-wrapper">
      <div class="page-header">
        <h1>教练课程</h1>
        <p>专业教练指导，提升您的运动技能</p>
      </div>

      <!-- 筛选区 -->
      <n-card class="filter-card">
        <n-space>
          <n-select
            v-model:value="filters.type"
            placeholder="课程类型"
            clearable
            :options="typeOptions"
            style="width: 150px"
            @update:value="handleSearch"
          />
          <n-input
            v-model:value="filters.keyword"
            placeholder="搜索课程名称"
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

      <!-- 课程列表 -->
      <n-spin :show="loading">
        <div v-if="courses.length" class="course-grid">
          <div
            v-for="course in courses"
            :key="course.id"
            class="course-card"
            @click="goDetail(course.id)"
          >
            <div class="course-cover">
              <img :src="course.coverUrl || '/placeholder.svg'" :alt="course.title" />
              <n-tag class="course-tag" type="success" size="small">
                {{ course.category || '课程' }}
              </n-tag>
            </div>
            <div class="course-body">
              <h3 class="course-name">{{ course.title }}</h3>
              <div class="course-coach">
                <n-icon><PersonOutline /></n-icon>
                <span>{{ course.coachUsername || '专业教练' }}</span>
              </div>
              <p class="course-venue" v-if="course.venueName">
                <n-icon><LocationOutline /></n-icon>
                {{ course.venueName }}
              </p>
              <div class="course-footer">
                <span class="course-price">¥{{ course.price }}<small>/节</small></span>
                <n-rate :value="course.rating || 5" readonly size="small" />
              </div>
            </div>
          </div>
        </div>

        <n-empty v-else-if="!loading" description="暂无课程数据" />
      </n-spin>

      <!-- 分页 -->
      <div v-if="total > 0" class="pagination">
        <n-pagination
          v-model:page="pagination.page"
          :page-size="pagination.pageSize"
          :item-count="total"
          show-size-picker
          :page-sizes="[12, 24, 36]"
          @update:page="fetchCourses"
          @update:page-size="handlePageSizeChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { SearchOutline, PersonOutline, LocationOutline } from '@vicons/ionicons5'
import { getCourses, getCourseCategories } from '@/api/course'

// 公共页面「课程列表」
//
// 页面职责：
// - 展示教练课程列表（课程封面/标题/教练/场地/价格/评分）
// - 支持筛选：课程分类（category）与关键字搜索（keyword）
// - 分页加载，点击卡片进入课程详情页（/courses/:id）
//
// 数据流：
// onMounted -> fetchCategories -> fetchCourses
// - 分类：getCourseCategories() -> typeOptions
// - 列表：getCourses(params) -> courses/total
//
// 参数口径与兼容：
// - 本页分页使用 page/size
// - 筛选：后端常用 category/keyword
// - UI 上为了更语义化使用 filters.type，提交前会转换为 category
// - 空条件会 delete 掉，避免后端把空串当作有效筛选

const router = useRouter()
const loading = ref(false)
const courses = ref([])
const total = ref(0)

const typeOptions = ref([])

const filters = reactive({
  type: null,
  keyword: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 12
})

const fetchCategories = async () => {
  // 拉取课程分类：
  // - 返回结构兼容：raw 是数组 / raw.data 是数组
  // - 最终转成 n-select 的 {label,value}
  try {
    const res = await getCourseCategories()
    const raw = res?.data
    const list = Array.isArray(raw) ? raw : Array.isArray(raw?.data) ? raw.data : []
    typeOptions.value = list
      .map((s) => (typeof s === 'string' ? s.trim() : ''))
      .filter((s) => !!s)
      .map((s) => ({ label: s, value: s }))
  } catch (e) {
    typeOptions.value = []
  }
}

const fetchCourses = async () => {
  // 拉取课程列表：
  // - params 合并分页与筛选
  // - filters.type 转换为 params.category（后端字段名）
  // - 返回结构兼容 items/list/直接数组
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.pageSize,
      ...filters
    }
    if (params.type) {
      params.category = params.type
      delete params.type
    }
    if (!params.keyword) delete params.keyword
    
    const res = await getCourses(params)
    courses.value = res.data?.items || res.data?.list || res.data || []
    total.value = res.data?.total ?? courses.value.length
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  // 搜索：回到第一页重新拉取。
  pagination.page = 1
  fetchCourses()
}

const handleReset = () => {
  // 重置：清空筛选并回到第一页。
  filters.type = null
  filters.keyword = ''
  pagination.page = 1
  fetchCourses()
}

const handlePageSizeChange = (size) => {
  // 修改每页条数：回到第一页。
  pagination.pageSize = size
  pagination.page = 1
  fetchCourses()
}

const goDetail = (id) => {
  // 跳转课程详情页。
  router.push(`/courses/${id}`)
}

onMounted(() => {
  fetchCategories().finally(() => {
    fetchCourses()
  })
})
</script>

<style scoped>
.course-list-page {
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

.course-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.course-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.course-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.course-cover {
  position: relative;
  height: 180px;
  overflow: hidden;
}

.course-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.course-tag {
  position: absolute;
  top: 12px;
  right: 12px;
}

.course-body {
  padding: 16px;
}

.course-name {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin: 0 0 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.course-coach {
  font-size: 13px;
  color: #666;
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.course-venue {
  font-size: 13px;
  color: #999;
  margin: 0 0 12px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.course-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.course-price {
  font-size: 20px;
  font-weight: 600;
  color: #f5222d;
}

.course-price small {
  font-size: 12px;
  font-weight: normal;
  color: #999;
}

.pagination {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}
</style>
