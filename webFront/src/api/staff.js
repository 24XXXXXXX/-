import { http } from './http'

// 员工端 API（/api/staff/**）
//
// 说明：
// - 这些接口对应“员工端页面”（/staff 前缀路由），前端路由守卫会用 meta.requiresStaff 做入口限制
// - 但最终安全边界仍在后端：后端会校验 ROLE_STAFF/ROLE_ADMIN，并做资源级别校验（是否可领取/是否已指派等）

// 员工投诉列表
export function getStaffComplaints(params) {
  // 员工端投诉列表：通常是“与我相关/可处理”的集合（具体口径由后端决定）。
  return http.get('/api/staff/complaints', { params })
}

// 员工投诉详情
export function getStaffComplaintDetail(id) {
  // 员工端投诉详情：后端会校验是否允许当前员工查看该工单。
  return http.get(`/api/staff/complaints/${id}`)
}

// 更新投诉状态
export function updateComplaintStatus(id, data) {
  // 员工推进工单状态机：例如 SUBMITTED -> IN_PROGRESS -> RESOLVED（由后端校验合法性）。
  return http.post(`/api/staff/complaints/${id}/status`, data)
}

// 添加投诉消息
export function addStaffComplaintMessage(id, data) {
  // 员工追加消息：用于处理过程沟通；后端可能在此触发“领取/占用”逻辑（避免多人处理同一工单）。
  return http.post(`/api/staff/complaints/${id}/messages`, data)
}

// 巡检列表
export function getInspections(params) {
  // 员工“我的巡检上报”列表：仅返回自己提交的巡检记录。
  return http.get('/api/staff/inspections', { params })
}

// 巡检详情
export function getInspectionDetail(id) {
  // 巡检详情：后端会校验该巡检是否属于当前员工。
  return http.get(`/api/staff/inspections/${id}`)
}

// 提交巡检
export function createInspection(data) {
  // 创建巡检上报：data 通常包含 targetType/关联 ID/描述/附件等（以表单与后端 DTO 为准）。
  return http.post('/api/staff/inspections', data)
}

// 更新巡检状态
export function updateInspectionStatus(id, data) {
  // 更新巡检状态：员工只能更新自己的上报；状态合法性由后端统一校验。
  return http.post(`/api/staff/inspections/${id}/status`, data)
}

// 日报统计
export function getDailyReport(params) {
  // 员工个人日报：按 date 查询，返回当日聚合统计（投诉/巡检/核销等）。
  return http.get('/api/staff/daily-report', { params })
}
