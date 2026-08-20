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
    // 统一解析后端返回的具体错误信息，挂载到 error.message 供视图层展示
    if (error.response) {
      const { status, data } = error.response
      let message = ''

      if (typeof data === 'string' && data.trim()) {
        // 后端直接返回字符串的错误信息
        message = data
      } else if (data && typeof data === 'object') {
        // 后端返回 JSON 错误体，兼容多种字段名
        message = data.message || data.error || data.msg || ''
      }

      if (!message) {
        // 后端未携带可读信息时按状态码给出提示
        switch (status) {
          case 400: message = '请求参数错误'; break
          case 401: message = '未登录或登录已过期'; break
          case 403: message = '没有权限执行该操作'; break
          case 404: message = '请求的资源不存在'; break
          case 500: message = '服务器开小差了，请稍后重试'; break
          default: message = `请求失败（${status}）`
        }
      }
      error.message = message

      switch (status) {
        case 401:
          // 未授权，跳转到登录页
          window.location.href = '/login'
          break
        case 403:
          // 未授权或禁止访问，未登录时跳转到登录页
          const token = localStorage.getItem('token')
          if (!token) {
            // 未登录时跳转到登录页
            window.location.href = '/login'
          }
          break
      }
    } else if (error.request) {
      // 请求已发出，但没有收到响应
      error.message = '网络异常，请检查网络连接'
    } else {
      // 请求配置出错
      error.message = error.message || '请求配置出错'
    }
    return Promise.reject(error)
  }
)

export default instance
