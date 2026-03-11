// API 统一导出（Barrel 文件）。
//
// 作用：
// - 把分散在各业务模块里的 api 方法集中导出，页面层可以按需从 '@/api' 引用
// - 统一 API 目录结构：auth/venue/equipment/course/... 与后端模块大致对齐
// - 便于维护：新增模块时只需新增一个文件并在这里 export
//
// 约定：
// - 大部分 API 调用使用 http.js 中封装的 axios 实例（http），以便自动携带 token、处理 401 刷新、
//   以及对分页字段做兼容映射（items/records/content/list/rows 等）。
export * from './auth'
export * from './venue'
export * from './equipment'
export * from './course'
export * from './coach'
export * from './video'
export * from './wallet'
export * from './user'
export * from './complaint'
export * from './staff'
export * from './admin'
export * from './home'
export * from './favorite'
export * from './upload'
