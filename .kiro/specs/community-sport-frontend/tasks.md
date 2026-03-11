# Implementation Plan: Community Sport Frontend

## Overview

本实现计划将前端开发分为多个阶段，从基础架构搭建开始，逐步实现各功能模块。采用增量开发方式，确保每个阶段都能独立运行和测试。

## Tasks

- [x] 1. 项目基础架构搭建
  - [x] 1.1 安装必要依赖并配置项目
    - 安装 @vicons/ionicons5 图标库
    - 安装 dayjs 日期处理库
    - 安装 unplugin-auto-import 和 unplugin-vue-components（已有）
    - 配置 vite.config.js 路径别名
    - _Requirements: 1.1, 1.2_

  - [x] 1.2 创建 API 接口层
    - 创建 api/venue.js 场地接口
    - 创建 api/equipment.js 器材接口
    - 创建 api/course.js 课程接口
    - 创建 api/video.js 视频接口
    - 创建 api/wallet.js 钱包接口
    - 创建 api/user.js 用户接口
    - 创建 api/complaint.js 投诉接口
    - 创建 api/coach.js 教练接口
    - 创建 api/staff.js 员工接口
    - 创建 api/admin.js 管理员接口
    - 创建 api/home.js 首页接口
    - _Requirements: 1.1-1.9, 2.1-2.6, 3.1-3.9_

  - [x] 1.3 创建布局组件
    - 创建 layouts/UserLayout.vue 用户端布局（顶部导航+内容区）
    - 创建 layouts/AdminLayout.vue 管理端布局（侧边栏+顶栏+内容区）
    - 创建 layouts/CoachLayout.vue 教练端布局
    - 创建 layouts/StaffLayout.vue 员工端布局
    - _Requirements: 1.1, 8.1, 9.1, 10.1_

  - [x] 1.4 配置路由系统
    - 重构 router/index.js 添加完整路由配置
    - 实现路由守卫（认证检查、角色检查）
    - 配置路由懒加载
    - _Requirements: 1.1, 1.7, 1.8_

- [x] 2. 认证模块实现
  - [x] 2.1 实现登录页面
    - 创建 views/auth/Login.vue
    - 实现登录表单（用户名、密码）
    - 实现登录逻辑和错误提示
    - 实现记住登录状态
    - _Requirements: 1.1, 1.2, 1.3_

  - [x] 2.2 实现注册页面
    - 创建 views/auth/Register.vue
    - 实现注册表单（用户名、密码、确认密码、手机号、邮箱）
    - 实现表单验证
    - 实现注册成功跳转
    - _Requirements: 1.4, 1.5_

  - [x] 2.3 实现找回密码页面
    - 创建 views/auth/ForgotPassword.vue
    - 实现邮箱验证码发送
    - 实现密码重置表单
    - _Requirements: 1.9_

  - [x] 2.4 扩展 auth store
    - 添加用户详细信息存储
    - 实现 fetchUserInfo action
    - 实现角色判断 getters
    - _Requirements: 1.2, 1.6, 1.7_

- [x] 3. Checkpoint - 认证模块完成
  - 确保登录、注册、找回密码功能正常
  - 确保路由守卫正常工作
  - 如有问题请告知

- [x] 4. 用户端首页实现
  - [x] 4.1 实现首页
    - 创建 views/home/Index.vue
    - 实现轮播图组件
    - 实现热门场地推荐区块
    - 实现优质课程推荐区块
    - 实现器材优惠区块
    - 实现公告区块
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

- [x] 5. 场地模块实现
  - [x] 5.1 实现场地列表页
    - 创建 views/venue/List.vue
    - 实现场地类型筛选
    - 实现场地卡片展示
    - 实现分页加载
    - _Requirements: 3.1, 3.2_

  - [x] 5.2 实现场地详情页
    - 创建 views/venue/Detail.vue
    - 展示场地基本信息
    - 实现日期选择器
    - 实现时段列表展示
    - 实现预约功能（钱包扣款）
    - 实现收藏功能
    - _Requirements: 3.3, 3.4, 3.5, 3.6, 3.7_

  - [x] 5.3 实现场地预约记录页
    - 创建 views/user/VenueBookings.vue
    - 展示预约列表
    - 实现取消预约功能
    - 展示核销码
    - _Requirements: 3.8, 3.9_

