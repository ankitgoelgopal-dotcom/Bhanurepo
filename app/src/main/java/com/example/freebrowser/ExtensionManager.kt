package com.example.freebrowser

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.WebView
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

class ContentScript(val matches: List<Regex>, val js: List<String>, val css: List<String>, val runAt: String)

class Extension(
    val id: String, val name: String, val version: String,
    val dir: File, val scripts: List<ContentScript>, val background: List<String>
)

/** Minimal loader for unpacked Chrome-style (MV2/MV3) extensions: content_scripts + background scripts. */
class ExtensionManager(private val ctx: Context, private val prefs: Prefs) {
    private val root = File(ctx.filesDir, "extensions").apply { mkdirs() }
    val list = mutableStateListOf<Extension>()
    private var bg: WebView? = null

    private val shim = """
(function(){window.chrome=window.chrome||{};var c=window.chrome;
c.runtime=c.runtime||{id:'ext',getURL:function(p){return p},getManifest:function(){return {}},
sendMessage:function(){},onMessage:{addListener:function(){}}};
c.storage=c.storage||{local:{
get:function(k,cb){var o={};try{o=JSON.parse(localStorage.getItem('__ext')||'{}')}catch(e){}var r={};
if(k==null)r=o;else if(typeof k==='string'){if(k in o)r[k]=o[k]}
else if(Array.isArray(k))k.forEach(function(x){if(x in o)r[x]=o[x]});
else for(var x in k)r[x]=(x in o)?o[x]:k[x];cb&&cb(r)},
set:function(v,cb){var o={};try{o=JSON.parse(localStorage.getItem('__ext')||'{}')}catch(e){}
for(var x in v)o[x]=v[x];localStorage.setItem('__ext',JSON.stringify(o));cb&&cb()}}};})();
""".trimIndent()

    init { reload() }

    fun reload() {
        list.clear()
        root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { d ->
            runCatching { load(d) }.getOrNull()?.let { list.add(it) }
        }
    }

    private fun load(dir: File): Extension? {
        val mf = File(dir, "manifest.json").takeIf { it.exists() }
            ?: dir.listFiles()?.firstNotNullOfOrNull { c -> File(c, "manifest.json").takeIf { it.exists() } }
            ?: return null
        val base = mf.parentFile!!
        val m = JSONObject(mf.readText())
        val scripts = mutableListOf<ContentScript>()
        m.optJSONArray("content_scripts")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                scripts += ContentScript(
                    strings(o.optJSONArray("matches")).map(::toRegex),
                    strings(o.optJSONArray("js")).map { File(base, it).takeIf(File::exists)?.readText() ?: "" },
                    strings(o.optJSONArray("css")).map { File(base, it).takeIf(File::exists)?.readText() ?: "" },
                    o.optString("run_at", "document_idle")
                )
            }
        }
        val bgScripts = mutableListOf<String>()
        m.optJSONObject("background")?.optJSONArray("scripts")?.let { a ->
            strings(a).forEach { f -> File(base, f).takeIf(File::exists)?.let { bgScripts += it.readText() } }
        }
        return Extension(dir.name, m.optString("name", dir.name), m.optString("version", "?"), base, scripts, bgScripts)
    }

    private fun strings(a: org.json.JSONArray?): List<String> =
        if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }

    private fun toRegex(p: String): Regex {
        if (p == "<all_urls>") return Regex("^https?://.*")
        val m = Regex("^(\\*|https?)://([^/]*)(/.*)$").find(p) ?: return Regex("a^")
        val (s, h, path) = m.destructured
        val sr = if (s == "*") "https?" else s
        val hr = when {
            h == "*" -> "[^/]+"
            h.startsWith("*.") -> "([^/]+\\.)?" + Regex.escape(h.drop(2))
            else -> Regex.escape(h)
        }
        val pr = path.split("*").joinToString(".*") { Regex.escape(it) }
        return Regex("^$sr://$hr$pr$")
    }

    /** JS snippets to evaluate for [url] at [stage] ("document_start" or "document_end"). */
    fun scriptsFor(url: String, stage: String): List<String> {
        val out = mutableListOf<String>()
        for (e in list) {
            if (!prefs.extEnabled(e.id)) continue
            for (cs in e.scripts) {
                val atStart = cs.runAt == "document_start"
                if ((stage == "document_start") != atStart) continue
                if (cs.matches.none { it.matches(url) }) continue
                cs.css.forEach {
                    out += "(function(){var s=document.createElement('style');s.textContent=${JSONObject.quote(it)};" +
                        "(document.head||document.documentElement).appendChild(s)})();"
                }
                cs.js.forEach { out += "$shim\n(function(){try{\n$it\n}catch(e){console.error('ext ${e.name}',e)}})();" }
            }
        }
        return out
    }

    fun importZip(uri: Uri): Boolean = runCatching {
        val name = "ext_" + System.currentTimeMillis()
        val dest = File(root, name).apply { mkdirs() }
        ctx.contentResolver.openInputStream(uri)!!.use { ins ->
            ZipInputStream(ins).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val f = File(dest, entry.name)
                    if (!f.canonicalPath.startsWith(dest.canonicalPath)) throw SecurityException("bad zip entry")
                    if (entry.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); f.outputStream().use { zip.copyTo(it) } }
                    entry = zip.nextEntry
                }
            }
        }
        if (load(dest) == null) { dest.deleteRecursively(); false } else { reload(); startBackground(); true }
    }.getOrDefault(false)

    fun delete(e: Extension) { File(root, e.id).deleteRecursively(); reload(); startBackground() }

    /** Runs enabled extensions' background scripts in a hidden WebView. */
    @SuppressLint("SetJavaScriptEnabled")
    fun startBackground() {
        bg?.destroy()
        val scripts = list.filter { prefs.extEnabled(it.id) }.flatMap { e -> e.background.map { e to it } }
        if (scripts.isEmpty()) { bg = null; return }
        val w = WebView(ctx)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.loadDataWithBaseURL("https://background.invalid/", "<html><body></body></html>", "text/html", "utf-8", null)
        scripts.forEach { (e, js) ->
            w.evaluateJavascript("$shim\n(function(){try{\n$js\n}catch(x){console.error('bg ${e.name}',x)}})();", null)
        }
        bg = w
    }
}
