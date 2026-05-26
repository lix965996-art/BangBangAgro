# 帮帮农 Android App

这是使用 Capacitor 打包的安卓 App，基于原有的 Vue 前端项目。

## 项目结构

```
android-app/
├── android/                    # Android 原生项目
│   ├── app/
│   │   └── src/
│   │       └── main/
│   │           └── assets/
│   │               └── public/ # Vue 构建产物
│   ├── gradle/
│   └── build.gradle
├── www/                        # Vue 构建产物（Web 资源）
├── capacitor.config.json       # Capacitor 配置文件
├── package.json
└── README.md
```

## 前置要求

1. **Java JDK 11 或更高版本**
   - 下载：https://adoptium.net/
   - 配置 JAVA_HOME 环境变量

2. **Android Studio**（推荐）
   - 下载：https://developer.android.com/studio
   - 安装 Android SDK、Build Tools、Platform Tools

3. **Gradle**（Android Studio 自带）

## 构建步骤

### 方式一：使用 Android Studio（推荐）

1. 打开 Android Studio
2. 选择 "Open an existing Android Studio project"
3. 导航到 `android-app/android` 文件夹
4. 等待 Gradle 同步完成
5. 点击 Build > Build Bundle(s) / APK(s) > Build APK(s)
6. APK 文件位置：`android/app/build/outputs/apk/debug/app-debug.apk`

### 方式二：使用命令行

```bash
# 进入 Android 项目目录
cd android-app/android

# 构建 debug APK
./gradlew assembleDebug

# 构建 release APK（需要签名配置）
./gradlew assembleRelease
```

## 配置说明

### 修改 App 名称

编辑 `android/app/src/main/res/values/strings.xml`：

```xml
<string name="app_name">帮帮农</string>
```

### 修改 App 图标

1. 准备不同尺寸的图标文件
2. 替换 `android/app/src/main/res/mipmap-*` 文件夹中的图标
3. 图标尺寸：
   - mipmap-mdpi: 48x48
   - mipmap-hdpi: 72x72
   - mipmap-xhdpi: 96x96
   - mipmap-xxhdpi: 144x144
   - mipmap-xxxhdpi: 192x192

### 修改服务器地址

编辑 Vue 项目中的环境变量文件，然后重新构建：

```javascript
// vue/.env.production
VUE_APP_BASE_API = http://你的服务器IP:9090
```

重新构建步骤：

```bash
# 1. 重新构建 Vue 项目
cd ../vue
npm run build

# 2. 复制构建产物到 www 文件夹
xcopy /E /I /Y dist\* ..\android-app\www

# 3. 同步到 Android 项目
cd ../android-app
npx cap sync android

# 4. 重新构建 Android App
cd android
./gradlew assembleDebug
```

## 调试方法

### 使用 Chrome DevTools

1. 在 Android 手机上开启 USB 调试
2. 连接手机到电脑
3. 打开 Chrome 浏览器，访问 `chrome://inspect`
4. 找到你的 App，点击 "inspect"
5. 可以像调试网页一样调试 App

### 查看日志

```bash
# 查看 Android 日志
adb logcat | grep -i "capacitor"
```

## 常见问题

### 1. Gradle 构建失败

**问题**：`Could not find tools.jar`

**解决**：确保 JAVA_HOME 环境变量正确设置

```bash
# 检查 Java 版本
java -version

# 设置 JAVA_HOME（Windows）
set JAVA_HOME=C:\Program Files\Java\jdk-17
```

### 2. 网络请求失败

**问题**：App 中无法访问后端 API

**解决**：

1. 确保手机和服务器在同一网络
2. 检查服务器防火墙设置
3. 使用服务器的局域网 IP 地址

### 3. 白屏问题

**问题**：App 打开后显示白屏

**解决**：

1. 检查 `www` 文件夹是否包含 `index.html`
2. 确保 Vue 项目的 `publicPath` 设置为 `./`
3. 检查控制台错误信息

## 上架应用商店

### 1. 生成签名密钥

```bash
keytool -genkey -v -keystore bangbangagro.keystore -alias bangbangagro -keyalg RSA -keysize 2048 -validity 10000
```

### 2. 配置签名

编辑 `android/app/build.gradle`：

```android {
    signingConfigs {
        release {
            storeFile file('path/to/bangbangagro.keystore')
            storePassword 'your_password'
            keyAlias 'bangbangagro'
            keyPassword 'your_key_password'
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
        }
    }
}
```

### 3. 构建 Release APK

```bash
cd android
./gradlew assembleRelease
```

### 4. 上架要求

- App 图标（512x512）
- 应用截图（多种尺寸）
- 隐私政策链接
- 应用描述

## 更新流程

当 Vue 前端代码更新后：

```bash
# 1. 重新构建 Vue 项目
cd ../vue
npm run build

# 2. 复制新的构建产物
xcopy /E /I /Y dist\* ..\android-app\www

# 3. 同步到 Android
cd ../android-app
npx cap sync android

# 4. 重新构建 App
cd android
./gradlew assembleDebug
```

## 技术栈

- **前端**：Vue 2.6 + Element UI
- **打包工具**：Capacitor 8.x
- **Android**：原生 Android 项目
- **后端**：Spring Boot（独立部署）

## 注意事项

1. **不要修改 `android` 文件夹中的原生代码**，除非你了解 Android 开发
2. **所有前端修改都在 Vue 项目中进行**，然后重新打包
3. **确保后端服务正常运行**，App 需要访问 API
4. **测试时使用真机**，模拟器可能有兼容性问题

## 获取帮助

- Capacitor 官方文档：https://capacitorjs.com/docs
- Android 开发者文档：https://developer.android.com/
- Vue.js 官方文档：https://vuejs.org/