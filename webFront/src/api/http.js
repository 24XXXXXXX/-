import axios from 'axios'
import { setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'

// Axios 实例：项目所有前端 API 请求都从这里发出。
//
// 为什么要单独封装一个 http 实例？
// 1）统一设置超时、baseURL 等公共配置
// 2）集中处理请求拦截：自动携带 AccessToken
// 3）集中处理响应拦截：分页数据格式兼容、401 自动刷新 Token
export const http = axios.create({
  baseURL: '',
  timeout: 15000,
})

function normalizePageResponseData(data) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    return
  }

  // 分页返回值“格式兼容层”。
  //
  // 现实情况：
  // - 不同后端分页实现（MyBatis-Plus / Spring Data / 自定义）返回字段不一致
  // - 前端如果每个页面都去适配字段，会非常痛苦
  //
  // 本项目后端主口径是：{ page, size, total, items }
  // 这里把 items 同步映射成 records/content/list/rows 等常见字段，
  // 让旧页面/不同分页组件也能无改动使用。
  //
  // 注意：这里只做“字段映射/别名补齐”，不做数据裁剪/排序等业务处理。
  // 这样既能兼容不同返回格式，又不会改变真实数据语义。

  // backend (this project): { page, size, total, items }
  if (Array.isArray(data.items) && typeof data.total === 'number') {
    if (data.records === undefined) data.records = data.items
    if (data.content === undefined) data.content = data.items
    if (data.list === undefined) data.list = data.items
    if (data.rows === undefined) data.rows = data.items
    if (data.totalElements === undefined) data.totalElements = data.total
    return
  }

  // mybatis-plus style: { records, total, ... }
  if (Array.isArray(data.records) && typeof data.total === 'number') {
    if (data.items === undefined) data.items = data.records
    if (data.content === undefined) data.content = data.records
    if (data.list === undefined) data.list = data.records
    if (data.rows === undefined) data.rows = data.records
    if (data.totalElements === undefined) data.totalElements = data.total
    return
  }

  // spring-data style: { content, totalElements, ... }
  if (Array.isArray(data.content) && typeof data.totalElements === 'number') {
    if (data.items === undefined) data.items = data.content
    if (data.records === undefined) data.records = data.content
    if (data.list === undefined) data.list = data.content
    if (data.rows === undefined) data.rows = data.content
    if (data.total === undefined) data.total = data.totalElements
    return
  }

  // legacy/other: { list, total } or { rows, total }
  if (Array.isArray(data.list) && typeof data.total === 'number') {
    if (data.items === undefined) data.items = data.list
    if (data.records === undefined) data.records = data.list
    if (data.content === undefined) data.content = data.list
    if (data.rows === undefined) data.rows = data.list
    if (data.totalElements === undefined) data.totalElements = data.total
    return
  }

  if (Array.isArray(data.rows) && typeof data.total === 'number') {
    if (data.items === undefined) data.items = data.rows
    if (data.records === undefined) data.records = data.rows
    if (data.content === undefined) data.content = data.rows
    if (data.list === undefined) data.list = data.rows
    if (data.totalElements === undefined) data.totalElements = data.total
  }
}

function getOrCreateDeviceId() {
  // 设备标识：用于 refreshToken 刷新时绑定“设备维度”的会话。
  //
  // 背景：
  // - 如果只靠 refreshToken 字符串做刷新，有时难以区分“同一账号不同设备”的会话
  // - deviceId 可以作为附加信息，后端可用它做更细粒度的会话管理（踢下线、注销某设备等）
  //
  // 注意：这里使用 localStorage 持久化，浏览器不清缓存则 deviceId 不变。
  const key = 'deviceId'
  let deviceId = localStorage.getItem(key)
  if (!deviceId) {
    deviceId = `${Date.now()}_${Math.random().toString(16).slice(2)}`
    localStorage.setItem(key, deviceId)
  }
  return deviceId
}

export function setupHttp(pinia) {
  // 让 useAuthStore() 在非组件环境（纯 js 文件）也能访问到 pinia。
  setActivePinia(pinia)

  http.interceptors.request.use((config) => {
    const auth = useAuthStore()
    // 统一注入 AccessToken：后端 Spring Security 会从 Authorization 解析 JWT。
    if (auth.accessToken) {
      config.headers = config.headers || {}
      config.headers.Authorization = `Bearer ${auth.accessToken}`
    }

    // Normalize common query param names.
    // backend uses: page/size
    const params = config?.params
    if (params && typeof params === 'object' && !Array.isArray(params)) {
      // 一些页面可能传 pageSize/pageNum（历史写法），这里统一转换成后端口径 page/size。
      if (params.size === undefined && params.pageSize !== undefined) {
        params.size = params.pageSize
        delete params.pageSize
      }
      if (params.page === undefined && params.pageNum !== undefined) {
        params.page = params.pageNum
        delete params.pageNum
      }
    }

    return config
  })

  http.interceptors.response.use(
    (resp) => {
      // 对分页响应做一次“字段兼容映射”，避免页面层重复适配。
      normalizePageResponseData(resp?.data)
      return resp
    },
    async (error) => {
      const auth = useAuthStore()
      const original = error?.config
      const status = error?.response?.status

      // 401 处理（AccessToken 过期/无效）：尝试用 RefreshToken 换新 Token，然后重放原请求。
      //
      // 关键细节：
      // - original._retry：避免 refresh 失败时死循环
      // - 必须在刷新成功后更新 store 中的 token，再重放请求
      // - 这里使用 axios.post('/api/auth/refresh') 而不是 http.post：
      //   因为 refresh 本身可能也会触发 http 的拦截器逻辑，
      //   直接用 axios 可以避免“刷新请求再次进入 401 分支”的递归/循环风险。
      if (status === 401 && original && !original._retry && auth.refreshToken) {
        original._retry = true
        try {
          const deviceId = getOrCreateDeviceId()
          const r = await axios.post('/api/auth/refresh', {
            refreshToken: auth.refreshToken,
            deviceId,
          })
          const data = r?.data
          if (data?.accessToken && data?.refreshToken) {
            // 保存新的 token 对（AccessToken + RefreshToken）
            auth.setTokenPair(data)
            original.headers = original.headers || {}
            original.headers.Authorization = `Bearer ${auth.accessToken}`
            // 重放原请求：对用户来说几乎无感知
            return http(original)
          }
        } catch (e) {
        }
        // 刷新失败：认为登录态已失效，清空并回到未登录状态。
        auth.logout()
      }

      return Promise.reject(error)
    }
  )
}
