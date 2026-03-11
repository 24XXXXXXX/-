import axios from 'axios'
import { http } from './http'

// 认证相关 API：
// - login/register/logout：使用 http 实例，便于统一 baseURL/超时设置，且可复用拦截器能力
// - refresh：这里刻意使用 axios（而非 http）直连刷新接口，避免 refresh 请求自己触发 401 拦截器而造成递归
//
// 注意：这些函数只负责发起请求，不处理“登录态写入/刷新 token 存储”等业务。
// 登录态的单一数据源在 stores/auth.js（Auth Store）。

export function loginApi(payload) {
  // 登录：后端返回 tokenPair + 用户基本信息（以接口返回为准）。
  // payload 通常包含 username/password/deviceId。
  return http.post('/api/auth/login', payload)
}

export function registerApi(payload) {
  // 注册：创建新用户账号。
  return http.post('/api/auth/register', payload)
}

export function logoutApi(payload) {
  // 登出：推荐在前端清空本地 token 的同时调用该接口，让后端 refreshToken 失效（更安全）。
  return http.post('/api/auth/logout', payload)
}

export function sendPasswordResetCode(data) {
  // 发送重置密码验证码（邮箱/短信等具体策略由后端实现）。
  return http.post('/api/auth/password-reset/code', data)
}

export function confirmPasswordReset(data) {
  // 确认重置密码：携带验证码 + 新密码。
  return http.post('/api/auth/password-reset/confirm', data)
}

export function refreshApi(payload) {
  // 刷新 Token：用 refreshToken 换取新的 accessToken。
  // 使用 axios 直连，避免被 http.js 的响应拦截器影响。
  return axios.post('/api/auth/refresh', payload)
}
