<template>
  <div class="daily-report-page">
    <n-card title="日报统计">
      <div class="date-picker">
        <n-date-picker v-model:value="selectedDate" type="date" @update:value="fetchReport" />
      </div>

      <div v-if="report.region" class="region-text">所属区域：{{ report.region }}</div>

      <n-spin :show="loading">
        <div class="stats-grid">
          <div class="stat-card">
            <span class="stat-label">辖区场地</span>
            <span class="stat-value">{{ report.venuesTotal || 0 }}</span>
            <span class="stat-unit">个</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">维护中场地</span>
            <span class="stat-value">{{ report.venuesMaintenance || 0 }}</span>
            <span class="stat-unit">个</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">禁用场地</span>
            <span class="stat-value">{{ report.venuesDisabled || 0 }}</span>
            <span class="stat-unit">个</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">我的核销</span>
            <span class="stat-value">{{ report.myVenueVerifications || 0 }}</span>
            <span class="stat-unit">次</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">我的投诉更新</span>
            <span class="stat-value">{{ report.myComplaintsUpdated || 0 }}</span>
            <span class="stat-unit">次</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">我的巡检上报</span>
            <span class="stat-value">{{ report.myInspectionReports || 0 }}</span>
            <span class="stat-unit">次</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">我解决的投诉</span>
            <span class="stat-value">{{ report.myComplaintsResolved || 0 }}</span>
            <span class="stat-unit">件</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">低库存器材</span>
            <span class="stat-value">{{ report.equipmentLowStock || 0 }}</span>
            <span class="stat-unit">种</span>
          </div>
        </div>
      </n-spin>
    </n-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getDailyReport } from '@/api/staff'
import dayjs from 'dayjs'

const loading = ref(false)
const selectedDate = ref(Date.now())
const report = ref({})

const fetchReport = async () => {
  loading.value = true
  try {
    const date = dayjs(selectedDate.value).format('YYYY-MM-DD')
    const res = await getDailyReport({ date })
    report.value = res.data || {}
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchReport()
})
</script>

<style scoped>
.daily-report-page {
  padding: 20px;
}
.date-picker {
  margin-bottom: 24px;
}
.region-text {
  margin-bottom: 16px;
  color: #666;
  font-size: 14px;
}
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px;
}
.stat-card {
  padding: 20px;
  background: #f8f8f8;
  border-radius: 8px;
  text-align: center;
}
.stat-label {
  display: block;
  font-size: 14px;
  color: #666;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 32px;
  font-weight: 600;
  color: #18a058;
}
.stat-unit {
  font-size: 14px;
  color: #999;
  margin-left: 4px;
}
</style>
