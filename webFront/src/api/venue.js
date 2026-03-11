import { http } from './http'

// 场地与预约相关 API
//
// 本文件同时涵盖：
// - 公共/用户端：浏览场地、查看详情、查询可预约时段、创建/取消预约、查看我的预约
// - 管理端：创建/更新场地、上下架/状态切换、上传场地图片、批量生成时段、后台预约单查询
// - 核销：/api/bookings/verify 通常由员工或管理员执行（在后端做严格权限校验）
// - 评价：提交场地评价 + 获取场地评价列表（公开展示）
//
// 说明：
// - http.js 会统一注入 token、做分页字段兼容（page/pageSize <-> current/size）
// - 前端路由守卫只负责入口限制，最终 RBAC 与资源级校验以服务端为准

// 场地类型
export function getVenueTypes() {
  return http.get('/api/venue/types')
}

// 场地列表
export function getVenues(params) {
  // 场地列表（通常是公共页）：
  // - params 常见包含分页与筛选：type、keyword、region、priceRange、sort 等（以页面实际传参为准）
  // - http.js 会把不同分页字段命名统一成后端可识别的形式
  return http.get('/api/venues', { params })
}

// 场地详情
export function getVenueDetail(id) {
  return http.get(`/api/venues/${id}`)
}

// 获取时段
export function getTimeslots(venueId, params) {
  // 查询某个场地的可用时段：
  // - params 往往包含日期 date（或 startDate/endDate）以及状态筛选（可用/不可用）
  // - 具体时段是否可预约，最终由后端基于状态、是否被占用、是否过期等规则决定
  return http.get(`/api/venues/${venueId}/timeslots`, { params })
}

// 创建场地（管理员）
export function createVenue(data) {
  // 管理端创建场地：
  // - data 通常包括名称、类型、位置/片区、价格、开放时间、描述、容量等
  // - 属于强权限操作：后端需要记录操作人并做字段合法性校验
  return http.post('/api/venues', data)
}

// 更新场地（管理员）
export function updateVenue(id, data) {
  // 管理端更新场地：使用 PATCH 语义表示“部分更新”。
  return http.patch(`/api/venues/${id}`, data)
}

// 更新场地状态（管理员）
export function updateVenueStatus(id, status) {
  // 上下架/停用/维护中等状态切换：
  // - status 的枚举与业务含义以后端为准
  // - 典型用于让前台立即隐藏不可用场地，或阻止继续预约
  return http.patch(`/api/venues/${id}/status`, { status })
}

// 上传场地图片（管理员）
export function uploadVenuePhotos(id, files) {
  // 多文件上传（multipart/form-data）：
  // - 后端字段名约定为 files（这里 append('files', file) 多次）
  // - 常用于场地轮播图/相册
  const formData = new FormData()
  files.forEach(file => formData.append('files', file))
  return http.post(`/api/venues/${id}/photos`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 生成时段（管理员）
export function generateTimeslots(venueId, data) {
  // 批量生成时段：
  // - data 往往包含日期范围、每天的时间段划分、单个时段时长、价格策略等
  // - 这是“批量写入”的后台操作，后端需要保证幂等/避免重复生成（取决于实现）
  return http.post(`/api/venues/${venueId}/timeslots/generate`, data)
}

// 更新时段状态（管理员）
export function updateTimeslotStatus(id, status) {
  // 单个时段状态切换：例如禁用某个时段（维修/活动占用）或重新开放。
  return http.patch(`/api/timeslots/${id}/status`, { status })
}

// 用户预约
export function createBooking(data) {
  // 创建预约（下单）：
  // - data 通常包含 venueId、timeslotId（或时间范围）、联系人信息、备注等
  // - 后端会校验：时段是否可用/是否已被占用/用户是否重复预约等
  return http.post('/api/bookings', data)
}

// 取消预约
export function cancelBooking(id) {
  // 取消预约：
  // - 会推进订单/预约状态机（例如 CREATED/PAID -> CANCELED）
  // - 后端通常会限制“距离开始时间太近不可取消”等规则
  return http.post(`/api/bookings/${id}/cancel`)
}

// 我的预约列表
export function getMyBookings(params) {
  // 我的预约列表：
  // - params 常见为分页 + 状态筛选
  // - 该接口在本项目里还可能返回 reviewed 字段，表示该预约是否已评价（用于前端展示“去评价”按钮）
  return http.get('/api/bookings', { params })
}

// 我的预约详情
export function getMyBookingDetail(id) {
  return http.get(`/api/bookings/${id}`)
}

// 我的预约核销记录
export function getMyBookingVerifyLogs(id) {
  // 核销日志：用于展示“谁在什么时候核销了这笔预约”（审计/对账用途）。
  return http.get(`/api/bookings/${id}/verify-logs`)
}

// 管理员预约列表
export function getAdminBookings(params) {
  // 管理端预约列表：通常支持更丰富的筛选（日期区间、场地、用户、核销状态等）。
  return http.get('/api/admin/bookings', { params })
}

// 管理员预约详情
export function getAdminBookingDetail(id) {
  return http.get(`/api/admin/bookings/${id}`)
}

// 核销预约
export function verifyBooking(data) {
  // 核销入口：
  // - 通常由员工/管理员在现场核销（扫码/输入码/订单号等）
  // - data 的结构取决于后端设计（例如 {bookingId, verifyCode}）
  // - 核销是强审计动作：后端必须校验权限 + 防重复核销 + 记录操作人
  return http.post('/api/bookings/verify', data)
}

// 提交场地评价
export function submitVenueReview(data) {
  // 提交场地评价：
  // - 通常关联 bookingId + rating + content（以实现为准）
  // - 后端会校验：是否已完成/是否允许评价/是否重复评价
  return http.post('/api/venue-reviews', data)
}

// 获取场地评价
export function getVenueReviews(venueId, params) {
  // 场地评价列表（公开展示）：
  // - params 通常为分页与排序
  return http.get(`/api/venues/${venueId}/reviews`, { params })
}
