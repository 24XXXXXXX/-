import { http } from './http'

// 收藏相关 API
//
// 设计要点：
// - 收藏是“用户 -> 目标资源”的关联关系，常见目标：场地/课程/视频/器材等
// - targetType 通常是后端枚举（例如 VENUE/COURSE/VIDEO/EQUIPMENT），大小写/空格不一致会导致后端无法匹配
//   因此在 API 层做一次归一化，避免页面层到处写 toUpperCase
// - http.js 会注入 token；未登录调用这些接口一般会返回 401

const normalizeTargetType = (dataOrParams) => {
  // 兼容页面层不同字段命名：targetType 或 type。
  const raw = dataOrParams?.targetType ?? dataOrParams?.type
  if (!raw) return undefined
  return String(raw).trim().toUpperCase()
}

// 添加收藏
export function addFavorite(data) {
  // 添加收藏：
  // - payload 只保留后端需要的字段，避免把多余 UI 字段一起传过去
  // - 一般期望后端做幂等：重复收藏同一目标不会创建多条记录
  const payload = {
    targetType: normalizeTargetType(data),
    targetId: data?.targetId
  }
  return http.post('/api/favorites', payload)
}

// 取消收藏
export function removeFavorite(data) {
  // 取消收藏：使用 DELETE + query params。
  // 同样通常期望幂等：即使目标未收藏，取消也应返回成功或明确的业务错误（以实现为准）。
  const params = {
    targetType: normalizeTargetType(data),
    targetId: data?.targetId
  }
  return http.delete('/api/favorites', { params })
}

// 收藏列表
export function getFavorites(params) {
  // 收藏列表：
  // - 支持分页与按 targetType 筛选
  // - 这里手动把 size/pageSize 做兼容（与 http.js 的分页兼容层互补）
  const p = {
    page: params?.page,
    size: params?.size ?? params?.pageSize,
    targetType: normalizeTargetType(params)
  }
  return http.get('/api/favorites', { params: p })
}

// 检查收藏状态
export function checkFavoriteStatus(params) {
  // 检查某个目标是否被当前用户收藏：
  // - 常用于详情页渲染“已收藏/收藏”按钮状态
  const p = {
    targetType: normalizeTargetType(params),
    targetId: params?.targetId
  }
  return http.get('/api/favorites/status', { params: p })
}

// 检查收藏状态（别名）
export function checkFavorite(params) {
  return checkFavoriteStatus(params)
}

// 获取收藏数量
export function getFavoriteCount(params) {
  // 获取收藏数量：
  // - 可用于列表页显示某类目标的收藏数，或详情页展示热度（是否公开由后端决定）
  const p = {
    targetType: normalizeTargetType(params),
    targetId: params?.targetId
  }
  return http.get('/api/favorites/count', { params: p })
}
