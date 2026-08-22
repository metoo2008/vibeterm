package dev.vibeterm.data

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.File

/** 主机指纹 TOFU(Trust On First Use)存储。 */
object KnownHosts {
    private const val FILE_NAME = "known_hosts.json"
    private val lock = Any()

    sealed interface Result {
        object Trusted : Result
        object FirstUse : Result
        data class Mismatch(val storedAlgorithm: String) : Result
    }

    /** 校验主机公钥;首次连接自动信任并记录。 */
    fun verify(context: Context, host: String, port: Int, algorithm: String, key: ByteArray): Result {
        synchronized(lock) {
            val f = File(context.filesDir, FILE_NAME)
            val json = if (f.exists()) JSONObject(f.readText()) else JSONObject()
            val id = "$host:$port"
            val encoded = "$algorithm ${Base64.encodeToString(key, Base64.NO_WRAP)}"
            val stored = json.optString(id, "")
            return when {
                stored.isEmpty() -> {
                    json.put(id, encoded)
                    f.writeText(json.toString(2))
                    Result.FirstUse
                }
                stored == encoded -> Result.Trusted
                else -> Result.Mismatch(stored.substringBefore(' '))
            }
        }
    }

    fun forget(context: Context, host: String, port: Int) {
        synchronized(lock) {
            val f = File(context.filesDir, FILE_NAME)
            if (!f.exists()) return
            val json = JSONObject(f.readText())
            json.remove("$host:$port")
            f.writeText(json.toString(2))
        }
    }
}
