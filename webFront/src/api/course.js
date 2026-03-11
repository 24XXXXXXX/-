import { http } from './http'

// 课程相关 API
//
// 覆盖的业务链路：
// - 公共/用户端：课程列表/详情/场次、下单报名（booking）、支付、取消、查看我的报名、评价
// - 咨询（course-consultations）：用户与教练/客服之间的“会话 + 消息”模型
// - 核销（verify）：员工/管理员在现场对课程报名进行核销（后端需严格 RBAC + 审计）
//
// 说明：
// - http.js 会自动注入 token，并处理分页字段兼容与 401 refresh
// - 支付/核销/取消等接口属于敏感操作：前端只负责触发，最终合法性校验与状态机推进由后端保证

// 课程列表
export function getCourses(params) {
  // 课程列表（公共页/用户端）：
  // - params 常见包含分页 + 筛选：category、keyword、coachId、priceRange、sort 等（以页面实际传参为准）
  return http.get('/api/courses', { params })
}

// 课程类型（去重）
export function getCourseCategories() {
  return http.get('/api/courses/categories')
}

// 课程详情
export function getCourseDetail(id) {
  return http.get(`/api/courses/${id}`)
}

// 课程场次
export function getCourseSessions(courseId) {
  // “场次”通常表示课程的具体上课时间与容量（例如每周一 19:00-20:00 的一节）。
  // 选择场次后再创建 course-booking。
  return http.get(`/api/courses/${courseId}/sessions`)
}

// 课程评价
export function getCourseReviews(courseId, params) {
  // 课程评价列表：一般是公开展示，支持分页/排序。
  return http.get(`/api/courses/${courseId}/reviews`, { params })
}

// 创建课程预约
export function createCourseBooking(data) {
  // 创建课程报名（预约单）：
  // - data 通常包含 courseId/sessionId、人数、联系方式等
  // - 后端会校验场次是否存在/是否还有余量/用户是否重复报名等
  return http.post('/api/course-bookings', data)
}

// 支付课程预约
export function payCourseBooking(id) {
  // 支付报名：资金敏感操作。
  // - 后端会校验当前状态是否允许支付（例如 CREATED -> PAID）
  // - 若接入真实支付，这里通常只创建支付单/返回支付参数；本项目以实现为准
  return http.post(`/api/course-bookings/${id}/pay`)
}

// 取消课程预约
export function cancelCourseBooking(id) {
  // 取消报名：
  // - 状态机推进（例如 CREATED/PAID -> CANCELED/REFUNDED）
  // - 后端可能会根据开课时间限制取消窗口，并处理退款/余额回退等
  return http.post(`/api/course-bookings/${id}/cancel`)
}

// 我的课程预约列表
export function getMyCourseBookings(params) {
  // 我的课程报名列表：通常支持分页 + 状态筛选（待支付/已支付/已核销/已取消）。
  return http.get('/api/course-bookings', { params })
}

// 我的课程预约详情
export function getMyCourseBookingDetail(id) {
  return http.get(`/api/course-bookings/${id}`)
}

// 提交课程评价
export function submitCourseReview(data) {
  // 提交课程评价：
  // - 通常关联 bookingId/courseId + rating/content
  // - 后端会校验：是否已完成/是否已核销/是否重复评价
  return http.post('/api/course-reviews', data)
}

// 课程咨询
export function createConsultation(data) {
  // 创建咨询会话：
  // - data 通常包含 courseId、问题描述等
  // - 后端会把咨询归属到当前用户，并建立会话（conversation）记录
  return http.post('/api/course-consultations', data)
}

// 我的咨询列表
export function getMyConsultations(params) {
  // 我的咨询列表：
  // - params 常见为分页 + 状态筛选（进行中/已关闭）
  // - 咨询会话通常包含最后一条消息摘要、未读数等（以实现为准）
  return http.get('/api/course-consultations', { params })
}

// 咨询详情
export function getConsultationDetail(id) {
  return http.get(`/api/course-consultations/${id}`)
}

// 发送咨询消息
export function sendConsultationMessage(id, data) {
  // 在某个咨询会话下追加消息：
  // - data 通常包含 message 内容或附件信息
  // - 后端需校验“会话归属/是否允许发言/是否已关闭”等
  return http.post(`/api/course-consultations/${id}/messages`, data)
}

// 核销课程预约（员工/管理员）
export function verifyCourseBooking(data) {
  // 课程报名核销：
  // - 通常由员工/管理员在现场对“到课/验票”进行确认
  // - data 结构取决于后端（例如 {bookingId, verifyCode}）
  // - 核销是强审计动作：后端必须防重复核销，并记录核销人/时间
  return http.post('/api/course-bookings/verify', data)
}
