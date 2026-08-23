package dev.vibeterm.data

import org.json.JSONObject
import java.util.UUID

/** SSH 主机配置。密码不存这里,单独走 [SecureStore] 加密存储。 */
data class HostProfile(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val host: String = "",
    val port: Int = 22,
    val username: String = "",
    val useTmux: Boolean = true,
) {
    val displayName: String
        get() = label.ifBlank { "$username@$host" }

    /** 端口必须在合法范围;非法值回落 22,避免把无效端口写进配置或发起连接。 */
    val safePort: Int
        get() = if (port in 1..65535) port else 22

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("label", label)
        .put("host", host)
        .put("port", port)
        .put("username", username)
        .put("useTmux", useTmux)

    companion object {
        fun fromJson(o: JSONObject) = HostProfile(
            id = o.getString("id"),
            label = o.optString("label"),
            host = o.getString("host"),
            port = o.optInt("port", 22),
            username = o.getString("username"),
            useTmux = o.optBoolean("useTmux", true),
        )
    }
}
