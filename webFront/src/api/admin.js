import { http } from './http'

// 管理员端 API（/api/admin/**）
//
// 说明：
// - 这些接口主要被 /admin 前缀路由下的页面使用（router meta.requiresAdmin）
// - http.js 会自动注入 token，并在遇到 401 时尝试 refresh + 重放请求
// - 对于审批/发布/删除等敏感操作：前端只负责发起请求与提示，真正的权限与审计记录由后端保证

// 用户列表
export function getUsers(params) {
  // 管理端用户列表：支持分页与条件筛选（具体字段以页面传参与后端实现为准）。
  //
  // role 参数规范化：
  // - 有些页面/历史代码可能传 ROLE_ADMIN/ROLE_USER 这种“带前缀”的字符串
  // - 但本项目后端接口通常期望 ADMIN/USER 这种“去掉 ROLE_ 前缀”的代码
  const p = { ...(params || {}) }
  if (typeof p.role === 'string' && p.role.startsWith('ROLE_')) {
    p.role = p.role.slice('ROLE_'.length)
  }
  return http.get('/api/admin/users', { params: p })
}

// 用户详情
export function getUserDetail(id) {
  return http.get(`/api/admin/users/${id}`)
}

// 创建用户
export function createUser(data) {
  return http.post('/api/admin/users', data)
}

// 更新用户
export function updateUser(id, data) {
  return http.put(`/api/admin/users/${id}`, data)
}

// 更新用户状态
export function updateUserStatus(id, data) {
  return http.post(`/api/admin/users/${id}/status`, data)
}

// 更新用户角色
export function updateUserRoles(id, data) {
  return http.post(`/api/admin/users/${id}/roles`, data)
}

// 重置用户密码
export function resetUserPassword(id, data) {
  return http.post(`/api/admin/users/${id}/password`, data)
}

// 更新员工资料
export function updateStaffProfile(id, data) {
  return http.post(`/api/admin/users/${id}/staff-profile`, data)
}

// 教练认证申请列表
export function getCoachApplications(params) {
  return http.get('/api/admin/coach/applications', { params })
}

// 审核通过
export function approveCoachApplication(id) {
  // 教练认证审批通过：属于权限敏感操作（会改变用户角色/状态）。
  return http.post(`/api/admin/coach/applications/${id}/approve`)
}

// 审核拒绝
export function rejectCoachApplication(id, data) {
  return http.post(`/api/admin/coach/applications/${id}/reject`, data)
}

// 充值申请列表
export function getTopupRequests(params) {
  return http.get('/api/admin/wallet/topups', { params })
}

// 审核通过充值
export function approveTopup(id) {
  // 充值审批通过：资金敏感操作（后端会给钱包入账并写流水）。
  return http.post(`/api/admin/wallet/topups/${id}/approve`)
}

// 审核拒绝充值
export function rejectTopup(id, data) {
  return http.post(`/api/admin/wallet/topups/${id}/reject`, data)
}

// 提现申请列表
export function getWithdrawRequests(params) {
  return http.get('/api/admin/withdraw-requests', { params })
}

// 审核通过提现
export function approveWithdraw(id) {
  // 提现审批通过：资金敏感操作（后端会推进提现流程并记录操作人用于审计）。
  return http.post(`/api/admin/withdraw-requests/${id}/approve`)
}

// 审核拒绝提现
export function rejectWithdraw(id, data) {
  return http.post(`/api/admin/withdraw-requests/${id}/reject`, data)
}

// 投诉列表
export function getComplaints(params) {
  return http.get('/api/admin/complaints', { params })
}

// 投诉详情
export function getComplaintDetail(id) {
  return http.get(`/api/admin/complaints/${id}`)
}

// 管理员追加投诉消息
export function addAdminComplaintMessage(id, data) {
  return http.post(`/api/admin/complaints/${id}/messages`, data)
}

// 管理员更新投诉状态
export function updateAdminComplaintStatus(id, data) {
  return http.post(`/api/admin/complaints/${id}/status`, data)
}

// 指派投诉
export function assignComplaint(id, data) {
  return http.post(`/api/admin/complaints/${id}/assign`, data)
}

// 巡检列表
export function getInspections(params) {
  return http.get('/api/admin/inspections', { params })
}

// 巡检详情
export function getInspectionDetail(id) {
  return http.get(`/api/admin/inspections/${id}`)
}

// 更新巡检状态
export function updateInspectionStatus(id, data) {
  return http.post(`/api/admin/inspections/${id}/status`, data)
}

// 公告列表
export function getNotices(params) {
  return http.get('/api/admin/notices', { params })
}

// 公告详情
export function getNoticeDetail(id) {
  return http.get(`/api/admin/notices/${id}`)
}

// 创建公告
export function createNotice(data) {
  return http.post('/api/admin/notices', data)
}

// 更新公告
export function updateNotice(id, data) {
  return http.put(`/api/admin/notices/${id}`, data)
}

// 更新公告发布状态
export function updateNoticePublished(id, data) {
  return http.post(`/api/admin/notices/${id}/published`, data)
}

// 上传公告封面
export function uploadNoticeCover(id, file) {
  // 文件上传使用 multipart/form-data：
  // - 这里构造 FormData 并设置 Content-Type
  // - 后端通常返回 { fileName, url } 之类结构供前端回填
  const formData = new FormData()
  formData.append('file', file)
  return http.post(`/api/admin/notices/${id}/cover`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 删除公告
export function deleteNotice(id) {
  return http.delete(`/api/admin/notices/${id}`)
}

// 轮播图列表
export function getBanners(params) {
  return http.get('/api/admin/home-banners', { params })
}

// 创建轮播图
export function createBanner(data) {
  return http.post('/api/admin/home-banners', data)
}

// 更新轮播图
export function updateBanner(id, data) {
  return http.put(`/api/admin/home-banners/${id}`, data)
}

// 更新轮播图启用状态
export function updateBannerEnabled(id, data) {
  return http.post(`/api/admin/home-banners/${id}/enabled`, data)
}

// 上传轮播图图片
export function uploadBannerImage(id, file) {
  // 轮播图图片上传：同样是 multipart/form-data。
  const formData = new FormData()
  formData.append('file', file)
  return http.post(`/api/admin/home-banners/${id}/image`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 删除轮播图
export function deleteBanner(id) {
  return http.delete(`/api/admin/home-banners/${id}`)
}

// 系统配置列表
export function getSysConfigs(params) {
  return http.get('/api/admin/sys-configs', { params })
}

// 更新系统配置
export function updateSysConfigs(data) {
  // 系统配置 upsert：
  // - 既支持传单个对象，也支持传数组（批量保存）
  // - 同时兼容不同字段命名：cfgKey/cfgValue 或 key/value 或 configKey/configValue
  //   这样页面层即使历史字段不统一，也能在 API 层做一次“适配/归一化”。
  const normalize = (item) => {
    if (!item || typeof item !== 'object') return item
    return {
      cfgKey: item.cfgKey ?? item.configKey ?? item.key,
      cfgValue: item.cfgValue ?? item.configValue ?? item.value,
      remark: item.remark
    }
  }

  if (Array.isArray(data)) {
    return Promise.all(data.map((it) => http.post('/api/admin/sys-configs', normalize(it))))
  }
  return http.post('/api/admin/sys-configs', normalize(data))
}

// 数据监控指标
export function getMetrics(params) {
  // 管理端仪表盘指标：对应后端 /api/admin/metrics。
  // params 通常包含时间范围（startDate/endDate 等）或其它过滤条件（以实现为准）。
  return http.get('/api/admin/metrics', { params })
}
