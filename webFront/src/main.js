import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import { setupHttp } from './api/http'

// 前端启动入口（Vite + Vue3）：
//
// 这个文件只做“应用装配”，不写具体业务逻辑。
// 初始化顺序通常很关键：
// 1）createApp(App)：创建 Vue 应用实例
// 2）创建并配置 Pinia（状态管理）
// 3）初始化 axios/http（注入 token 拦截器、401 刷新逻辑等）
// 4）注册 pinia/router 插件
// 5）mount('#app')：挂载到 index.html 中的根节点
const app = createApp(App)

const pinia = createPinia()

// pinia 持久化插件：
// - 让 store 支持 persist 配置（例如 auth store 的 token/roles 刷新后仍存在）
// - 底层通常基于 localStorage/sessionStorage
pinia.use(piniaPluginPersistedstate)

// 初始化 http（axios 实例 + 拦截器）：
// - 需要在非组件环境也能访问 pinia（http.js 内部会 setActivePinia）
// - 因此这里传入 pinia，并先 setup 再 app.use(pinia)
//   让后续 import 的 api 函数/拦截器在运行时能正确拿到 store
setupHttp(pinia)

app.use(pinia)
app.use(router)
app.mount('#app')
