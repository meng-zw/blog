# 博客网站项目

## 项目简介

这是一个集博客文章发布和工具分享于一体的综合性平台，采用前后端分离的架构设计。

## 技术栈

### 前端技术栈
- **框架**：Vue 3
- **构建工具**：Vite
- **路由**：Vue Router 4
- **状态管理**：Pinia
- **UI组件库**：Element Plus
- **Markdown编辑器**：Vditor
- **HTTP客户端**：Axios

### 后端技术栈
- **框架**：Spring Boot 4.0.x
- **JDK版本**：JDK 25
- **ORM框架**：MyBatis-Plus
- **数据库**：MySQL 8.0+
- **缓存**：Redis
- **认证授权**：JWT + Spring Security
- **构建工具**：Maven

## 项目结构

```
blog-project/
├── blog-frontend/          # 前端项目
│   ├── src/
│   │   ├── components/     # 组件目录
│   │   ├── views/          # 页面目录
│   │   ├── router/         # 路由配置
│   │   ├── store/          # 状态管理
│   │   ├── utils/          # 工具函数
│   │   ├── App.vue         # 根组件
│   │   └── main.ts         # 入口文件
│   ├── public/             # 静态资源
│   ├── package.json        # 依赖配置
│   ├── tsconfig.json       # TypeScript配置
│   └── vite.config.ts      # Vite配置
└── blog-backend/           # 后端项目
    ├── src/
    │   ├── main/java/com/blog/
    │   │   ├── controller/  # 控制器
    │   │   ├── entity/      # 实体类
    │   │   ├── mapper/      # 数据访问层
    │   │   ├── service/     # 业务逻辑层
    │   │   ├── utils/       # 工具类
    │   │   ├── config/      # 配置类
    │   │   └── BlogApplication.java  # 主类
    │   └── main/resources/
    │       ├── application.yml  # 配置文件
    │       └── data.sql         # 数据库初始化脚本
    ├── pom.xml              # Maven配置
    └── target/              # 构建输出目录
```

## 核心功能

### 用户管理
- 用户注册
- 用户登录
- JWT认证
- 权限管理

### 文章管理
- 文章发布（支持Markdown）
- 文章编辑
- 文章删除
- 文章列表
- 文章详情
- 浏览量统计

### 工具分享
- 工具发布
- 工具编辑
- 工具删除
- 工具列表
- 工具详情
- 浏览量统计

### 分类与标签
- 文章分类
- 工具分类
- 标签管理

## 快速开始

### 前置条件
- JDK 25+
- MySQL 8.0+
- Redis 6.0+
- Node.js 18+

### 后端启动

1. **配置数据库**
   - 创建数据库：`CREATE DATABASE blog DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   - 修改 `blog-backend/src/main/resources/application.yml` 中的数据库配置

2. **启动Redis**
   - 确保Redis服务正在运行
   - 修改 `blog-backend/src/main/resources/application.yml` 中的Redis配置（如果需要）

3. **构建并启动后端项目**
   ```bash
   cd blog-backend
   mvn clean install
   mvn spring-boot:run
   ```

### 前端启动

1. **安装依赖**
   ```bash
   cd blog-frontend
   npm install
   ```

2. **启动前端项目**
   ```bash
   npm run dev
   ```

3. **访问应用**
   - 前端访问地址：http://localhost:5173
   - 后端API地址：http://localhost:8080/api

## API文档

后端API文档使用Swagger3生成，访问地址：http://localhost:8080/swagger-ui/index.html

## 初始数据

项目包含初始数据，通过 `data.sql` 脚本自动导入：

- 管理员用户：
  - 用户名：admin
  - 密码：admin123

- 测试用户：
  - 用户名：test
  - 密码：test123

- 文章分类：技术分享、生活随笔、学习笔记、其他
- 工具分类：开发工具、设计工具、在线服务、其他工具
- 标签：Vue、React、Spring Boot、JavaScript、TypeScript、Markdown、开发工具、设计工具

## 开发指南

### 前端开发

1. **组件开发**：在 `src/components` 目录下创建组件
2. **页面开发**：在 `src/views` 目录下创建页面
3. **路由配置**：在 `src/router/index.ts` 中配置路由
4. **状态管理**：在 `src/store` 目录下创建状态管理模块
5. **API调用**：使用 `src/utils/axios.ts` 中的实例进行API调用

### 后端开发

1. **实体类开发**：在 `src/main/java/com/blog/entity` 目录下创建实体类
2. **控制器开发**：在 `src/main/java/com/blog/controller` 目录下创建控制器
3. **业务逻辑开发**：在 `src/main/java/com/blog/service` 目录下创建业务逻辑
4. **数据访问层开发**：在 `src/main/java/com/blog/mapper` 目录下创建数据访问层
5. **工具类开发**：在 `src/main/java/com/blog/utils` 目录下创建工具类
6. **配置类开发**：在 `src/main/java/com/blog/config` 目录下创建配置类

## 部署指南

### 前端部署

1. **构建生产版本**
   ```bash
   cd blog-frontend
   npm run build
   ```

2. **部署到服务器**
   - 将 `dist` 目录下的文件部署到Nginx或其他Web服务器

### 后端部署

1. **构建生产版本**
   ```bash
   cd blog-backend
   mvn clean package -DskipTests
   ```

2. **部署到服务器**
   - 将 `target` 目录下的JAR文件部署到服务器
   - 使用 `java -jar blog-backend-1.0.0.jar` 命令启动应用

## 项目特性

1. **前后端分离**：采用前后端分离的架构设计，提高开发效率和系统可维护性
2. **响应式设计**：前端页面采用响应式设计，适配不同设备尺寸
3. **Markdown支持**：文章支持Markdown格式编写，实时预览
4. **JWT认证**：采用JWT进行身份认证，保证系统安全性
5. **权限管理**：区分普通用户和管理员角色，实现细粒度的权限控制
6. **高性能**：使用Redis缓存热点数据，提高系统响应速度
7. **易于扩展**：模块化设计，便于后续功能扩展

## 注意事项

1. 首次启动项目时，会自动创建数据库表结构并导入初始数据
2. 请确保JDK版本为25或以上
3. 请确保MySQL版本为8.0或以上
4. 请确保Redis服务正在运行
5. 开发环境下，前端通过Vite的代理配置访问后端API

## 联系方式

如有问题或建议，欢迎联系项目团队。

