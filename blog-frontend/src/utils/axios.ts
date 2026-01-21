import axios from 'axios'

const instance = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
instance.interceptors.request.use(
  (config) => {
    // 从localStorage获取token
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
instance.interceptors.response.use(
  (response) => {
    return response.data
  },
  (error) => {
    // 处理错误
    if (error.response) {
      // 服务器返回错误状态码
      switch (error.response.status) {
        case 401:
          // 未授权，跳转到登录页
          window.location.href = '/login'
          break
        case 403:
          // 禁止访问
          console.error('禁止访问')
          break
        case 404:
          // 资源不存在
          console.error('资源不存在')
          break
        case 500:
          // 服务器错误
          console.error('服务器错误')
          break
        default:
          console.error('请求失败:', error.response.data.message)
      }
    } else if (error.request) {
      // 请求已发出，但没有收到响应
      console.error('没有收到响应')
    } else {
      // 请求配置出错
      console.error('请求配置出错:', error.message)
    }
    return Promise.reject(error)
  }
)

export default instance
