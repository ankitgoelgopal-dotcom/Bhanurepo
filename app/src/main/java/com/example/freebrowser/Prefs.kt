package com.example.freebrowser

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("browser", Context.MODE_PRIVATE)

    var bypass: Boolean
        get() = sp.getBoolean("bypass", true)
        set(v) = sp.edit().putBoolean("bypass", v).apply()
    var home: String
        get() = sp.getString("home", "mysearch:home") ?: ""
        set(v) = sp.edit().putString("home", v).apply()
    /** Search URL template; %s is replaced by the encoded query. */
    var search: String
        get() = sp.getString("search", "https://duckduckgo.com/?q=%s") ?: ""
        set(v) = sp.edit().putString("search", v).apply()
    var appName: String
        get() = sp.getString("appName", "Zephyr") ?: "Zephyr"
        set(v) = sp.edit().putString("appName", v).apply()
    /** Base URL of your SearXNG server, e.g. http://1.2.3.4:8080 */
    var searchServer: String
        get() = sp.getString("server", "") ?: ""
        set(v) = sp.edit().putString("server", v).apply()
    /** One "Name: value" per line. */
    var headersText: String
        get() = sp.getString("headers", "") ?: ""
        set(v) = sp.edit().putString("headers", v).apply()

    fun customHeaders(): Map<String, String> = headersText.lines().mapNotNull {
        val i = it.indexOf(':')
        if (i > 0) it.substring(0, i).trim() to it.substring(i + 1).trim() else null
    }.toMap()

    fun extEnabled(id: String) = sp.getBoolean("ext_$id", true)
    fun setExtEnabled(id: String, v: Boolean) = sp.edit().putBoolean("ext_$id", v).apply()
}
