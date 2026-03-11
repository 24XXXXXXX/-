import { http } from './http'

// 通用上传 API
//
// 该模块把“上传文件”抽象成一个统一入口：/api/upload/photo/{category}
// - category 由业务决定，用于让后端把文件归档到不同目录/存储桶前缀（例如 avatar、notice、banner 等）
// - 前端只负责按约定传 category + file，后端负责校验文件类型/大小、鉴权、保存并返回可访问 URL
//
// 说明：
// - 使用 multipart/form-data 传文件（FormData + Content-Type）
// - 返回结构以服务端为准，常见是 { fileName, url } 或 { url }

// 通用图片上传
export function uploadPhoto(category, file) {
  // 这里使用 path 参数拼接 category：
  // - 请确保 category 来自受控枚举/常量，而不是任意用户输入，避免出现意外路径（最终也应由后端兜底校验）
  const formData = new FormData()
  formData.append('file', file)
  return http.post(`/api/upload/photo/${category}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 通用文件上传（别名）
export function uploadFile(category, file) {
  // 兼容命名：某些页面更倾向于叫 uploadFile，即使后端当前统一走 photo 接口。
  return uploadPhoto(category, file)
}
