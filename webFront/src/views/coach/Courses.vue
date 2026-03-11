<template>
  <div class="coach-courses-page">
    <n-card title="我的课程">
      <template #header-extra>
        <n-button type="primary" @click="$router.push('/coach/courses/create')">发布课程</n-button>
      </template>

      <n-spin :show="loading">
        <div v-if="courses.length" class="course-list">
          <div v-for="course in courses" :key="course.id" class="course-card">
            <img :src="course.coverUrl || '/placeholder.svg'" class="course-cover" />
            <div class="course-info">
              <div class="course-header">
                <h3>{{ course.title }}</h3>
                <n-tag :type="course.status === 'ON_SALE' ? 'success' : 'default'">
                  {{ course.status === 'ON_SALE' ? '已上架' : '已下架' }}
                </n-tag>
              </div>
              <p class="course-desc">{{ course.category || '-' }}</p>
              <div class="course-meta">
                <span class="price">¥{{ course.price }}</span>
                <span class="stats">{{ course.durationMinutes || '-' }} 分钟</span>
              </div>
            </div>
            <div class="course-actions">
              <n-button text type="primary" @click="$router.push(`/coach/courses/${course.id}/edit`)">编辑</n-button>
              <n-button
                text
                :type="course.status === 'ON_SALE' ? 'warning' : 'success'"
                @click="toggleStatus(course)"
              >
                {{ course.status === 'ON_SALE' ? '下架' : '上架' }}
              </n-button>
              <n-button text type="info" @click="manageSessions(course)">场次管理</n-button>
            </div>
          </div>
        </div>
        <n-empty v-else description="暂无课程">
          <template #extra>
            <n-button type="primary" @click="$router.push('/coach/courses/create')">发布课程</n-button>
          </template>
        </n-empty>
      </n-spin>

      <div class="pagination-wrap" v-if="total > 0">
        <n-pagination
          v-model:page="pagination.page"
          :page-size="pagination.pageSize"
          :item-count="total"
          @update:page="fetchCourses"
        />
      </div>
    </n-card>

    <!-- 场次管理弹窗 -->
    <n-modal v-model:show="showSessionModal" preset="card" title="场次管理" style="width: 700px">
      <div v-if="currentCourse">
        <n-button type="primary" size="small" @click="showAddSession = true" style="margin-bottom: 16px">
          添加场次
        </n-button>
        <n-table :bordered="false" :single-line="false">
          <thead>
            <tr>
              <th>日期</th>
              <th>时间</th>
              <th>人数</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="session in sessions" :key="session.id">
              <td>{{ formatDate(session.startTime) }}</td>
              <td>{{ formatTime(session.startTime) }}-{{ formatTime(session.endTime) }}</td>
              <td>{{ session.enrolledCount }}/{{ session.capacity }}</td>
              <td>
                <n-tag :type="session.status === 'OPEN' ? 'success' : 'default'" size="small">
                  {{ session.status === 'OPEN' ? '可预约' : '已关闭' }}
                </n-tag>
              </td>
              <td>
                <n-button
                  text
                  size="small"
                  :type="session.status === 'OPEN' ? 'warning' : 'success'"
                  @click="toggleSessionStatus(session)"
                >
                  {{ session.status === 'OPEN' ? '关闭' : '开启' }}
                </n-button>
              </td>
            </tr>
          </tbody>
        </n-table>
        <n-empty v-if="!sessions.length" description="暂无场次" size="small" />
      </div>
    </n-modal>

    <!-- 添加场次弹窗 -->
    <n-modal v-model:show="showAddSession" preset="card" title="添加场次" style="width: 500px">
      <n-form ref="sessionFormRef" :model="sessionForm" :rules="sessionRules" label-placement="left" label-width="80">
        <n-form-item label="日期" path="date">
          <n-date-picker v-model:value="sessionForm.date" type="date" style="width: 100%" />
        </n-form-item>
        <n-form-item label="开始时间" path="startTime">
          <n-time-picker v-model:value="sessionForm.startTime" format="HH:mm" style="width: 100%" />
        </n-form-item>
        <n-form-item label="结束时间" path="endTime">
          <n-time-picker v-model:value="sessionForm.endTime" format="HH:mm" style="width: 100%" />
        </n-form-item>
        <n-form-item label="人数" path="capacity">
          <n-input-number v-model:value="sessionForm.capacity" :min="1" style="width: 100%" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showAddSession = false">取消</n-button>
          <n-button type="primary" :loading="addingSession" @click="handleAddSession">添加</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getCoachCourses, updateCourseStatus, getCoachCourseSessions, createCourseSession, updateSessionStatus } from '@/api/coach'
