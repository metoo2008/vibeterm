package dev.vibeterm.data

import java.io.File
import java.io.IOException

/**
 * 临时文件 + 同目录原子重命名,避免进程被杀或存储故障时写坏配置文件。
 * Linux/Android 上同目录 rename 覆盖目标是原子操作。
 *
 * rename 失败时**保留旧文件并抛异常**——绝不回退成直接覆盖(那会重新引入写坏风险)。
 */
internal fun File.writeTextAtomic(text: String) {
    val tmp = File(parentFile, "$name.tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(this)) {
        tmp.delete()
        throw IOException("atomic rename failed: $tmp -> $this(旧文件已保留)")
    }
}
