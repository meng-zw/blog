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

# 利用官方镜像的 templates 机制在启动时生成配置：
# 仅 BACKEND_HOST/BACKEND_PORT 会被替换为环境变量，$uri 等 Nginx 内置变量不受影响
RUN <<'NGINX_CONF' cat > /etc/nginx/templates/default.conf.template
server {
    listen 80;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    gzip on;
    gzip_types text/plain text/css application/json application/javascript image/svg+xml;
    gzip_min_length 1k;

    # 带 hash 的构建产物内容不变，启用长缓存
    location /assets/ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    # Vue Router history 模式：刷新或直接访问子路由时回退到 index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 后端 API 反向代理，对应 vite.config.ts 中 /api -> http://localhost:8081 的开发代理
    location /api/ {
        proxy_pass http://${BACKEND_HOST}:${BACKEND_PORT};
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
NGINX_CONF

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
