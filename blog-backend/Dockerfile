# ---------- 构建阶段：Maven 打包 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# 国内网络环境构建缓慢时，取消下行注释改用阿里云 Maven 镜像
# RUN sed -i 's|https://repo.maven.apache.org/maven2|https://maven.aliyun.com/repository/public|g' /usr/share/maven/conf/settings.xml

# 先单独复制 pom.xml 下载依赖：pom 未变化时命中 Docker 层缓存，避免每次构建重复下载依赖
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# 复制源码并打包（跳过测试以加快镜像构建）
COPY src ./src
RUN mvn clean package -DskipTests

# ---------- 运行阶段：最小化 JRE 镜像 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# 时区与 JVM 参数：按容器内存上限的百分比分配堆内存，避免硬编码 -Xmx
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# 以下环境变量按 Spring Boot 松散绑定规则覆盖 application.yml 中的同名配置，
# 默认值与 application.yml 保持一致，部署时通过 docker run -e 覆盖即可，无需重新打包
# 注意：容器内的 localhost 并非宿主机，连接宿主机上的 MySQL/Redis 请使用 host.docker.internal
ENV SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/blog?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai" \
    SPRING_DATASOURCE_USERNAME=root \
    SPRING_DATASOURCE_PASSWORD=root \
    SPRING_DATA_REDIS_HOST=localhost \
    SPRING_DATA_REDIS_PORT=6379 \
    JWT_SECRET="your-secret-key-must-be-at-least-256-bits-long-for-HS512-algorithm-to-work-properly"

COPY --from=build /app/target/blog-backend-*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
