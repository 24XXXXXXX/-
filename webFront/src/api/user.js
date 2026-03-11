import { http } from './http'

// 用户中心 API（/api/me）
//
// 说明：
// - /api/me 这组接口的语义是“当前登录用户自己的资源”（profile/messages/addresses...）
// - 因为依赖登录态，所以请求会通过 http.js 的拦截器自动携带 Authorization: Bearer <token>
// - 若 token 过期触发 401，http.js 会尝试 refresh 并重放请求（用户侧尽量无感知）

// 获取当前用户信息
export function getMe() {
  return http.get('/api/me')
}

// 更新用户信息
export function updateMe(data) {
  return http.put('/api/me', data)
}

// 上传头像
export function uploadAvatar(file) {
  // 上传文件使用 multipart/form-data：
  // - 这里手动构造 FormData，并显式设置 Content-Type
  // - 后端通常会返回头像 url/路径，页面拿到后再更新 auth store 的 avatarUrl
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/api/me/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 修改密码
export function changePassword(data) {
  // 修改密码：一般需要旧密码/新密码（具体字段以页面/后端为准）。
  return http.post('/api/me/password', data)
}

// 获取消息列表
export function getMessages(params) {
  // 我的消息列表：通常支持分页/类型筛选（params 具体字段以后端实现为准）。
  return http.get('/api/me/messages', { params })
}

// 获取未读消息数
export function getUnreadCount() {
  // 未读数：用于 header 小红点/角标展示。
  return http.get('/api/me/messages/unread-count')
}

// 标记消息已读
export function markMessageRead(id) {
  // 标记单条消息已读：id 为消息 ID。
  return http.post(`/api/me/messages/${id}/read`)
}

// 全部已读
export function markAllRead() {
  // 一键已读：典型用于“全部标为已读”按钮。
  return http.post('/api/me/messages/read-all')
}

// 获取地址列表
export function getAddresses() {
  // 地址管理：通常用于下单时选择收货地址。
  return http.get('/api/me/addresses')
}

// 创建地址
export function createAddress(data) {
  return http.post('/api/me/addresses', data)
}

// 更新地址
export function updateAddress(id, data) {
  return http.put(`/api/me/addresses/${id}`, data)
}

// 删除地址
export function deleteAddress(id) {
  return http.delete(`/api/me/addresses/${id}`)
}
