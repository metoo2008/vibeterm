package dev.vibeterm.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** 密码加密存储(Android Keystore, AES256-GCM)。 */
object SecureStore {
    @Volatile
    private var prefs: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences =
        prefs ?: synchronized(this) {
            prefs ?: run {
                val key = MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context.applicationContext,
                    "secrets",
                    key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                ).also { prefs = it }
            }
        }

    fun putPassword(context: Context, hostId: String, password: String) {
        prefs(context).edit().putString("pw_$hostId", password).apply()
    }

    fun getPassword(context: Context, hostId: String): String? =
        prefs(context).getString("pw_$hostId", null)

    fun removePassword(context: Context, hostId: String) {
        prefs(context).edit().remove("pw_$hostId").apply()
    }
}
