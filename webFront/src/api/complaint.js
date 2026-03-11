import { http } from './http'

// 投诉相关 API（用户端主流程）
//
// 注意本文件的边界：
// - 这里主要是“用户自己发起/跟进投诉”的接口（/api/complaints/**）
// - 员工/管理员处理投诉（领取、状态更新、处理记录等）通常在 staff.js/admin.js 中对应 /api/staff/** 或 /api/admin/**
//
// 说明：
// - http.js 会自动注入 token，并处理 401 refresh + 重放（用户尽量无感）
// - 投诉是典型“会话式”业务：一条投诉下有多条 messages，状态机推进由后端控制

// 提交投诉
export function createComplaint(data) {
  // 创建投诉：
  // - data 通常包含投诉对象（场地/课程/器材订单等）、标题/描述、证据图片等
  // - 后端会校验数据合法性，并把投诉归属到当前用户
  return http.post('/api/complaints', data)
}

// 我的投诉列表
export function getMyComplaints(params) {
  // 我的投诉列表：
  // - params 常见为分页 + 状态筛选（待处理/处理中/已解决/已关闭等）
  // - http.js 会兼容 page/pageSize 与 current/size 等不同分页字段
  return http.get('/api/complaints', { params })
}

// 投诉详情
export function getComplaintDetail(id) {
  // 投诉详情：
  // - 后端会做资源级校验（只能查看自己的投诉，除非是 staff/admin 端接口）
  // - 返回内容通常包含 messages 列表与当前状态
  return http.get(`/api/complaints/${id}`)
}

// 追加消息
export function addComplaintMessage(id, data) {
  // 追加一条投诉消息：
  // - data 通常包含 message 文本、附件等
  // - 该动作有时会触发后端的状态联动（例如用户追问导致重新打开，或员工首次回复自动“领取”）
  //   具体规则以服务端实现为准
  return http.post(`/api/complaints/${id}/messages`, data)
}
