FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B clean test \
 && mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl \
 && addgroup -S -g 10001 blog \
 && adduser -S -D -H -u 10001 -G blog blog \
 && mkdir -p /app/data/media \
 && chown -R blog:blog /app
WORKDIR /app
ENV TZ=Asia/Shanghai JAVA_OPTS="-XX:MaxRAMPercentage=75.0" BLOG_MEDIA_DIR=/app/data/media
COPY --from=build --chown=blog:blog /workspace/target/blog-backend-*.jar app.jar
USER 10001:10001
EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=6 \
  CMD curl --fail --silent http://127.0.0.1:8081/api/actuator/health/readiness >/dev/null || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
