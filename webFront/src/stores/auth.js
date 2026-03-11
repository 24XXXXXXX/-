import { defineStore } from 'pinia'
import { loginApi, refreshApi } from '@/api/auth'
import { getMe } from '@/api/user'

function normalizeAssetUrl(url) {
  // 统一处理后端返回的资源路径（头像/封面等）。
  //
  // 为什么要 normalize？
  // - 后端可能返回："/upload/xxx.png"（带前导 /）
  // - 也可能返回："upload/xxx.png"（不带 /）
  // - 也可能是完整 URL：http(s)://...
  //
  // 前端在渲染 <img :src> 时需要尽量保证可用，因此在 store 层统一规范化。
  if (url === undefined || url === null) return url
  const s = String(url).trim()
  if (!s) return null
  if (s.startsWith('http://') || s.startsWith('https://') || s.startsWith('data:') || s.startsWith('blob:')) {
    return s
  }
  if (s.startsWith('/')) return s
  return `/${s}`
}

// 认证状态（Auth Store）：整个前端“登录态/角色/Token”的单一数据源。
//
// 这个 store 的定位：
// - 保存当前登录用户的基础信息（id/username/nickname/roles...）
// - 保存 accessToken / refreshToken，并提供刷新逻辑
// - 给路由守卫、页面组件、axios 拦截器提供“是否登录/是否有角色权限”的判断
export const useAuthStore = defineStore('auth', {
  state: () => ({
    // userId/username 等用于展示与业务请求（例如创建订单、上报巡检等需要关联用户）。
    userId: null,
    username: null,
    nickname: null,
    avatarUrl: null,
    phone: null,
    email: null,

    // roles：后端返回的角色代码数组，例如 ['USER']、['ADMIN']。
    // 前端路由守卫会基于它做权限限制（注意：UI 限制只是体验层，最终以接口鉴权为准）。
    roles: [],

    // 双 Token：
    // - accessToken：短效，用于访问业务接口（放在 Authorization Header）
    // - refreshToken：长效，用于换取新的 accessToken
    accessToken: '',
    refreshToken: '',

    // expiresInSeconds：accessToken 的过期秒数（用于展示/预判刷新，具体策略看项目实现）。
    expiresInSeconds: 0,
  }),
  getters: {
    // 是否登录：以 accessToken 是否存在作为判断依据。
    // （严格来说还应判断是否过期，但本项目主要依赖 401 + refresh 机制兜底）
    isLoggedIn: (s) => !!s.accessToken,

    // 角色判断：用于路由守卫和 UI 控制。
    isAdmin: (s) => s.roles.includes('ADMIN'),
    isStaff: (s) => s.roles.includes('STAFF'),
    isCoach: (s) => s.roles.includes('COACH'),
    isUser: (s) => s.roles.includes('USER'),
  },
  actions: {
    getOrCreateDeviceId() {
      // deviceId：设备标识（与 http.js 中逻辑一致）。
      // 主要用于 refreshToken 刷新接口：后端可以用 deviceId 做“设备维度的会话控制”。
      const key = 'deviceId'
      let deviceId = localStorage.getItem(key)
      if (!deviceId) {
        deviceId = `${Date.now()}_${Math.random().toString(16).slice(2)}`
        localStorage.setItem(key, deviceId)
      }
      return deviceId
    },
    setTokenPair(resp) {
      // setTokenPair：把后端登录/刷新接口返回的数据写入 store。
      //
      // 为什么叫 Pair？因为本项目是“AccessToken + RefreshToken”双 Token。
      // 登录 / refresh 都会返回一对 token。
      this.userId = resp?.userId ?? this.userId
      this.username = resp?.username ?? this.username
      this.nickname = resp?.nickname ?? this.nickname
      if (resp?.avatarUrl !== undefined) {
        this.avatarUrl = normalizeAssetUrl(resp?.avatarUrl)
      }
      this.roles = Array.isArray(resp?.roles) ? resp.roles : (this.roles || [])
      this.accessToken = resp?.accessToken || ''
      this.refreshToken = resp?.refreshToken || ''
      this.expiresInSeconds = resp?.expiresInSeconds ?? 0
    },
    async login({ username, password, deviceId }) {
      // 登录：调用后端 /api/auth/login
      // 成功后会拿到 tokenPair + 用户信息（userId/roles...），写入 store 并持久化。
      const r = await loginApi({ username, password, deviceId })
      this.setTokenPair(r?.data)
      return r?.data
    },
    async fetchUserInfo() {
      // 拉取当前用户信息：用于页面展示（昵称/头像/联系方式等）。
      //
      // 为什么登录后还要再调一次 /api/me？
      // - login 接口通常只返回“认证所需的最小信息”（token + id/roles）
      // - 用户资料可能会变化，需要以 /api/me 为准
      try {
        const r = await getMe()
        const data = r?.data?.data || r?.data
        if (data) {
          this.nickname = data.nickname
          this.avatarUrl = normalizeAssetUrl(data.avatarUrl)
          this.phone = data.phone
          this.email = data.email

          if (Array.isArray(data.roles)) {
            // 如果后端角色发生变化（例如管理员给用户赋权/撤权），
            // 前端需要更新 roles，并视情况刷新 token（因为 token 的 claims 里也可能包含 roles）。
            const oldRoles = Array.isArray(this.roles) ? this.roles : []
            const oldSet = new Set(oldRoles)
            const newSet = new Set(data.roles)
            const changed = oldSet.size !== newSet.size || [...oldSet].some(x => !newSet.has(x))
            this.roles = data.roles

            if (changed && this.refreshToken) {
              try {
                // 角色变更时尝试 refresh：目的通常是让新的 token 立刻生效。
                await this.refreshTokenPair()
              } catch (e) {
              }
            }
          }
        }
      } catch (e) {
        console.error('Failed to fetch user info:', e)
      }
    },
    async refreshTokenPair() {
      // refreshTokenPair：使用 refreshToken 换取新的 accessToken。
      //
      // 这个动作一般会在两个地方触发：
      // 1）axios 拦截器遇到 401 自动刷新
      // 2）主动刷新（例如 roles 变化、或你未来想做“定时刷新”）
      if (!this.refreshToken) return
      const deviceId = this.getOrCreateDeviceId()
      const r = await refreshApi({ refreshToken: this.refreshToken, deviceId })
      const data = r?.data
      if (data?.accessToken && data?.refreshToken) {
        this.setTokenPair(data)
      }
      return data
    },
    updateUserInfo(data) {
      // updateUserInfo：用于用户在个人中心修改资料后同步 store（避免再拉一次 /api/me）。
      if (data.nickname !== undefined) this.nickname = data.nickname
      if (data.avatarUrl !== undefined) this.avatarUrl = normalizeAssetUrl(data.avatarUrl)
      if (data.phone !== undefined) this.phone = data.phone
      if (data.email !== undefined) this.email = data.email
    },
    logout() {
      // logout：清空本地登录态。
      // 注意：
      // - 前端清空只是“客户端退出”，更强的安全做法是同时调用后端 /api/auth/logout
      //   让 refreshToken 在服务端失效（避免 token 泄露后继续刷新）。
      this.userId = null
      this.username = null
      this.nickname = null
      this.avatarUrl = null
      this.phone = null
      this.email = null
      this.roles = []
      this.accessToken = ''
      this.refreshToken = ''
      this.expiresInSeconds = 0
    },
  },
  persist: {
    // pinia 持久化：刷新页面后仍能保持登录态。
    // 这里使用 localStorage，意味着：
    // - 浏览器关闭再打开仍保留
    // - 需要注意 XSS 风险（不要在页面中引入不可信脚本）
    key: 'auth',
    storage: localStorage,
  },
})
