package dev.vibeterm.data

import android.content.Context
import org.json.JSONArray
import java.io.File

/** 主机列表持久化:filesDir/hosts.json(不含密码)。 */
object HostStore {
    private const val FILE_NAME = "hosts.json"

    fun load(context: Context): MutableList<HostProfile> {
        val f = File(context.filesDir, FILE_NAME)
        if (!f.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(f.readText())
            MutableList(arr.length()) { HostProfile.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun save(context: Context, hosts: List<HostProfile>) {
        val arr = JSONArray()
        hosts.forEach { arr.put(it.toJson()) }
        File(context.filesDir, FILE_NAME).writeTextAtomic(arr.toString(2))
    }
}
