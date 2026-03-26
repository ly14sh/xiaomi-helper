# 智能手机使用指南 PDF阅读器

一个基于Android的PDF阅读器应用，专为《智能手机使用指南》电子书设计。采用MIUI风格设计，提供流畅的阅读体验。

## 功能特点

- 📖 **PDF阅读** - 支持本地PDF文件渲染，流畅翻页
- 👆 **多种翻页方式** - 点击翻页、滑动翻页、底栏按钮翻页
- 🔍 **双指缩放** - 支持双指捏合放大/缩小，最大4倍
- ✋ **自由拖动** - 放大后可自由拖动查看细节
- 📑 **目录导航** - 快速跳转到指定章节，CardView弹窗样式
- 🔢 **页码跳转** - 支持直接输入页码跳转
- 🎨 **MIUI风格** - 蓝色主题，现代化UI设计
- 📱 **全屏沉浸** - 隐藏状态栏，获得沉浸式阅读体验
- 🎬 **视频播放** - 部分页面支持播放教学视频（调用系统播放器）

## 操作方式

| 操作 | 功能 |
|------|------|
| 左半边点击 | 上一页 |
| 右半边点击 | 下一页 |
| 中间点击 | 显示/隐藏工具栏 |
| 左右滑动 | 翻页 |
| 双指捏合 | 放大/缩小 |
| 放大后拖动 | 查看细节 |
| 底栏页码 | 点击跳转指定页 |
| 目录按钮 | 显示章节列表 |

## 界面截图

- 顶栏：小米Logo + 标题 + 目录按钮
- 底栏：上一页/页码/下一页（等宽分布）
- 播放按钮：右下角半透明圆形按钮（仅视频页面显示）

## 技术栈

- Android Native (Java)
- PdfRenderer API
- 自定义PageTurnView实现翻页动画
- CardView现代化弹窗
- JSON配置文件（视频页面配置）

## 项目结构

```
app/
├── src/main/
│   ├── java/com/pdf/guide/
│   │   ├── MainActivity.java      # 主界面
│   │   ├── PDFImageView.java      # PDF显示组件
│   │   └── PageTurnView.java      # 翻页动画组件
│   ├── assets/
│   │   ├── guide.pdf             # PDF文件
│   │   └── video_config.json     # 视频配置文件
│   └── res/drawable/
│       └── xiaomi_logo.png       # 小米Logo
└── build.gradle
```

## 下载与构建

### 方法一：直接下载APK

从Releases页面下载最新版本：
- 📥 [xiaomi_helper_v1.2.apk](https://github.com/ly14sh/xiaomi-helper/releases/download/v1.2/xiaomi_helper_v1.2.apk)

### 方法二：自行构建

> ⚠️ **注意**：自行构建前需要下载资源文件

**步骤1：下载资源文件**

由于PDF文件较大(61MB)，需要从Releases下载：
- 📥 [下载 guide.zip](https://github.com/ly14sh/xiaomi-helper/releases/download/v1.02/guide.zip) (位于 v1.02 Release)
- 将 `guide.zip` 解压后得到 `guide.pdf`，放入 `app/src/main/assets/` 目录

**步骤2：构建项目**

```bash
# 克隆项目
git clone https://github.com/ly14sh/xiaomi-helper.git
cd xiaomi-helper

# 确保资源文件已放入 app/src/main/assets/guide.pdf

# 构建Debug版本
./gradlew assembleDebug

# APK输出路径
app/build/outputs/apk/debug/app-debug.apk
```

## 视频配置

视频页面配置位于 `app/src/main/assets/video_config.json`：

```json
{
  "pages": {
    "9": {
      "hasVideo": 1,
      "url": "https://..."
    }
  }
}
```

- `hasVideo`: 1表示有视频，0表示无视频
- `url`: 视频播放地址

## 版本历史

### v1.2 (2026-03-26)
- ✨ 新增15个视频页面配置（第9,12,13,15,17,19,21,23,25,29,31,33,35,36,43页）
- ⚡ 优化PDF渲染性能（异步渲染、分辨率限制）
- 🎨 新增视频播放按钮（圆形播放图标）
- 🐛 修复视频配置文件格式问题

### v1.1 (2026-03-26)
- ✨ 新增视频播放功能（调用系统播放器）
- ✨ 新增JSON配置文件管理视频页面
- 🎨 新增现代化CardView目录弹窗
- 🎨 新增页码跳转对话框
- 💄 优化顶栏布局，标题更居中
- 💄 优化底栏按钮间距和字体大小
- 💄 优化翻页动画速度（80ms）
- 🐛 修复放大后拖动冲突问题
- 🐛 修复LOGO显示不全问题

### v1.02
- ✨ 添加页面滑动翻页动画
- ✨ 边缘阴影效果优化
- 🐛 修复底栏布局问题

### v1.00
- 🎉 初始版本发布
- ✨ 基本PDF阅读功能
- ✨ 目录导航功能

## License

MIT License

Copyright (c) 2026 ly14sh

---

**免责声明**：本应用中的PDF文档《智能手机使用指南》版权归 Xiaomi Inc. 所有，仅用于学习和演示目的。
