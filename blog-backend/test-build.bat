@echo off
echo ====================================
echo 正在测试项目编译...
echo ====================================
echo.

cd /d "%~dp0"

echo [1/3] 清理项目...
call mvn clean
if %errorlevel% neq 0 (
    echo 清理失败！
    pause
    exit /b 1
)
echo.

echo [2/3] 编译项目...
call mvn compile
if %errorlevel% neq 0 (
    echo 编译失败！
    pause
    exit /b 1
)
echo.

echo [3/3] 打包项目...
call mvn package -DskipTests
if %errorlevel% neq 0 (
    echo 打包失败！
    pause
    exit /b 1
)
echo.

echo ====================================
echo 项目构建成功！
echo ====================================
echo.
echo 提示：
echo 1. 在启动前，请确保MySQL数据库已启动并创建了blog数据库
echo 2. 在启动前，请确保Redis服务已启动
echo 3. 在application.yml中修改数据库连接信息（如需要）
echo.
echo 启动命令：java -jar target/blog-backend-1.0.0.jar
echo 或者：mvn spring-boot:run
echo.
pause
