package com.example.freebrowser

import android.text.TextUtils
import org.json.JSONObject
import java.net.URI

object Pages {
    private fun e(s: String) = TextUtils.htmlEncode(s)

    private val css = """
:root{--bg:#fff;--fg:#1a1a1a;--mut:#666;--ac:#2563eb;--card:#f3f4f6}
@media(prefers-color-scheme:dark){:root{--bg:#121212;--fg:#eee;--mut:#9aa;--ac:#7aa7ff;--card:#1e1e1e}}
*{box-sizing:border-box}body{margin:0;padding:16px;background:var(--bg);color:var(--fg);font:16px system-ui,sans-serif}
h1{font-size:34px;text-align:center;margin:12vh 0 20px}form{display:flex;gap:8px}
input{flex:1;padding:12px 14px;border:1px solid var(--mut);border-radius:24px;font-size:16px;background:var(--card);color:var(--fg)}
button{padding:12px 18px;border:0;border-radius:24px;background:var(--ac);color:#fff;font-size:16px}
.r{margin:18px 0}.r a{color:var(--ac);font-size:18px;text-decoration:none}.u{color:var(--mut);font-size:13px}
.r p{margin:4px 0 0}.note{color:var(--mut);text-align:center;margin-top:16px}
.more{display:block;text-align:center;margin:24px 0}
""".trimIndent()

    private fun box(q: String) = """<form onsubmit="App.search(this.q.value,1);return false">
<input name="q" value="${e(q)}" placeholder="Search" autocomplete="off"><button>Go</button></form>"""

    private fun page(body: String) = """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><style>$css</style></head><body>$body</body></html>"""

    fun start(name: String, note: String = ""): String =
        page("<h1>${e(name)}</h1>${box("")}" + if (note.isNotEmpty()) "<p class=note>${e(note)}</p>" else "")

    fun results(name: String, q: String, page: Int, json: JSONObject): String {
        val sb = StringBuilder("<div style='margin-top:4px'>${box(q)}</div>")
        val arr = json.optJSONArray("results")
        if (arr == null || arr.length() == 0) sb.append("<p class=note>No results.</p>")
        else for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            val url = r.optString("url")
            val host = runCatching { URI(url).host }.getOrNull() ?: url
            sb.append("<div class=r><div class=u>${e(host)}</div><a href=\"${e(url)}\">${e(r.optString("title", url))}</a>")
            sb.append("<p>${e(r.optString("content"))}</p></div>")
        }
        if (arr != null && arr.length() > 0)
            sb.append("<a class=more href=# data-q=\"${e(q)}\" data-p=\"${page + 1}\" onclick=\"App.search(this.dataset.q,+this.dataset.p);return false\">More results</a>")
        return page(sb.toString())
    }
}
