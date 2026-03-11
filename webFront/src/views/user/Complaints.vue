<template>
  <div class="complaints-page">
    <n-card title="我的投诉">
      <template #header-extra>
        <n-button type="primary" @click="$router.push('/user/complaints/create')">提交投诉</n-button>
      </template>

      <n-spin :show="loading">
        <div v-if="complaints.length" class="complaint-list">
          <div
            v-for="complaint in complaints"
            :key="complaint.id"
            class="complaint-item"
            @click="goDetail(complaint.id)"
          >
            <div class="complaint-header">
              <n-tag :type="getTypeTagType(complaint.complaintType)" size="small">{{ getTypeText(complaint.complaintType) }}</n-tag>
              <span class="complaint-time">{{ formatDate(complaint.createdAt) }}</span>
              <n-tag :type="getStatusType(complaint.status)">{{ getStatusText(complaint.status) }}</n-tag>
            </div>
            <p class="complaint-content">{{ complaint.content }}</p>
          </div>
        </div>
        <n-empty v-else description="暂无投诉记录">
          <template #extra>
            <n-button type="primary" @click="$router.push('/user/complaints/create')">提交投诉</n-button>
          </template>
        </n-empty>
      </n-spin>

      <div class="pagination-wrap" v-if="total > 0">
        <n-pagination
          v-model:page="pagination.page"
          :page-size="pagination.pageSize"
          :item-count="total"
          @update:page="fetchComplaints"
        />
      </div>
    </n-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMyComplaints } from '@/api/complaint'
import dayjs from 'dayjs'

// 用户端「我的投诉」列表页
//
// 页面职责：
// - 分页展示当前用户提交的投诉单
// - 提供“提交投诉”入口，跳转到创建页
// - 点击列表项进入投诉详情页（/user/complaints/:id）查看处理进度与沟通记录
//
// 数据流：
// onMounted -> fetchComplaints -> getMyComplaints(params) -> complaints/total -> template 渲染
//
// 分页与字段兼容：
// - params 使用 page/pageSize
// - 返回结构兼容 items/list/content
// - total 兼容 total 或 totalElements
//
// 权限边界：
// - 本页接口需要登录
// - “只能查看自己的投诉单”由后端做资源级校验

const router = useRouter()
const loading = ref(false)
const complaints = ref([])
const total = ref(0)

const pagination = reactive({
  page: 1,
  pageSize: 10
})

const formatDate = (date) => dayjs(date).format('YYYY-MM-DD HH:mm')

const typeMap = {
  VENUE: { text: '场地问题', type: 'info' },
  EQUIPMENT: { text: '器材问题', type: 'warning' },
  COURSE: { text: '课程问题', type: 'primary' },
  OTHER: { text: '其他问题', type: 'default' }
}

// typeMap：投诉类型码 -> 文案/Tag 类型（仅影响 UI 展示）。

const getTypeText = (type) => typeMap[type]?.text || type
const getTypeTagType = (type) => typeMap[type]?.type || 'default'

const statusMap = {
  SUBMITTED: { text: '待处理', type: 'warning' },
  ASSIGNED: { text: '已指派', type: 'info' },
  IN_PROGRESS: { text: '处理中', type: 'info' },
  RESOLVED: { text: '已解决', type: 'success' }
}

// statusMap：投诉状态机的展示映射。
// 实际状态流转（指派/处理/解决等）由后台员工端/管理员端推进。

const getStatusText = (status) => statusMap[status]?.text || status
const getStatusType = (status) => statusMap[status]?.type || 'default'

const fetchComplaints = async () => {
  // 拉取投诉列表：
  // - 分页参数 page/pageSize
  // - 返回结构兼容 items/list/content
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      pageSize: pagination.pageSize
    }
    const res = await getMyComplaints(params)
    complaints.value = res.data?.items || res.data?.list || res.data?.content || []
    total.value = res.data?.total ?? res.data?.totalElements ?? complaints.value.length
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const goDetail = (id) => {
  // 进入详情页。
  router.push(`/user/complaints/${id}`)
}

onMounted(() => {
  fetchComplaints()
})
</script>

<style scoped>
.complaints-page {
  padding: 20px;
}
.complaint-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.complaint-item {
  padding: 16px;
  border: 1px solid #eee;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.complaint-item:hover {
  border-color: #18a058;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}
.complaint-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.complaint-time {
  font-size: 12px;
  color: #999;
  margin-left: auto;
  margin-right: 10px;
}
.complaint-title {
  margin: 0 0 8px;
  font-size: 15px;
}
.complaint-content {
  margin: 0;
  font-size: 14px;
  color: #666;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
