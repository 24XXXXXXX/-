import { http } from './http'

// 教练端 API（/api/coach/**）
//
// 说明：
// - 这些接口主要给教练中心页面使用，前端路由通常带 meta.requiresCoach 做入口限制
// - 但最终权限仍以服务端校验为准（是否为教练、是否拥有该课程/视频、是否可操作该报名单等）
// - 涉及资金/核销/状态切换的接口属于敏感操作：后端应记录操作人并校验状态机合法性

// 提交教练认证申请
export function submitCoachApplication(data) {
  // 用户申请成为教练：
  // - data 往往包含个人信息、资质材料、擅长领域等
  // - 审批在管理端完成（见 admin.js：/api/admin/coach/applications/**）
  return http.post('/api/coach/applications', data)
}

// 我的认证申请
export function getMyCoachApplication() {
  // 查询当前用户自己的教练认证申请状态：
  // - 用于展示“待审核/通过/拒绝 + 拒绝原因”等
  return http.get('/api/coach/applications/me')
}

// 教练课程列表
export function getCoachCourses(params) {
  // 教练自己发布的课程列表：通常支持分页 + 状态筛选（草稿/上架/下架等）。
  return http.get('/api/coach/courses', { params })
}

// 教练课程详情
export function getCoachCourseDetail(id) {
  return http.get(`/api/coach/courses/${id}`)
}

// 教练课程类型（去重）
export function getCoachCourseCategories() {
  return http.get('/api/coach/courses/categories')
}

// 创建课程
export function createCourse(data) {
  // 教练创建课程：
  // - data 一般包含标题、分类、价格、简介、适用人群等
  // - 后端应把课程与当前教练账号绑定（资源归属）
  return http.post('/api/coach/courses', data)
}

// 更新课程
export function updateCourse(id, data) {
  return http.put(`/api/coach/courses/${id}`, data)
}

// 更新课程状态
export function updateCourseStatus(id, status) {
  // 课程状态切换（上架/下架/禁用等）：
  // - status 枚举以后端为准
  // - 属于典型状态机动作，后端会校验当前状态是否允许切换
  return http.post(`/api/coach/courses/${id}/status`, { status })
}

// 获取课程场次
export function getCoachCourseSessions(courseId) {
  return http.get(`/api/coach/courses/${courseId}/sessions`)
}

// 创建课程场次
export function createCourseSession(courseId, data) {
  // 为课程创建场次：
  // - data 常见包含开始/结束时间、容量、是否开放报名等
  // - 场次是报名（course-bookings）的承载对象
  return http.post(`/api/coach/courses/${courseId}/sessions`, data)
}

// 更新场次状态
export function updateSessionStatus(id, status) {
  // 场次状态切换：例如开放/关闭报名、临时停课等。
  return http.post(`/api/coach/course-sessions/${id}/status`, { status })
}

// 教练预约列表
export function getCoachBookings(params) {
  // 教练侧报名单列表：
  // - params 通常支持分页 + 状态筛选（待接受/已接受/已拒绝/已核销等）
  return http.get('/api/coach/course-bookings', { params })
}

// 接受预约
export function acceptBooking(id) {
  // 接受报名单：
  // - 会推进状态机（例如 PENDING -> ACCEPTED）
  // - 后端应校验报名单归属（必须是自己课程/场次）
  return http.post(`/api/coach/course-bookings/${id}/accept`)
}

// 拒绝预约
export function rejectBooking(id, data) {
  // 拒绝报名单：
  // - data 通常包含拒绝原因
  // - 涉及资金时可能会触发退款/余额回退，最终以服务端为准
  return http.post(`/api/coach/course-bookings/${id}/reject`, data)
}

// 核销预约
export function verifyCoachBooking(data) {
  // 教练端核销报名：
  // - 一般用于到课确认（与 staff/admin 核销类似，入口不同）
  // - 后端必须防重复核销，并记录核销人/时间用于审计
  return http.post('/api/coach/course-bookings/verify', data)
}

// 教练视频列表
export function getCoachVideos(params) {
  // 教练自己发布的视频列表：通常支持分页 + 状态筛选。
  return http.get('/api/coach/videos', { params })
}

// 教练视频详情
export function getCoachVideoDetail(id) {
  return http.get(`/api/coach/videos/${id}`)
}

// 上传视频文件
export function uploadVideoFile(file, onProgress) {
  // 上传视频文件（multipart/form-data）：
  // - 大文件上传常需要进度条，axios 的 onUploadProgress 会回调上传进度
  // - 后端可能返回临时文件 key/url，后续 createVideo 时再关联该文件（以实现为准）
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/api/coach/videos/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress
  })
}

// 上传视频封面
export function uploadVideoCover(id, file) {
  // 上传视频封面：
  // - 通常在视频创建后调用，用于设置封面图
  const formData = new FormData()
  formData.append('file', file)
  return http.post(`/api/coach/videos/${id}/cover`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 创建视频
export function createVideo(data) {
  return http.post('/api/coach/videos', data)
}

// 更新视频
export function updateVideo(id, data) {
  return http.put(`/api/coach/videos/${id}`, data)
}

// 更新视频状态
export function updateVideoStatus(id, status) {
  // 视频状态切换（上架/下架/审核相关等）：status 枚举以后端为准。
  return http.post(`/api/coach/videos/${id}/status`, { status })
}

// 教练咨询列表
export function getCoachConsultations(params) {
  // 教练侧咨询会话列表：
  // - params 通常为分页 + 状态筛选（进行中/已关闭）
  return http.get('/api/coach/course-consultations', { params })
}

// 教练咨询详情
export function getCoachConsultationDetail(id) {
  return http.get(`/api/coach/course-consultations/${id}`)
}

// 回复咨询
export function replyConsultation(id, data) {
  // 回复某个咨询会话：
  // - data 通常包含回复内容
  // - 后端校验：会话归属/是否已关闭/是否允许回复
  return http.post(`/api/coach/course-consultations/${id}/reply`, data)
}

// 关闭咨询
export function closeConsultation(id) {
  // 关闭咨询会话：
  // - 关闭后通常不允许继续发消息（具体以服务端为准）
  return http.post(`/api/coach/course-consultations/${id}/close`)
}

// 提现申请列表
export function getWithdrawRequests(params) {
  // 教练侧提现申请列表：
  // - 这里在 API 层兼容 size/pageSize
  // - status 用于筛选（待审/通过/拒绝等）
  const p = {
    page: params?.page,
    size: params?.size ?? params?.pageSize,
    status: params?.status
  }
  return http.get('/api/coach/withdraw-requests', { params: p })
}

// 提交提现申请
export function submitWithdrawRequest(data) {
  // 提交提现申请：资金敏感操作。
  // - data 常见包含金额、收款信息等
  // - 后端会校验余额是否足够、是否满足最小提现额度、并记录操作人
  // - 审批通常在管理端完成（见后端 /api/admin/withdraw-requests/**）
  return http.post('/api/coach/withdraw-requests', data)
}
