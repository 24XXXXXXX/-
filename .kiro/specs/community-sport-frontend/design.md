# Design Document: Community Sport Frontend

## Overview

本设计文档描述社区运动场地管理系统前端的技术架构和实现方案。前端采用 Vue 3 + Naive UI + Pinia + Vue Router 技术栈，实现用户端（前台）和管理端（后台）两套界面，与已完成的 Spring Boot 后端 API 对接。

## Architecture

### 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      Vue 3 Application                       │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │   Views     │  │  Components │  │      Layouts        │  │
│  │  (Pages)    │  │  (Reusable) │  │  (User/Admin/Coach) │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │   Router    │  │    Pinia    │  │    Composables      │  │
│  │  (Routes)   │  │   (Stores)  │  │    (Hooks)          │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────┐│
│  │                    API Layer (Axios)                    ││
│  │  - Request/Response Interceptors                        ││
│  │  - Token Management                                     ││
│  │  - Error Handling                                       ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   Spring Boot Backend API                    │
│                   (http://localhost:8080)                    │
└─────────────────────────────────────────────────────────────┘
```

### 路由架构

```
/                           # 用户端首页
├── /login                  # 登录页
├── /register               # 注册页
├── /forgot-password        # 找回密码
├── /venues                 # 场地列表
│   └── /:id                # 场地详情
├── /equipment              # 器材列表
│   └── /:id                # 器材详情
├── /cart                   # 购物车
├── /courses                # 课程列表
│   └── /:id                # 课程详情
├── /videos                 # 视频列表
│   └── /:id                # 视频详情
├── /notices                # 公告列表
│   └── /:id                # 公告详情
├── /user                   # 用户中心
│   ├── /profile            # 个人资料
│   ├── /wallet             # 钱包
│   ├── /addresses          # 地址管理
│   ├── /bookings           # 场地预约记录
│   ├── /orders             # 器材订单
│   ├── /course-bookings    # 课程预约
│   ├── /video-purchases    # 已购视频
│   ├── /complaints         # 投诉记录
│   ├── /favorites          # 收藏
│   └── /messages           # 消息中心
├── /coach                  # 教练端
│   ├── /apply              # 申请认证
│   ├── /courses            # 课程管理
│   ├── /bookings           # 预约管理
│   ├── /videos             # 视频管理
│   ├── /consultations      # 咨询管理
│   ├── /earnings           # 收入管理
│   └── /withdraw           # 提现申请
├── /staff                  # 员工端
│   ├── /complaints         # 投诉处理
│   ├── /inspections        # 巡检上报
│   ├── /verify             # 核销
│   └── /daily-report       # 日报
└── /admin                  # 管理员端
    ├── /dashboard          # 数据看板
    ├── /users              # 用户管理
    ├── /coaches            # 教练审核
    ├── /venues             # 场地管理
    ├── /equipment          # 器材管理
    ├── /orders             # 订单管理
    ├── /topups             # 充值审核
    ├── /withdrawals        # 提现审核
    ├── /complaints         # 投诉管理
    ├── /notices            # 公告管理
    ├── /banners            # 轮播图管理
    └── /settings           # 系统配置
```

## Components and Interfaces

### 目录结构

```
src/
├── api/                    # API 接口层
│   ├── http.js             # Axios 配置
│   ├── auth.js             # 认证接口
│   ├── venue.js            # 场地接口
│   ├── equipment.js        # 器材接口
│   ├── course.js           # 课程接口
│   ├── video.js            # 视频接口
│   ├── wallet.js           # 钱包接口
│   ├── user.js             # 用户接口
│   ├── complaint.js        # 投诉接口
│   ├── coach.js            # 教练接口
│   ├── staff.js            # 员工接口
│   └── admin.js            # 管理员接口
├── stores/                 # Pinia 状态管理
│   ├── auth.js             # 认证状态
│   ├── cart.js             # 购物车状态
│   └── user.js             # 用户信息状态
├── router/                 # 路由配置
│   └── index.js
├── layouts/                # 布局组件
│   ├── UserLayout.vue      # 用户端布局
│   ├── AdminLayout.vue     # 管理端布局
│   └── CoachLayout.vue     # 教练端布局
├── views/                  # 页面组件
│   ├── auth/               # 认证页面
│   ├── home/               # 首页
│   ├── venue/              # 场地页面
│   ├── equipment/          # 器材页面
│   ├── course/             # 课程页面
│   ├── video/              # 视频页面
│   ├── user/               # 用户中心
│   ├── coach/              # 教练端
│   ├── staff/              # 员工端
│   └── admin/              # 管理端
├── components/             # 公共组件
│   ├── common/             # 通用组件
│   └── business/           # 业务组件
└── composables/            # 组合式函数
    ├── useAuth.js
    ├── usePagination.js
    └── useUpload.js
```

### API 接口设计

```javascript
// api/venue.js
export const venueApi = {
  // 获取场地类型
  getTypes: () => http.get('/api/venue/types'),
  // 获取场地列表
  getList: (params) => http.get('/api/venues', { params }),
  // 获取场地详情
  getDetail: (id) => http.get(`/api/venues/${id}`),
  // 获取时段
  getTimeslots: (venueId, params) => http.get(`/api/venues/${venueId}/timeslots`, { params }),
  // 创建预约
  createBooking: (data) => http.post('/api/bookings', data),
  // 取消预约
  cancelBooking: (id) => http.post(`/api/bookings/${id}/cancel`),
  // 获取预约列表
  getBookings: (params) => http.get('/api/bookings', { params }),
  // 获取预约详情
  getBookingDetail: (id) => http.get(`/api/bookings/${id}`),
}

// api/equipment.js
export const equipmentApi = {
  // 获取分类
  getCategories: () => http.get('/api/equipment/categories'),
  // 获取列表
  getList: (params) => http.get('/api/equipments', { params }),
  // 获取详情
  getDetail: (id) => http.get(`/api/equipments/${id}`),
  // 获取评价
  getReviews: (id, params) => http.get(`/api/equipments/${id}/reviews`, { params }),
  // 获取购物车
  getCart: () => http.get('/api/equipment/cart'),
  // 更新购物车
  updateCart: (data) => http.post('/api/equipment/cart', data),
  // 创建订单
  createOrder: (data) => http.post('/api/equipment/orders', data),
  // 获取订单列表
  getOrders: (params) => http.get('/api/equipment/orders', { params }),
  // 获取订单详情
  getOrderDetail: (id) => http.get(`/api/equipment/orders/${id}`),
  // 确认收货
  confirmReceive: (id) => http.post(`/api/equipment/orders/${id}/receive`),
  // 提交评价
  submitReview: (data) => http.post('/api/equipment-reviews', data),
}

// api/course.js
export const courseApi = {
  // 获取课程列表
  getList: (params) => http.get('/api/courses', { params }),
  // 获取课程详情
  getDetail: (id) => http.get(`/api/courses/${id}`),
  // 获取课程场次
  getSessions: (courseId) => http.get(`/api/courses/${courseId}/sessions`),
  // 获取课程评价
  getReviews: (courseId, params) => http.get(`/api/courses/${courseId}/reviews`, { params }),
  // 创建预约
  createBooking: (data) => http.post('/api/course-bookings', data),
  // 支付预约
  payBooking: (id) => http.post(`/api/course-bookings/${id}/pay`),
  // 取消预约
  cancelBooking: (id) => http.post(`/api/course-bookings/${id}/cancel`),
  // 获取预约列表
  getBookings: (params) => http.get('/api/course-bookings', { params }),
  // 获取预约详情
  getBookingDetail: (id) => http.get(`/api/course-bookings/${id}`),
  // 提交评价
  submitReview: (data) => http.post('/api/course-reviews', data),
}

// api/wallet.js
export const walletApi = {
  // 获取余额
  getBalance: () => http.get('/api/wallet/balance'),
  // 获取流水
  getTransactions: (params) => http.get('/api/wallet/transactions', { params }),
  // 提交充值申请
  submitTopup: (data) => http.post('/api/wallet/topups', data),
  // 获取充值申请列表
  getTopups: (params) => http.get('/api/wallet/topups', { params }),
  // 签到
  signin: () => http.post('/api/signin'),
  // 获取签到状态
  getSigninStatus: () => http.get('/api/signin/status'),
}
```

### Store 设计

```javascript
// stores/auth.js - 已存在，需扩展
export const useAuthStore = defineStore('auth', {
  state: () => ({
    userId: null,
    username: null,
    nickname: null,
    avatarUrl: null,
    roles: [],
    accessToken: '',
    refreshToken: '',
  }),
  getters: {
    isLoggedIn: (s) => !!s.accessToken,
    isAdmin: (s) => s.roles.includes('ADMIN'),
    isStaff: (s) => s.roles.includes('STAFF'),
    isCoach: (s) => s.roles.includes('COACH'),
    isUser: (s) => s.roles.includes('USER'),
  },
  actions: {
    setTokenPair(resp) { /* ... */ },
    async login(credentials) { /* ... */ },
    logout() { /* ... */ },
    async fetchUserInfo() { /* ... */ },
  },
  persist: true,
})

// stores/cart.js
export const useCartStore = defineStore('cart', {
  state: () => ({
    items: [],
    loading: false,
  }),
  getters: {
    totalCount: (s) => s.items.reduce((sum, item) => sum + item.quantity, 0),
    totalAmount: (s) => s.items.reduce((sum, item) => sum + item.price * item.quantity, 0),
  },
  actions: {
    async fetchCart() { /* ... */ },
    async addItem(equipmentId, quantity) { /* ... */ },
    async updateItem(equipmentId, quantity) { /* ... */ },
    async removeItem(equipmentId) { /* ... */ },
    clearCart() { /* ... */ },
  },
})
```

## Data Models

### 前端数据模型（TypeScript 类型定义参考）

```typescript
// 用户信息
interface User {
  id: number
  username: string
  nickname: string
  avatarUrl: string
  phone: string
  email: string
  roles: string[]
}

// 场地
interface Venue {
  id: number
  typeId: number
  typeName: string
  name: string
  area: string
  address: string
  spec: string
  openTimeDesc: string
  pricePerHour: number
  contactPhone: string
  coverUrl: string
  description: string
  status: 'ACTIVE' | 'MAINTENANCE' | 'DISABLED'
}

// 时段
interface Timeslot {
  id: number
  venueId: number
  startTime: string
  endTime: string
  price: number
  status: 'AVAILABLE' | 'BOOKED' | 'BLOCKED'
}

// 场地预约
interface VenueBooking {
  id: number
  bookingNo: string
  venueId: number
  venueName: string
  timeslotId: number
  startTime: string
  endTime: string
  amount: number
  status: 'CREATED' | 'PAID' | 'CANCELED' | 'REFUNDED' | 'USED'
  verificationCode: string
  createdAt: string
}

// 器材
interface Equipment {
  id: number
  categoryId: number
  categoryName: string
  name: string
  spec: string
  purpose: string
  price: number
  stock: number
  coverUrl: string
  description: string
  status: 'ON_SALE' | 'OFF_SALE'
}

// 购物车项
interface CartItem {
  id: number
  equipmentId: number
  equipmentName: string
  coverUrl: string
  price: number
  stock: number
  quantity: number
}

// 器材订单
interface EquipmentOrder {
  id: number
  orderNo: string
  totalAmount: number
  status: 'CREATED' | 'PAID' | 'SHIPPED' | 'RECEIVED' | 'CANCELED' | 'REFUNDED'
  receiverName: string
  receiverPhone: string
  receiverAddress: string
  logisticsCompany: string
  trackingNo: string
  items: OrderItem[]
  createdAt: string
}

// 课程
interface Course {
  id: number
  coachUserId: number
  coachName: string
  coachAvatar: string
  title: string
  category: string
  durationMinutes: number
  price: number
  venueId: number
  venueName: string
  capacity: number
  outline: string
  status: 'ON_SALE' | 'OFF_SALE'
}

// 课程场次
interface CourseSession {
  id: number
  courseId: number
  startTime: string
  endTime: string
  capacity: number
  enrolledCount: number
  status: 'OPEN' | 'CLOSED' | 'CANCELED'
}

// 课程预约
interface CourseBooking {
  id: number
  bookingNo: string
  courseSessionId: number
  courseName: string
  coachName: string
  sessionTime: string
  amount: number
  status: 'PENDING_COACH' | 'ACCEPTED' | 'REJECTED' | 'PAID' | 'CANCELED' | 'REFUNDED' | 'USED'
  verificationCode: string
  rejectReason: string
  createdAt: string
}

// 钱包
interface Wallet {
  balance: number
}

// 钱包流水
interface WalletTransaction {
  id: number
  txnNo: string
  txnType: string
  direction: 'IN' | 'OUT'
  amount: number
  remark: string
  createdAt: string
}

// 投诉
interface Complaint {
  id: number
  complaintNo: string
  complaintType: 'VENUE' | 'EQUIPMENT' | 'COURSE' | 'OTHER'
  content: string
  attachments: string[]
  status: 'SUBMITTED' | 'ASSIGNED' | 'IN_PROGRESS' | 'RESOLVED'
  createdAt: string
}

// 收藏
interface Favorite {
  id: number
  targetType: 'VENUE' | 'COACH' | 'COURSE' | 'EQUIPMENT' | 'VIDEO'
  targetId: number
  targetName: string
  targetCover: string
  createdAt: string
}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Auth State Management
*For any* valid login credentials, after successful login the auth store should contain valid tokens and user info, and after logout the store should be cleared.
**Validates: Requirements 1.2, 1.5, 1.6, 1.7**

### Property 2: Navigation Guard Consistency
*For any* protected route, if the user is not authenticated, they should be redirected to the login page; if authenticated, they should access the route based on their roles.
**Validates: Requirements 1.8**

### Property 3: Venue Filter Consistency
*For any* venue type filter selection, all displayed venues should match the selected type.
**Validates: Requirements 3.2**

### Property 4: Timeslot Date Consistency
*For any* date selection on venue detail page, displayed timeslots should belong to that date.
**Validates: Requirements 3.4**

### Property 5: Booking Flow Integrity
*For any* successful venue booking, the wallet balance should decrease by the booking amount, and a booking record with verification code should be created.
**Validates: Requirements 3.5, 3.7**

### Property 6: Cart Total Calculation
*For any* cart state, the total amount should equal the sum of (price × quantity) for all items.
**Validates: Requirements 4.4**

### Property 7: Order Creation Integrity
*For any* successful order creation, the wallet balance should decrease by the order total, and cart should be cleared.
**Validates: Requirements 4.6**

### Property 8: Course Booking State Transitions
*For any* course booking, the status should follow valid transitions: PENDING_COACH → ACCEPTED/REJECTED → PAID → USED.
**Validates: Requirements 5.4, 5.5**

### Property 9: Complaint Message Ordering
*For any* complaint detail view, messages should be displayed in chronological order.
**Validates: Requirements 6.4, 6.5**

### Property 10: Profile Update Persistence
*For any* profile update, the saved data should match the submitted data when retrieved.
**Validates: Requirements 7.2, 7.3, 7.4**

## Error Handling

### API 错误处理

```javascript
// 统一错误处理
http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error?.response?.status
    const message = error?.response?.data?.message || '请求失败'
    
    switch (status) {
      case 400:
        // 参数错误
        window.$message?.error(message)
        break
      case 401:
        // 未授权，尝试刷新 token
        // 已在 http.js 中处理
        break
      case 403:
        // 无权限
        window.$message?.error('无权限访问')
        break
      case 404:
        // 资源不存在
        window.$message?.error('资源不存在')
        break
      case 500:
        // 服务器错误
        window.$message?.error('服务器错误，请稍后重试')
        break
      default:
        window.$message?.error(message)
    }
    
    return Promise.reject(error)
  }
)
```

### 表单验证

```javascript
// 使用 Naive UI 的表单验证
const rules = {
  username: {
    required: true,
    message: '请输入用户名',
    trigger: 'blur',
  },
  password: {
    required: true,
    message: '请输入密码',
    trigger: 'blur',
  },
  email: {
    type: 'email',
    message: '请输入有效的邮箱地址',
    trigger: 'blur',
  },
}
```

## Testing Strategy

### 测试框架

- 单元测试：Vitest
- 组件测试：Vue Test Utils + Vitest
- E2E 测试：Playwright（可选）

### 测试覆盖

1. **Store 测试**：验证状态管理逻辑
2. **API 测试**：验证接口调用和响应处理
3. **组件测试**：验证组件渲染和交互
4. **路由测试**：验证导航守卫和权限控制

### 属性测试配置

- 使用 fast-check 进行属性测试
- 每个属性测试至少运行 100 次迭代
- 测试标注格式：`**Feature: community-sport-frontend, Property N: {property_text}**`