- [x] 6. Checkpoint - 场地模块完成
  - 确保场地浏览、预约、取消功能正常
  - 如有问题请告知

- [x] 7. 器材商城模块实现
  - [x] 7.1 实现器材列表页
    - 创建 views/equipment/List.vue
    - 实现分类筛选
    - 实现搜索功能
    - 实现器材卡片展示
    - _Requirements: 4.1_

  - [x] 7.2 实现器材详情页
    - 创建 views/equipment/Detail.vue
    - 展示器材信息
    - 展示用户评价
    - 实现加入购物车
    - 实现收藏功能
    - _Requirements: 4.2, 4.3_

  - [x] 7.3 实现购物车页面
    - 创建 views/equipment/Cart.vue
    - 创建 stores/cart.js
    - 实现购物车列表
    - 实现数量修改
    - 实现删除商品
    - 实现总价计算
    - _Requirements: 4.4_

  - [x] 7.4 实现订单确认页
    - 创建 views/equipment/Checkout.vue
    - 实现地址选择
    - 实现订单摘要
    - 实现下单功能（钱包扣款）
    - _Requirements: 4.5, 4.6, 4.7_

  - [x] 7.5 实现订单列表页
    - 创建 views/user/EquipmentOrders.vue
    - 展示订单列表
    - 展示订单状态和物流信息
    - 实现确认收货
    - _Requirements: 4.8, 4.9_

  - [x] 7.6 实现订单详情和评价
    - 创建 views/user/EquipmentOrderDetail.vue
    - 展示订单详情
    - 实现评价功能
    - _Requirements: 4.10_

- [x] 8. Checkpoint - 器材商城完成
  - 确保器材浏览、购物车、下单、收货、评价功能正常
  - 如有问题请告知

- [x] 9. 课程模块实现
  - [x] 9.1 实现课程列表页
    - 创建 views/course/List.vue
    - 实现分类筛选
    - 实现课程卡片展示
    - _Requirements: 5.1_

  - [x] 9.2 实现课程详情页
    - 创建 views/course/Detail.vue
    - 展示课程和教练信息
    - 展示课程场次
    - 展示用户评价
    - 实现预约功能
    - 实现收藏功能
    - _Requirements: 5.2, 5.3, 5.4_

  - [x] 9.3 实现课程预约记录页
    - 创建 views/user/CourseBookings.vue
    - 展示预约列表和状态
    - 实现支付功能
    - 实现取消功能
    - 展示核销码
    - _Requirements: 5.5, 5.6, 5.7_

  - [x] 9.4 实现课程评价功能
    - 在预约详情中添加评价入口
    - 实现评价表单
    - _Requirements: 5.8_

- [x] 10. Checkpoint - 课程模块完成
  - 确保课程浏览、预约、支付、取消、评价功能正常
  - 如有问题请告知

- [x] 11. 视频模块实现
  - [x] 11.1 实现视频列表页
    - 创建 views/video/List.vue
    - 实现视频卡片展示
    - 实现筛选功能
    - _Requirements: 11.1_

  - [x] 11.2 实现视频详情页
    - 创建 views/video/Detail.vue
    - 展示视频信息和预览
    - 实现购买功能
    - 实现收藏功能
    - _Requirements: 11.2, 11.3_

  - [x] 11.3 实现已购视频页
    - 创建 views/user/VideoPurchases.vue
    - 展示已购视频列表
    - 实现视频播放
    - _Requirements: 11.4_

