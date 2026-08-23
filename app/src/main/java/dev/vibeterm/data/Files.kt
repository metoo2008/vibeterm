package dev.vibeterm.data

import java.io.File

/**
 * 临时文件 + 同目录原子重命名,避免进程被杀或存储故障时写坏配置文件。
 * Linux/Android 上同目录 rename 覆盖目标是原子操作。
 */
internal fun File.writeTextAtomic(text: String) {
    val tmp = File(parentFile, "$name.tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(this)) {
        // 极少数文件系统 rename 失败时回退为直接写,至少不丢新数据
        writeText(text)
        tmp.delete()
    }
}
