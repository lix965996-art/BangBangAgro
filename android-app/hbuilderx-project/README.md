# 帮帮农 HBuilderX 项目

这是一个使用 HBuilderX 打包的安卓 App 项目。

## 使用方法

### 1. 安装 HBuilderX

下载地址：https://www.dcloud.io/hbuilderx.html

选择 **App 开发版**。

### 2. 导入项目

1. 打开 HBuilderX
2. 文件 → 导入 → 从本地目录导入
3. 选择 `hbuilderx-project` 文件夹

### 3. 修改服务器地址

编辑 `index.html` 文件，找到这行：

```javascript
var SERVER_URL = 'http://你的服务器IP:9090';
```

修改为你的实际服务器地址，例如：

```javascript
var SERVER_URL = 'http://192.168.1.100:9090';
```

或者如果你的项目已部署到公网：

```javascript
var SERVER_URL = 'http://你的域名.com';
```

### 4. 云打包

1. 菜单：发行 → 原生App-云打包
2. 选择打包类型：
   - **Android**：打包安卓 APK
   - **iOS**：打包 iOS IPA（需要苹果开发者账号）
3. 勾选需要的权限（摄像头、定位等）
4. 点击打包
5. 等待打包完成（通常 5-10 分钟）
6. 下载 APK 文件

### 5. 安装测试

1. 将 APK 文件传到手机
2. 安装并打开
3. 测试功能是否正常

## 项目结构

```
hbuilderx-project/
├── index.html          # 主页面（加载你的网页）
├── manifest.json       # App 配置文件
├── Logo.jpg           # App 图标
└── README.md          # 说明文档
```

## 配置说明

### manifest.json 主要配置

| 配置项 | 说明 | 当前值 |
|--------|------|--------|
| `name` | App 名称 | 帮帮农 |
| `appid` | App ID | __UNI__BANGBANGAGRO |
| `versionName` | 版本号 | 1.0.0 |
| `versionCode` | 版本代码 | 100 |

### 权限配置

已配置以下权限：
- ✅ 摄像头（用于 AI 检测拍照）
- ✅ 定位（用于农田定位）
- ✅ 网络访问
- ✅ 存储读写

### Android 配置

- 最低版本：Android 5.0 (API 21)
- 目标版本：Android 11 (API 30)
- 支持架构：armeabi-v7a, arm64-v8a

## 常见问题

### Q1: 打包失败

**可能原因**：
- 网络问题（云打包需要联网）
- HBuilderX 版本太旧
- manifest.json 格式错误

**解决方案**：
- 检查网络连接
- 更新 HBuilderX 到最新版
- 验证 JSON 格式是否正确

### Q2: App 白屏

**可能原因**：
- 服务器地址配置错误
- 服务器未启动
- 网络不通

**解决方案**：
- 检查 `index.html` 中的 SERVER_URL
- 确保后端服务正在运行
- 测试手机能否访问服务器地址

### Q3: 功能不正常

**可能原因**：
- 权限未正确配置
- API 接口不兼容

**解决方案**：
- 检查 manifest.json 中的权限配置
- 测试 API 接口是否正常

### Q4: 如何更新 App？

**步骤**：
1. 修改 `index.html` 中的服务器地址（如果需要）
2. 重新云打包
3. 生成新的 APK
4. 替换旧版本

## 进阶配置

### 修改 App 图标

1. 准备图标文件（建议 1024x1024 像素）
2. 替换 `Logo.jpg` 文件
3. 重新打包

### 添加启动页

1. 在 manifest.json 中配置启动页
2. 添加启动页图片
3. 重新打包

### 配置推送通知

1. 在 manifest.json 中添加推送模块
2. 配置推送服务（如个推、极光等）
3. 重新打包

## 技术说明

### 工作原理

这个 App 的工作原理是：
1. App 启动时加载 `index.html`
2. `index.html` 中的 JavaScript 创建一个全屏 iframe
3. iframe 加载你的 Vue 网页
4. 用户看到的是你的网页，但运行在 App 壳中

### 优点

- ✅ 开发速度快
- ✅ 不需要原生开发知识
- ✅ 云端打包，免费
- ✅ 跨平台（Android + iOS）

### 缺点

- ❌ 性能比原生 App 慢
- ❌ 复杂功能可能受限
- ❌ 需要网络连接

## 联系方式

如有问题，请参考：
- HBuilderX 官方文档：https://uniapp.dcloud.net.cn/
- DCloud 社区：https://ask.dcloud.net.cn/

## 更新日志

### v1.0.0 (2026-05-25)
- 初始版本
- 支持基本功能
- 配置基本权限