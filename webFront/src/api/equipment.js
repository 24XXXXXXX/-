import { http } from './http'

// 器材商城相关 API
//
// 该模块同时覆盖三类调用方：
// - 公共/用户端：浏览器材、购物车、下单、查看自己的订单、确认收货、提交评价
// - 管理员端（/api/admin/**）：器材上下架/维护、后台订单管理与发货
// - 员工端（/api/staff/**）：通常用于“仓库/发货人员”处理订单发货
//
// 说明：
// - http.js 会统一处理 token 注入、分页字段兼容、401 refresh + 重放
// - 前端路由守卫仅做入口限制（meta.requiresAdmin/requiresStaff），最终权限与资源级校验由后端保证

// 器材分类
export function getCategories() {
  return http.get('/api/equipment/categories')
}

// 器材列表
export function getEquipments(params) {
  // 器材列表（公共端/用户端）：
  // - params 常见包含分页：page/pageSize 或 current/size（由 http.js 做兼容）
  // - 以及筛选：categoryId、keyword、minPrice/maxPrice、sort 等（以页面实际传参为准）
  return http.get('/api/equipments', { params })
}

// 器材详情
export function getEquipmentDetail(id) {
  return http.get(`/api/equipments/${id}`)
}

// 器材评价列表
export function getEquipmentReviews(id, params) {
  // 某个器材的评价列表：通常支持分页与排序。
  return http.get(`/api/equipments/${id}/reviews`, { params })
}

// 获取购物车
export function getCart() {
  // 当前登录用户的购物车：后端通常会返回 items + 汇总金额/数量等。
  return http.get('/api/equipment/cart')
}

// 更新购物车
export function updateCart(data) {
  // 更新购物车（增删改统一入口）：
  // - data 的具体结构取决于后端设计（例如 {equipmentId, quantity} 或 items 数组）
  // - 常见约定：quantity=0 代表删除该商品
  // - 该接口通常被设计成“幂等更新”（以服务端最终状态为准），以便多端同步
  return http.post('/api/equipment/cart', data)
}

// 创建订单
export function createOrder(data) {
  // 下单：
  // - data 往往包含地址信息/支付方式/备注/购物车条目等
  // - 下单属于关键业务动作，失败时需要把后端错误提示清晰展示给用户
  return http.post('/api/equipment/orders', data)
}

// 我的订单列表
export function getMyOrders(params) {
  // 我的订单列表：通常支持分页 + 状态筛选（待发货/已发货/已完成/已取消等）。
  return http.get('/api/equipment/orders', { params })
}

// 我的订单详情
export function getMyOrderDetail(id) {
  return http.get(`/api/equipment/orders/${id}`)
}

// 确认收货
export function confirmReceive(id) {
  // 确认收货：
  // - 会触发订单状态机推进（例如 SHIPPED -> COMPLETED）
  // - 后端会校验订单归属（只能操作自己的订单）
  return http.post(`/api/equipment/orders/${id}/receive`)
}

// 提交评价
export function submitReview(data) {
  // 提交器材评价：
  // - 通常关联 orderId/equipmentId/rating/content 等字段
  // - 后端一般会校验“是否已购买/是否允许评价/是否重复评价”等约束
  return http.post('/api/equipment-reviews', data)
}

// 管理员器材列表
export function getAdminEquipments(params) {
  // 管理端器材列表：
  // - 相比用户端可能会返回更多管理字段（库存、成本、上下架状态等）
  // - params 同样通常包含分页与筛选
  return http.get('/api/admin/equipments', { params })
}

// 管理员器材详情
export function getAdminEquipmentDetail(id) {
  return http.get(`/api/admin/equipments/${id}`)
}

// 创建器材
export function createEquipment(data) {
  return http.post('/api/admin/equipments', data)
}

// 更新器材
export function updateEquipment(id, data) {
  return http.put(`/api/admin/equipments/${id}`, data)
}

// 更新器材状态
export function updateEquipmentStatus(id, status) {
  // 上下架/启用禁用：
  // - status 的枚举以后端为准
  // - 这是典型“状态切换”接口，后端会做权限校验与状态合法性校验
  return http.post(`/api/admin/equipments/${id}/status`, { status })
}

// 删除器材
export function deleteEquipment(id) {
  return http.delete(`/api/admin/equipments/${id}`)
}

// 上传器材封面
export function uploadEquipmentCover(id, file) {
  // 上传封面图片（multipart/form-data）：
  // - 这里走的是非 /api/admin 前缀的上传接口（以实际后端实现为准）
  // - 即使前端在管理端页面调用，后端仍应校验管理员权限
  const formData = new FormData()
  formData.append('file', file)
  return http.post(`/api/equipments/${id}/cover`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 管理员订单列表
export function getAdminOrders(params) {
  return http.get('/api/admin/equipment/orders', { params })
}

// 管理员订单详情
export function getAdminOrderDetail(id) {
  return http.get(`/api/admin/equipment/orders/${id}`)
}

// 发货
export function shipOrder(id, data) {
  // 管理端发货：
  // - data 通常包含物流公司/运单号/发货备注等
  // - 发货属于敏感操作：后端需记录操作人（审计）并校验订单当前状态是否允许发货
  return http.post(`/api/admin/equipment/orders/${id}/ship`, data)
}

// 员工订单列表
export function getStaffOrders(params) {
  return http.get('/api/staff/equipment/orders', { params })
}

// 员工发货
export function staffShipOrder(id, data) {
  // 员工端发货：
  // - 与管理端发货类似，但权限来自 STAFF
  // - 具体哪些订单允许员工操作（是否分配给该员工/是否为所有员工共享池）由后端定义并校验
  return http.post(`/api/staff/equipment/orders/${id}/ship`, data)
}