- [x] 12. 用户中心实现
  - [x] 12.1 实现个人资料页
    - 创建 views/user/Profile.vue
    - 实现资料编辑
    - 实现头像上传
    - 实现密码修改
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

  - [x] 12.2 实现地址管理页
    - 创建 views/user/Addresses.vue
    - 实现地址列表
    - 实现地址增删改
    - 实现设置默认地址
    - _Requirements: 7.5_

  - [x] 12.3 实现钱包页面
    - 创建 views/user/Wallet.vue
    - 展示余额
    - 展示流水记录
    - 实现充值申请
    - 实现签到功能
    - _Requirements: 7.6, 7.7, 7.8_

  - [x] 12.4 实现收藏页面
    - 创建 views/user/Favorites.vue
    - 按类型展示收藏
    - 实现取消收藏
    - _Requirements: 7.9_

  - [x] 12.5 实现消息中心
    - 创建 views/user/Messages.vue
    - 展示消息列表
    - 实现已读/未读状态
    - 实现全部已读
    - _Requirements: 7.10_

- [x] 13. 投诉模块实现
  - [x] 13.1 实现投诉提交页
    - 创建 views/user/ComplaintCreate.vue
    - 实现投诉类型选择
    - 实现内容填写和附件上传
    - _Requirements: 6.1, 6.2_

  - [x] 13.2 实现投诉列表和详情页
    - 创建 views/user/Complaints.vue
    - 创建 views/user/ComplaintDetail.vue
    - 展示投诉列表和状态
    - 展示处理进度和消息
    - 实现追加消息
    - _Requirements: 6.3, 6.4, 6.5_

- [x] 14. Checkpoint - 用户端完成
  - 确保用户中心所有功能正常
  - 确保投诉功能正常
  - 如有问题请告知

- [x] 15. 教练端实现
  - [x] 15.1 实现教练认证申请页
    - 创建 views/coach/Apply.vue
    - 实现申请表单
    - 实现资质证明上传
    - _Requirements: 8.1_

  - [x] 15.2 实现课程管理页
    - 创建 views/coach/Courses.vue
    - 创建 views/coach/CourseForm.vue
    - 实现课程列表
    - 实现课程创建/编辑
    - 实现场次管理
    - 实现上下架
    - _Requirements: 8.2_

  - [x] 15.3 实现预约管理页
    - 创建 views/coach/Bookings.vue
    - 展示待处理和已确认预约
    - 实现接单/拒单
    - 实现核销功能
    - _Requirements: 8.3, 8.4, 8.5_

  - [x] 15.4 实现视频管理页
    - 创建 views/coach/Videos.vue
    - 创建 views/coach/VideoForm.vue
    - 实现视频上传
    - 实现视频列表管理
    - _Requirements: 11.5, 11.6_

  - [x] 15.5 实现收入和提现页
    - 创建 views/coach/Earnings.vue
    - 创建 views/coach/Withdrawals.vue
    - 展示收入统计
    - 实现提现申请
    - _Requirements: 8.6, 8.7_

  - [x] 15.6 实现咨询管理页
    - 创建 views/coach/Consultations.vue
    - 展示用户咨询
    - 实现回复功能
    - _Requirements: 8.8_

- [x] 16. Checkpoint - 教练端完成
  - 确保教练端所有功能正常
  - 如有问题请告知

- [x] 17. 员工端实现
  - [x] 17.1 实现投诉处理页
    - 创建 views/staff/Complaints.vue
    - 创建 views/staff/ComplaintDetail.vue
    - 展示分配的投诉
    - 实现状态更新
    - 实现消息回复
    - _Requirements: 9.1, 9.2, 9.3_

  - [x] 17.2 实现核销功能页
    - 创建 views/staff/Verify.vue
    - 实现场地预约核销
    - 实现课程预约核销
    - _Requirements: 9.4_

  - [x] 17.3 实现巡检上报页
    - 创建 views/staff/Inspections.vue
    - 创建 views/staff/InspectionForm.vue
    - 实现巡检记录列表
    - 实现上报表单
    - _Requirements: 9.5_

  - [x] 17.4 实现日报页面
    - 创建 views/staff/DailyReport.vue
    - 展示日报统计数据
    - _Requirements: 9.6_

