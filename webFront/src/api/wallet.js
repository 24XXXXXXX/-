import { http } from './http'

// 钱包/积分（签到）相关 API
//
// 覆盖的业务：
// - 钱包：余额查询、交易流水、用户侧提交充值申请（审批在管理端）
// - 签到：用户每日签到获取奖励（是否是余额/积分/优惠券由后端实现决定）
//
// 说明：
// - http.js 会自动携带 token，并在 401 时尝试 refresh + 重放
// - 资金相关操作（充值/审批/退款等）属于敏感链路：前端只发起请求，最终一致性与审计由后端保证

// 获取余额
export function getBalance() {
  // 当前登录用户的钱包余额：
  // - 返回字段单位（元/分）以后端约定为准
  return http.get('/api/wallet/balance')
}

// 获取流水
export function getTransactions(params) {
  // 交易流水：
  // - params 通常包含分页 + 类型筛选（充值/消费/退款/奖励等）+ 时间范围
  // - 流水是对账与审计的重要依据，展示时建议同时显示时间、类型、金额、备注/关联单号
  return http.get('/api/wallet/transactions', { params })
}

// 提交充值申请
export function submitTopup(data) {
  // 提交充值申请：
  // - data 可能包含充值金额、凭证图片/备注等
  // - 该申请通常需要管理员在管理端审批（见 admin.js：/api/admin/wallet/topups/**）
  return http.post('/api/wallet/topups', data)
}

// 我的充值申请列表
export function getMyTopups(params) {
  // 我的充值申请：
  // - params 通常为分页 + 状态筛选（待审/通过/拒绝）
  return http.get('/api/wallet/topups', { params })
}

// 签到
export function signin() {
  // 每日签到：
  // - 通常应由后端保证幂等（同一天重复签到不会重复发放奖励）
  // - 前端可以用 getSigninStatus 先查状态再决定是否展示按钮，但不要依赖前端来防刷
  return http.post('/api/signin')
}

// 获取签到状态
export function getSigninStatus() {
  // 查询今天是否已签到：用于 UI 显示（按钮禁用/展示奖励提示）。
  return http.get('/api/signin/status')
}
