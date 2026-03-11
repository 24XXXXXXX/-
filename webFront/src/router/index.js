import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

// 布局组件
//
// 本项目有 4 类角色端：用户端/教练端/员工端/管理员端。
// 前端通过“不同 Layout + 路由前缀”实现多端共存：
// - 用户端：/（默认）
// - 教练端：/coach
// - 员工端：/staff
// - 管理员端：/admin
const UserLayout = () => import('@/layouts/UserLayout.vue')
const AdminLayout = () => import('@/layouts/AdminLayout.vue')
const CoachLayout = () => import('@/layouts/CoachLayout.vue')
const StaffLayout = () => import('@/layouts/StaffLayout.vue')

const routes = [
  // 认证页面（无布局）
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/Login.vue'),
    // meta.public：表示公开页（无需登录也能访问）
    meta: { public: true }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/auth/Register.vue'),
    meta: { public: true }
  },
  {
    path: '/forgot-password',
    name: 'forgot-password',
    component: () => import('@/views/auth/ForgotPassword.vue'),
    meta: { public: true }
  },

  // 用户端（前台）
  {
    path: '/',
    component: UserLayout,
    children: [
      {
        path: '',
        name: 'home',
        component: () => import('@/views/home/Index.vue'),
        meta: { public: true }
      },
      // 场地
      {
        path: 'venues',
        name: 'venues',
        component: () => import('@/views/venue/List.vue'),
        meta: { public: true }
      },
      {
        path: 'venues/:id',
        name: 'venue-detail',
        component: () => import('@/views/venue/Detail.vue'),
        meta: { public: true }
      },
      // 器材
      {
        path: 'equipment',
        name: 'equipment',
        component: () => import('@/views/equipment/List.vue'),
        meta: { public: true }
      },
      {
        path: 'equipment/:id',
        name: 'equipment-detail',
        component: () => import('@/views/equipment/Detail.vue'),
        meta: { public: true }
      },
      {
        path: 'cart',
        name: 'cart',
        component: () => import('@/views/equipment/Cart.vue')
      },
      {
        path: 'checkout',
        name: 'checkout',
        component: () => import('@/views/equipment/Checkout.vue')
      },
      // 课程
      {
        path: 'courses',
        name: 'courses',
        component: () => import('@/views/course/List.vue'),
        meta: { public: true }
      },
      {
        path: 'courses/:id',
        name: 'course-detail',
        component: () => import('@/views/course/Detail.vue'),
        meta: { public: true }
      },
      // 视频
      {
        path: 'videos',
        name: 'videos',
        component: () => import('@/views/video/List.vue'),
        meta: { public: true }
      },
      {
        path: 'videos/:id',
        name: 'video-detail',
        component: () => import('@/views/video/Detail.vue'),
        meta: { public: true }
      },
      // 公告
      {
        path: 'notices',
        name: 'notices',
        component: () => import('@/views/notice/List.vue'),
        meta: { public: true }
      },
      {
        path: 'notices/:id',
        name: 'notice-detail',
        component: () => import('@/views/notice/Detail.vue'),
        meta: { public: true }
      },
      // 用户中心
      {
        path: 'user',
        children: [
          {
            path: 'profile',
            name: 'user-profile',
            component: () => import('@/views/user/Profile.vue')
          },
          {
            path: 'wallet',
            name: 'user-wallet',
            component: () => import('@/views/user/Wallet.vue')
          },
          {
            path: 'addresses',
            name: 'user-addresses',
            component: () => import('@/views/user/Addresses.vue')
          },
          {
            path: 'bookings',
            name: 'user-bookings',
            component: () => import('@/views/user/VenueBookings.vue')
          },
          {
            path: 'orders',
            name: 'user-orders',
            component: () => import('@/views/user/EquipmentOrders.vue')
          },
          {
            path: 'orders/:id',
            name: 'user-order-detail',
            component: () => import('@/views/user/EquipmentOrderDetail.vue')
          },
          {
            path: 'course-bookings',
            name: 'user-course-bookings',
            component: () => import('@/views/user/CourseBookings.vue')
          },
          {
            path: 'course-bookings/:id',
            name: 'user-course-booking-detail',
            component: () => import('@/views/user/CourseBookingDetail.vue')
          },
          {
            path: 'video-purchases',
            name: 'user-video-purchases',
            component: () => import('@/views/user/VideoPurchases.vue')
          },
          {
            path: 'complaints',
            name: 'user-complaints',
            component: () => import('@/views/user/Complaints.vue')
          },
          {
            path: 'complaints/create',
            name: 'user-complaint-create',
            component: () => import('@/views/user/ComplaintCreate.vue')
          },
          {
            path: 'complaints/:id',
            name: 'user-complaint-detail',
            component: () => import('@/views/user/ComplaintDetail.vue')
          },
          {
            path: 'favorites',
            name: 'user-favorites',
            component: () => import('@/views/user/Favorites.vue')
          },
          {
            path: 'consultations',
            name: 'user-consultations',
            component: () => import('@/views/user/Consultations.vue')
          },
          {
            path: 'messages',
            name: 'user-messages',
            component: () => import('@/views/user/Messages.vue')
          }
        ]
      }
    ]
  },

  // 教练认证申请（在用户布局下）
  {
    path: '/coach/apply',
    component: UserLayout,
    children: [
      {
        path: '',
        name: 'coach-apply',
        component: () => import('@/views/coach/Apply.vue')
      }
    ]
  },

  // 教练端
  {
    path: '/coach',
    component: CoachLayout,
    // meta.requiresCoach：进入教练端需要教练身份（或管理员身份，按守卫逻辑决定）
    meta: { requiresCoach: true },
    children: [
      {
        path: 'courses',
        name: 'coach-courses',
        component: () => import('@/views/coach/Courses.vue')
      },
      {
        path: 'courses/create',
        name: 'coach-course-create',
        component: () => import('@/views/coach/CourseForm.vue')
      },
      {
        path: 'courses/:id/edit',
        name: 'coach-course-edit',
        component: () => import('@/views/coach/CourseForm.vue')
      },
      {
        path: 'bookings',
        name: 'coach-bookings',
        component: () => import('@/views/coach/Bookings.vue')
      },
      {
        path: 'videos',
        name: 'coach-videos',
        component: () => import('@/views/coach/Videos.vue')
      },
      {
        path: 'videos/create',
        name: 'coach-video-create',
        component: () => import('@/views/coach/VideoForm.vue')
      },
      {
        path: 'videos/:id/edit',
        name: 'coach-video-edit',
        component: () => import('@/views/coach/VideoForm.vue')
      },
      {
        path: 'videos/:id/preview',
        name: 'coach-video-preview',
        component: () => import('@/views/coach/VideoPreview.vue')
      },
      {
        path: 'consultations',
        name: 'coach-consultations',
        component: () => import('@/views/coach/Consultations.vue')
      },
      {
        path: 'earnings',
        name: 'coach-earnings',
        component: () => import('@/views/coach/Earnings.vue')
      },
      {
        path: 'withdraw',
        name: 'coach-withdraw',
        component: () => import('@/views/coach/Withdrawals.vue')
      }
    ]
  },

  // 员工端
  {
    path: '/staff',
    component: StaffLayout,
    // meta.requiresStaff：员工端页面需要 STAFF 或 ADMIN
    meta: { requiresStaff: true },
    children: [
      {
        path: 'complaints',
        name: 'staff-complaints',
        component: () => import('@/views/staff/Complaints.vue')
      },
      {
        path: 'complaints/:id',
        name: 'staff-complaint-detail',
        component: () => import('@/views/staff/ComplaintDetail.vue')
      },
      {
        path: 'verify',
        name: 'staff-verify',
        component: () => import('@/views/staff/Verify.vue')
      },
      {
        path: 'inspections',
        name: 'staff-inspections',
        component: () => import('@/views/staff/Inspections.vue')
      },
      {
        path: 'inspections/create',
        name: 'staff-inspection-create',
        component: () => import('@/views/staff/InspectionForm.vue')
      },
      {
        path: 'daily-report',
        name: 'staff-daily-report',
        component: () => import('@/views/staff/DailyReport.vue')
      }
    ]
  },

  // 管理员端
  {
    path: '/admin',
    component: AdminLayout,
    // meta.requiresAdmin：管理员端页面需要 ADMIN
    meta: { requiresAdmin: true },
    children: [
      {
        path: 'dashboard',
        name: 'admin-dashboard',
        component: () => import('@/views/admin/Dashboard.vue')
      },
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('@/views/admin/Users.vue')
      },
      {
        path: 'users/create',
        name: 'admin-user-create',
        component: () => import('@/views/admin/UserForm.vue')
      },
      {
        path: 'users/:id/edit',
        name: 'admin-user-edit',
        component: () => import('@/views/admin/UserForm.vue')
      },
      {
        path: 'coaches',
        name: 'admin-coaches',
        component: () => import('@/views/admin/CoachApplications.vue')
      },
      {
        path: 'venues',
        name: 'admin-venues',
        component: () => import('@/views/admin/Venues.vue')
      },
      {
        path: 'venues/create',
        name: 'admin-venue-create',
        component: () => import('@/views/admin/VenueForm.vue')
      },
      {
        path: 'venues/:id/edit',
        name: 'admin-venue-edit',
        component: () => import('@/views/admin/VenueForm.vue')
      },
      {
        path: 'equipment',
        name: 'admin-equipment',
        component: () => import('@/views/admin/Equipment.vue')
      },
      {
        path: 'equipment/create',
        name: 'admin-equipment-create',
        component: () => import('@/views/admin/EquipmentForm.vue')
      },
      {
        path: 'equipment/:id/edit',
        name: 'admin-equipment-edit',
        component: () => import('@/views/admin/EquipmentForm.vue')
      },
      {
        path: 'orders',
        name: 'admin-orders',
        component: () => import('@/views/admin/Orders.vue')
      },
      {
        path: 'inspections',
        name: 'admin-inspections',
        component: () => import('@/views/admin/Inspections.vue')
      },
      {
        path: 'topups',
        name: 'admin-topups',
        component: () => import('@/views/admin/Topups.vue')
      },
      {
        path: 'withdrawals',
        name: 'admin-withdrawals',
        component: () => import('@/views/admin/Withdrawals.vue')
      },
      {
        path: 'complaints',
        name: 'admin-complaints',
        component: () => import('@/views/admin/Complaints.vue')
      },
      {
        path: 'notices',
        name: 'admin-notices',
        component: () => import('@/views/admin/Notices.vue')
      },
      {
        path: 'notices/create',
        name: 'admin-notice-create',
        component: () => import('@/views/admin/NoticeForm.vue')
      },
      {
        path: 'notices/:id/edit',
        name: 'admin-notice-edit',
        component: () => import('@/views/admin/NoticeForm.vue')
      },
      {
        path: 'banners',
        name: 'admin-banners',
        component: () => import('@/views/admin/Banners.vue')
      },
      {
        path: 'banners/create',
        name: 'admin-banner-create',
        component: () => import('@/views/admin/BannerForm.vue')
      },
      {
        path: 'banners/:id/edit',
        name: 'admin-banner-edit',
        component: () => import('@/views/admin/BannerForm.vue')
      },
      {
        path: 'settings',
        name: 'admin-settings',
        component: () => import('@/views/admin/Settings.vue')
      }
    ]
  },

  // 404
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFound.vue'),
    meta: { public: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
//
// 这是前端“权限控制”的第一道门：
// - 决定未登录用户能访问哪些页面
// - 决定不同角色（ADMIN/STAFF/COACH/USER）能进入哪些端
//
// 注意：
// - 前端守卫只能提升体验（提示、跳转），不能替代后端鉴权
// - 真正的安全边界必须依赖后端 Spring Security
router.beforeEach((to, from, next) => {
  const auth = useAuthStore()

  // 公开页面
  if (to.meta?.public) {
    // 已登录用户访问登录/注册页面，重定向到首页
    if ((to.path === '/login' || to.path === '/register') && auth.isLoggedIn) {
      return next({ path: '/' })
    }
    return next()
  }

  // 需要登录
  // 非公开页面默认都要求登录：如果没有 accessToken，则跳转登录并携带 redirect。
  //
  // redirect 的意义：
  // - 用户完成登录后，Login.vue 可以读取 query.redirect 并跳回原目标页
  // - 这样用户体验更好（不会登录后丢失原本要去的页面）
  if (!auth.isLoggedIn) {
    return next({ path: '/login', query: { redirect: to.fullPath } })
  }

  // 需要教练权限
  // 进入教练端：如果不是教练，则提示并引导到教练认证申请页。
  //
  // 这里的设计取舍：
  // - 当前逻辑只允许 COACH 进入教练端（即使 ADMIN 也不会放行）
  // - 这样做可以避免“管理员误入教练端页面导致操作口径混乱”
  // - 如果你希望管理员也能进入教练端，只需要把判断改为：!auth.isCoach && !auth.isAdmin
  if (to.meta?.requiresCoach && !auth.isCoach) {
    window.$message?.warning('需要教练权限，可先提交认证申请或等待审核')
    return next({ path: '/coach/apply' })
  }

  // 需要员工权限
  // 进入员工端：允许 STAFF，也允许 ADMIN（管理员一般具备更高权限）。
  if (to.meta?.requiresStaff && !auth.isStaff && !auth.isAdmin) {
    window.$message?.error('需要员工权限')
    return next({ path: '/' })
  }

  // 需要管理员权限
  if (to.meta?.requiresAdmin && !auth.isAdmin) {
    window.$message?.error('需要管理员权限')
    return next({ path: '/' })
  }

  next()
})

export default router