- [x] 18. Checkpoint - 员工端完成
  - 确保员工端所有功能正常
  - 如有问题请告知

- [x] 19. 管理员端实现
  - [x] 19.1 实现数据看板
    - 创建 views/admin/Dashboard.vue
    - 展示核心指标
    - 展示统计图表
    - _Requirements: 10.12_

  - [x] 19.2 实现用户管理
    - 创建 views/admin/Users.vue
    - 创建 views/admin/UserForm.vue
    - 实现用户列表（搜索、筛选）
    - 实现用户创建/编辑
    - 实现状态启用/禁用
    - 实现角色分配
    - _Requirements: 10.1, 10.2, 10.3_

  - [x] 19.3 实现教练审核
    - 创建 views/admin/CoachApplications.vue
    - 展示申请列表
    - 实现审核通过/拒绝
    - _Requirements: 10.4_

  - [x] 19.4 实现场地管理
    - 创建 views/admin/Venues.vue
    - 创建 views/admin/VenueForm.vue
    - 实现场地 CRUD
    - 实现时段生成
    - _Requirements: 10.5_

  - [x] 19.5 实现器材管理
    - 创建 views/admin/Equipment.vue
    - 创建 views/admin/EquipmentForm.vue
    - 实现器材 CRUD
    - 实现库存管理
    - _Requirements: 10.6_

  - [x] 19.6 实现订单管理
    - 创建 views/admin/Orders.vue
    - 展示订单列表
    - 实现发货操作
    - _Requirements: 10.7_

  - [x] 19.7 实现充值审核
    - 创建 views/admin/Topups.vue
    - 展示充值申请列表
    - 实现审核通过/拒绝
    - _Requirements: 10.8_

  - [x] 19.8 实现提现审核
    - 创建 views/admin/Withdrawals.vue
    - 展示提现申请列表
    - 实现审核通过/拒绝
    - _Requirements: 10.9_

  - [x] 19.9 实现投诉管理
    - 创建 views/admin/Complaints.vue
    - 展示投诉列表
    - 实现指派员工
    - _Requirements: 10.1_

  - [x] 19.10 实现公告管理
    - 创建 views/admin/Notices.vue
    - 创建 views/admin/NoticeForm.vue
    - 实现公告 CRUD
    - 实现发布/取消发布
    - _Requirements: 10.10_

  - [x] 19.11 实现轮播图管理
    - 创建 views/admin/Banners.vue
    - 创建 views/admin/BannerForm.vue
    - 实现轮播图 CRUD
    - 实现启用/禁用
    - _Requirements: 10.11_

  - [x] 19.12 实现系统配置
    - 创建 views/admin/Settings.vue
    - 展示配置项
    - 实现配置修改
    - _Requirements: 10.13_

- [x] 20. Checkpoint - 管理员端完成
  - 确保管理员端所有功能正常
  - 如有问题请告知

- [x] 21. 公告模块实现
  - [x] 21.1 实现公告列表和详情页
    - 创建 views/notice/List.vue
    - 创建 views/notice/Detail.vue
    - 展示公告列表
    - 展示公告详情
    - _Requirements: 2.5_

- [x] 22. 最终检查和优化
  - [x] 22.1 UI 优化和响应式适配
    - 检查所有页面的响应式布局
    - 优化移动端体验
    - 统一样式风格

  - [x] 22.2 更新开发完成度文档
    - 更新 communitySport/开发完成度.txt
    - 标记已完成的前端功能

- [x] 23. Final Checkpoint - 项目完成
  - 确保所有功能正常运行
  - 确保与后端 API 正确对接
  - 如有问题请告知

## Notes

- 任务按模块划分，每个模块完成后有检查点
- 优先实现核心用户流程（认证→首页→场地→器材→课程）
- 管理端功能可根据需要调整优先级
- 每个任务引用了对应的需求编号以便追溯
- 所有模块已完成：认证、首页、场地、器材商城、课程、视频、用户中心、投诉、教练端、员工端、公告、管理员端
- 前端开发已全部完成，可进行集成测试
