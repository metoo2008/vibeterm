package dev.vibeterm.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/**
 * 主机公钥固定(key pinning)存储。
 *
 * 只读校验与落盘分离:[check] 绝不写盘,首次连接(Unknown)与密钥变更(Mismatch)的信任决定
 * 必须由 UI 层展示 SHA256 指纹、经用户确认后调用 [save] —— 首连静默信任会让中间人拿到密码。
 */
object KnownHosts {
    private const val FILE_NAME = "known_hosts.json"
    private const val TAG = "VibeTerm"
    private val lock = Any()

    sealed interface CheckResult {
        data object Trusted : CheckResult
        data object Unknown : CheckResult
        data class Mismatch(val storedAlgorithm: String, val storedFingerprint: String) : CheckResult
    }

    /** 只读校验当前主机公钥,不产生任何副作用。 */
    fun check(context: Context, host: String, port: Int, algorithm: String, key: ByteArray): CheckResult {
        synchronized(lock) {
            val stored = load(context).optString(id(host, port), "")
            return when {
                stored.isEmpty() -> CheckResult.Unknown
                stored == encode(algorithm, key) -> CheckResult.Trusted
                else -> CheckResult.Mismatch(
                    stored.substringBefore(' '),
                    runCatching {
                        fingerprint(Base64.getDecoder().decode(stored.substringAfter(' ')))
                    }.getOrDefault("?"),
                )
            }
        }
    }

    /** 用户确认信任后保存(或替换)主机公钥。 */
    fun save(context: Context, host: String, port: Int, algorithm: String, key: ByteArray) {
        synchronized(lock) {
            val json = load(context)
            json.put(id(host, port), encode(algorithm, key))
            file(context).writeTextAtomic(json.toString(2))
        }
    }

    fun forget(context: Context, host: String, port: Int) {
        synchronized(lock) {
            val f = file(context)
            if (!f.exists()) return
            val json = load(context)
            json.remove(id(host, port))
            f.writeTextAtomic(json.toString(2))
        }
    }

    /** OpenSSH 风格指纹:SHA256:base64(无填充),与 ssh-keygen -lf 输出一致,便于人工核对。 */
    fun fingerprint(key: ByteArray): String =
        "SHA256:" + Base64.getEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(key))

    private fun id(host: String, port: Int) = "$host:$port"

    private fun encode(algorithm: String, key: ByteArray) =
        "$algorithm ${Base64.getEncoder().encodeToString(key)}"

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private fun load(context: Context): JSONObject {
        val f = file(context)
        if (!f.exists()) return JSONObject()
        return runCatching { JSONObject(f.readText()) }.getOrElse {
            Log.w(TAG, "known_hosts.json 损坏,已重置", it)
            JSONObject()
        }
    }
}
