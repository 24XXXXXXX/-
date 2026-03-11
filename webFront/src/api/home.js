import { http } from './http'

// 首页/公共内容相关 API
//
// 特点：
// - 多数接口是“公共可访问”的（不需要登录），用于首页展示内容
// - 轮播图/公告等内容由管理端在后台维护（管理端接口通常在 admin.js 中）
// - http.js 会做统一的 baseURL/错误处理；即便是公共接口，也沿用同一个 http 实例便于拦截与日志

// 首页轮播图
export function getBanners() {
  // 首页轮播图列表：通常只返回 enabled=true 的条目，并按 sort/order 排序（以后端为准）。
  return http.get('/api/home-banners')
}

// 首页推荐聚合
export function getRecommendations() {
  // 首页推荐聚合：
  // - 这是一个“聚合接口”，后端会把多个模块的推荐数据拼成一个响应（场地/课程/视频/器材等）
  // - 前端通常直接按区块渲染，避免首页发起大量并发请求
  return http.get('/api/home/recommendations')
}

// 公告列表
export function getNotices(params) {
  // 公告列表（公共）：
  // - params 常见包含分页 + 关键词/发布时间筛选
  // - 通常只返回已发布的公告；草稿/未发布由管理端接口维护
  return http.get('/api/notices', { params })
}

// 公告详情
export function getNoticeDetail(id) {
  // 公告详情：
  // - 返回内容可能包含富文本 content、封面 coverUrl、发布时间等
  // - 若后端支持“仅发布可见”，这里也会做 published 校验
  return http.get(`/api/notices/${id}`)
}
