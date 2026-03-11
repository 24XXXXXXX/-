import { http } from './http'

// 视频/课程视频相关 API
//
// 典型业务：
// - 公共端：浏览视频列表/分类、查看视频详情（详情里可能包含“是否已购买/是否可播放”的字段）
// - 用户端：购买视频（解锁观看权限）、查看我的已购记录
//
// 说明：
// - http.js 会负责 token 注入与分页字段兼容
// - 购买属于资金敏感操作：前端只负责触发，最终扣费/幂等/对账由后端保证

// 视频列表
export function getVideos(params) {
  // 视频列表：
  // - params 常见包含分页 + 筛选：category、keyword、sort、是否免费等（以页面实际传参为准）
  return http.get('/api/videos', { params })
}

// 视频详情
export function getVideoDetail(id) {
  // 视频详情：
  // - 若存在付费逻辑，后端通常会在返回里带上“是否已购买/是否可播放/试看地址”等信息
  return http.get(`/api/videos/${id}`)
}

// 视频分类（去重）
export function getVideoCategories() {
  return http.get('/api/videos/categories')
}

// 购买视频
export function purchaseVideo(id) {
  // 购买/解锁视频：
  // - 可能会从钱包扣费或生成交易流水
  // - 后端一般会做幂等保护（重复点击不会重复扣费），并校验余额/价格/视频状态
  return http.post(`/api/videos/${id}/purchase`)
}

// 已购视频列表
export function getMyVideoPurchases(params) {
  // 已购列表：
  // - 需要登录（token）
  // - params 通常包含分页与时间范围筛选
  return http.get('/api/video-purchases', { params })
}
