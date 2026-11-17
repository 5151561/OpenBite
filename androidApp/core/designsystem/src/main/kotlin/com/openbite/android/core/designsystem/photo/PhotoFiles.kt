package com.openbite.android.core.designsystem.photo

import android.content.Context
import java.io.File
import java.io.InputStream

/**
 * App 里照片路径的唯一解析规则，PhotoStore 和界面读图共用：
 * - `seed/` 开头：打包在 assets 里的示例图；
 * - 其余：filesDir 下的相对路径，规范化后必须落在 `filesDir/photos/` 里面（路径来自数据库和导入的数据，不能信）。
 */
object PhotoFiles {
    const val DIR = "photos"
    const val SEED_PREFIX = "seed/"

    /** [relativePath] 对应的照片文件；越出 `photos/` 目录时返回 null。 */
    fun photoFile(root: File, relativePath: String): File? {
        val base = File(root, DIR).canonicalFile
        val file = File(root, relativePath).canonicalFile
        // 按路径分段比较，`photosX/…` 这类前缀相同的兄弟目录不算在里面。
        return file.takeIf { it != base && it.toPath().startsWith(base.toPath()) }
    }

    fun isSeed(relativePath: String): Boolean = relativePath.startsWith(SEED_PREFIX) && ".." !in relativePath

    /** 打开照片；路径不合法时返回 null。在 IO 线程调用。 */
    fun open(context: Context, relativePath: String): InputStream? = when {
        isSeed(relativePath) -> context.assets.open(relativePath)
        else -> photoFile(context.filesDir, relativePath)?.inputStream()
    }
}
