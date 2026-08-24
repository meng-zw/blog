FROM node:22-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npm run test:run && npm run typecheck && npm run build

FROM nginxinc/nginx-unprivileged:stable-alpine
ENV BACKEND_HOST=api \
    BACKEND_PORT=8081 \
    MEDIA_UPLOAD_ORIGIN= \
    MEDIA_PUBLIC_ORIGINS= \
    NGINX_ENVSUBST_FILTER="^(BACKEND_HOST|BACKEND_PORT|MEDIA_UPLOAD_ORIGIN|MEDIA_PUBLIC_ORIGINS)$"
COPY --from=build --chown=101:101 /workspace/dist /usr/share/nginx/html
COPY --chown=101:101 nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --chown=101:101 nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --chmod=755 nginx/validate-media-csp.sh /docker-entrypoint.d/19-validate-media-csp.sh
RUN envsubst '${BACKEND_HOST} ${BACKEND_PORT} ${MEDIA_UPLOAD_ORIGIN} ${MEDIA_PUBLIC_ORIGINS}' < /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf \
 && nginx -t \
 && rm /etc/nginx/conf.d/default.conf
USER 101:101
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=6 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
