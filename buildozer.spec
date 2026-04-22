[app]

# 应用名称
title = CloudFlareScan
# 包名（小写，符合安卓规范）
package.name = cloudflarescan
# 包域名（反向域名格式）
package.domain = org.example
# 应用版本号
version = 3.0

# 主程序入口（对应你的代码文件）
source.dir = .
source.include_exts = py,png,jpg,kv,atlas
source.main.filename = CloudFlareScan.py

# 版本代码（安卓用，每次更新需递增）
android.api = 33
android.ndk = 25b
android.ndk.api = 21
android.sdk = 24

# 要求的权限（网络访问、网络状态、INTERNET 必须）
android.permissions = INTERNET,ACCESS_NETWORK_STATE,ACCESS_WIFI_STATE

# 依赖配置
# 注意：PySide6 在 buildozer 中安卓支持有限，需手动处理或替换为 Kivy（推荐）
requirements = python3,aiohttp,PySide6,ssl,asyncio,ipaddress,csv,platform

# 安卓架构
android.archs = arm64-v8a,armeabi-v7a,x86_64

# 应用图标（可选，需准备对应尺寸图片）
# android.icon = images/icon.png

# 启动画面（可选）
# android.presplash = images/presplash.png

# 权限提示
android.add_android_manifest = True

# 日志级别
log_level = 2

# 禁用自动更新
android.disable_update_check = True

# 额外的安卓配置
android.add_libs_armeabi_v7a = 
android.add_libs_arm64_v8a = 
android.add_libs_x86 = 
android.add_libs_x86_64 = 

# 构建类型（debug/release）
android.build_type = debug

# 禁用压缩
android.ndk_compile = False

# 环境变量
osx.python_version = 3.9
osx.kivy_version = 2.1.0

# 其他平台暂禁用
ios.deployment_target = 
ios.add_arc_libs = 
ios.add_frameworks = 
ios.mkdir = 

[buildozer]
# 日志级别
log_level = 2
# 构建目录
build_dir = ./.buildozer
# 分发目录
dist_dir = ./dist
# 缓存目录
cache_dir = ./.buildozer/cache