import dayjs from 'dayjs'

const loading = ref(false)
const courses = ref([])
const total = ref(0)

const pagination = reactive({
  page: 1,
  pageSize: 10
})

const showSessionModal = ref(false)
const currentCourse = ref(null)
const sessions = ref([])

const showAddSession = ref(false)
const addingSession = ref(false)
const sessionFormRef = ref(null)
const sessionForm = reactive({
  date: null,
  startTime: null,
  endTime: null,
  capacity: 10
})

const sessionRules = {
  date: { required: true, type: 'number', message: '请选择日期' },
  startTime: { required: true, type: 'number', message: '请选择开始时间' },
  endTime: { required: true, type: 'number', message: '请选择结束时间' },
  capacity: { required: true, type: 'number', min: 1, message: '请输入人数' }
}

const formatDate = (date) => dayjs(date).format('YYYY-MM-DD')
const formatTime = (date) => dayjs(date).format('HH:mm')

const fetchCourses = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.pageSize
    }
    const res = await getCoachCourses(params)
    courses.value = res.data?.items || res.data?.list || res.data || []
    total.value = res.data?.total ?? courses.value.length
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const toggleStatus = async (course) => {
  try {
    const newStatus = course.status === 'ON_SALE' ? 'OFF_SALE' : 'ON_SALE'
    await updateCourseStatus(course.id, newStatus)
    course.status = newStatus
    window.$message?.success('状态已更新')
  } catch (e) {
    window.$message?.error('操作失败')
  }
}

const manageSessions = async (course) => {
  currentCourse.value = course
  showSessionModal.value = true
  try {
    const res = await getCoachCourseSessions(course.id)
    sessions.value = res.data || []
  } catch (e) {
    console.error(e)
  }
}

const toggleSessionStatus = async (session) => {
  try {
    const newStatus = session.status === 'OPEN' ? 'CLOSED' : 'OPEN'
    await updateSessionStatus(session.id, newStatus)
    session.status = newStatus
    window.$message?.success('状态已更新')
  } catch (e) {
    window.$message?.error('操作失败')
  }
}

const handleAddSession = async () => {
  try {
    await sessionFormRef.value?.validate()
    addingSession.value = true
    
    const dateStr = dayjs(sessionForm.date).format('YYYY-MM-DD')
    const startTimeStr = dayjs(sessionForm.startTime).format('HH:mm')
    const endTimeStr = dayjs(sessionForm.endTime).format('HH:mm')
    
    await createCourseSession(currentCourse.value.id, {
      startTime: `${dateStr}T${startTimeStr}:00`,
      endTime: `${dateStr}T${endTimeStr}:00`,
      capacity: sessionForm.capacity
    })
    
    window.$message?.success('场次添加成功')
    showAddSession.value = false
    // 刷新场次列表
    const res = await getCoachCourseSessions(currentCourse.value.id)
    sessions.value = res.data || []
  } catch (e) {
    if (e?.message) window.$message?.error(e.message)
  } finally {
    addingSession.value = false
  }
}

onMounted(() => {
  fetchCourses()
})
</script>

<style scoped>
.coach-courses-page {
  padding: 20px;
}
.course-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.course-card {
  display: flex;
  gap: 16px;
  padding: 16px;
  border: 1px solid #eee;
  border-radius: 8px;
}
.course-cover {
  width: 160px;
  height: 100px;
  object-fit: cover;
  border-radius: 4px;
}
.course-info {
  flex: 1;
}
.course-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.course-header h3 {
  margin: 0;
  font-size: 16px;
}
.course-desc {
  margin: 0 0 10px;
  font-size: 14px;
  color: #666;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.course-meta {
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
.course-actions {
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
