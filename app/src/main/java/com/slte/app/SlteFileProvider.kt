package com.slte.app

import androidx.core.content.FileProvider

/** 应用自身文件分享（安装包/日志导出），与 Crisp SDK 的 FileProvider 隔离 */
class SlteFileProvider : FileProvider()
