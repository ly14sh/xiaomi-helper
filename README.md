# 智能手机使用指南 PDF阅读器

一个基于Android的PDF阅读器应用，专为《智能手机使用指南》电子书设计。

## 功能特点

- 📖 PDF阅读功能 - 支持本地PDF文件渲染
- 👆 点击翻页 - 左右点击快速翻页
- 🔍 双指缩放 - 支持双指捏合放大/缩小
- ✋ 自由拖动 - 放大后可自由拖动查看
- 📑 目录导航 - 快速跳转到指定章节
- 🎨 MIUI风格 - 蓝色主题，现代UI设计
- 📱 全屏沉浸 - 隐藏状态栏，获得沉浸式阅读体验

## 操作方式

- **左滑** → 下一页
- **右滑** → 上一页
- **左半边点击** → 上一页
- **右半边点击** → 下一页  
- **中间点击** → 显示/隐藏工具栏
- **双指捏合** → 放大/缩小
- **放大后单指拖动** → 查看细节

## 技术栈

- Android Native (Java)
- PdfRenderer API
- 自定义PageTurnView实现滑动翻页
- Material Design风格

## 项目结构

```
app/
├── src/main/
│   ├── java/com/pdf/guide/
│   │   ├── MainActivity.java      # 主界面
│   │   ├── PDFImageView.java      # PDF显示组件
│   │   └── PageTurnView.java      # 翻页动画组件
│   └── assets/
│       └── guide.zip             # PDF压缩包（首次启动自动解压）
└── build.gradle
```

## 下载PDF文件

由于PDF文件较大(61MB)，需要单独下载：
- 📥 [下载 guide.zip](https://github.com/ly14sh/xiaomi-helper/releases/download/v1.01/guide.zip)
- 将其放入 `app/src/main/assets/` 目录

## 构建

```bash
./gradlew assembleDebug
```

## 安装

构建后APK位于: `app/build/outputs/apk/debug/app-debug.apk`

## 版本历史

- **v1.01** - 添加页面滑动翻页动画，边缘阴影效果优化
- **v1.00** - 初始版本，基本阅读功能

## License

MIT License
