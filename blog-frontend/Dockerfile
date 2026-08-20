# ---------- 构建阶段：npm 构建产物 ----------
FROM node:22-alpine AS build
WORKDIR /app

# 国内网络环境构建缓慢时，取消下行注释改用 npmmirror 镜像源
# RUN npm config set registry https://registry.npmmirror.com

# 先单独复制依赖清单安装：package*.json 未变化时命中 Docker 层缓存，避免每次构建重复安装
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---------- 运行阶段：Nginx 托管静态文件 ----------
# Nginx 承担 Vite 开发服务器的两项职责：托管 dist 静态文件 + 将 /api 反向代理到后端
FROM nginx:stable-alpine

# 后端地址，运行时通过 -e 覆盖（如 -e BACKEND_HOST=host.docker.internal 或 compose 网络中的后端服务名）
ENV BACKEND_HOST=localhost \
    BACKEND_PORT=8081

COPY --from=build /app/dist /usr/share/nginx/html

# Nginx 配置模板：容器启动时由官方镜像的 templates 机制(envsubst)生成 /etc/nginx/conf.d/default.conf，
# 仅 BACKEND_HOST/BACKEND_PORT 会被替换为环境变量值，$uri 等 Nginx 内置变量不受影响
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
