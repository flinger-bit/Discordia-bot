package com.discordia.terminal

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MenuItem
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.discordia.terminal.databinding.ActivityCodeEditorBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.concurrent.TimeUnit

class CodeEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCodeEditorBinding
    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentDir = "/sdcard"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCodeEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val openPath = intent.getStringExtra(EXTRA_OPEN_FILE)
        val openDir = intent.getStringExtra(EXTRA_OPEN_DIR) ?: "/sdcard"
        currentDir = openDir

        setupWebView()
        binding.webView.loadUrl("file:///android_asset/editor/index.html")

        if (openPath != null) {
            binding.webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val file = File(openPath)
                    binding.webView.evaluateJavascript("IDE.openFile('${openPath.replace("'","\\'")}','${file.name.replace("'","\\'")}');", null)
                }
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false
            }
        } else {
            binding.webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false
                override fun onPageFinished(view: WebView?, url: String?) {
                    binding.webView.evaluateJavascript("IDE.loadDirectory('${currentDir.replace("'","\\'")}');", null)
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        with(binding.webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            setSupportZoom(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        binding.webView.webChromeClient = WebChromeClient()
        binding.webView.addJavascriptInterface(AndroidBridge(), "Android")
        WebView.setWebContentsDebuggingEnabled(true)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && binding.webView.canGoBack()) {
            binding.webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ─────────────────────────────────────────────
    // JavaScript ↔ Android Bridge
    // ─────────────────────────────────────────────
    inner class AndroidBridge {

        @JavascriptInterface
        fun readFile(path: String): String {
            return try {
                File(path).readText()
            } catch (e: Exception) {
                "// Error reading file: ${e.message}"
            }
        }

        @JavascriptInterface
        fun writeFile(path: String, content: String) {
            try {
                val file = File(path)
                file.parentFile?.mkdirs()
                file.writeText(content)
                runOnUiThread { Toast.makeText(this@CodeEditorActivity, "Saved: ${file.name}", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this@CodeEditorActivity, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }

        @JavascriptInterface
        fun listDir(path: String): String {
            return try {
                val dir = File(path)
                val files = dir.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name }) ?: emptyList()
                val arr = JSONArray()
                files.forEach { f ->
                    arr.put(JSONObject().put("name", f.name).put("isDir", f.isDirectory).put("size", f.length()).put("modified", f.lastModified()))
                }
                arr.toString()
            } catch (e: Exception) {
                "[]"
            }
        }

        @JavascriptInterface
        fun runCommand(command: String): String {
            return try {
                val pb = ProcessBuilder("/system/bin/sh", "-c", command)
                    .directory(File(currentDir))
                    .redirectErrorStream(true)
                pb.environment()["HOME"] = "/sdcard"
                val proc = pb.start()
                proc.waitFor(15, TimeUnit.SECONDS)
                BufferedReader(InputStreamReader(proc.inputStream)).readText().trimEnd()
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }

        @JavascriptInterface
        fun getGitStatus(): String {
            return try {
                val status = runCommand("git -C \"$currentDir\" status --porcelain 2>/dev/null")
                val branch = runCommand("git -C \"$currentDir\" branch --show-current 2>/dev/null").trim().ifEmpty { "main" }
                val modified = JSONArray()
                val staged = JSONArray()
                val untracked = JSONArray()
                status.lines().forEach { line ->
                    if (line.length >= 3) {
                        val xy = line.substring(0, 2)
                        val file = line.substring(3).trim()
                        when {
                            xy.startsWith("M") || xy.startsWith("R") -> staged.put(file)
                            xy.endsWith("M") -> modified.put(file)
                            xy.startsWith("?") -> untracked.put(file)
                            xy.startsWith("A") -> staged.put(file)
                            else -> modified.put(file)
                        }
                    }
                }
                JSONObject().put("branch", branch).put("modified", modified).put("staged", staged).put("untracked", untracked).toString()
            } catch (e: Exception) {
                JSONObject().put("branch", "main").put("modified", JSONArray()).put("staged", JSONArray()).put("untracked", JSONArray()).toString()
            }
        }

        @JavascriptInterface
        fun gitAdd(path: String) {
            runCommand("git -C \"$currentDir\" add \"$path\" 2>&1")
        }

        @JavascriptInterface
        fun gitCommit(message: String): String {
            return try {
                val out = runCommand("git -C \"$currentDir\" commit -m \"$message\" 2>&1")
                if (out.contains("error") || out.contains("nothing to commit")) "false" else "true"
            } catch (e: Exception) { "false" }
        }

        @JavascriptInterface
        fun gitPush(): String {
            return try {
                val out = runCommand("git -C \"$currentDir\" push 2>&1")
                if (out.contains("error") || out.contains("fatal")) out else "true"
            } catch (e: Exception) { e.message ?: "false" }
        }

        @JavascriptInterface
        fun gitPull(): String {
            return try {
                runCommand("git -C \"$currentDir\" pull 2>&1")
            } catch (e: Exception) { e.message ?: "Error" }
        }

        @JavascriptInterface
        fun openFolder(path: String) {
            currentDir = path
            runOnUiThread {
                binding.webView.evaluateJavascript("IDE.loadDirectory('${path.replace("'","\\'")}');", null)
            }
        }

        @JavascriptInterface
        fun getSettings(): String {
            return try {
                val prefs = getSharedPreferences("ide_settings", MODE_PRIVATE)
                prefs.getString("settings", "") ?: ""
            } catch (e: Exception) { "" }
        }

        @JavascriptInterface
        fun saveSettings(json: String) {
            try {
                getSharedPreferences("ide_settings", MODE_PRIVATE).edit().putString("settings", json).apply()
            } catch (e: Exception) {}
        }

        @JavascriptInterface
        fun getDeviceInfo(): String {
            return try {
                "SM-X200 · Android ${android.os.Build.VERSION.RELEASE}"
            } catch (e: Exception) { "Android Device" }
        }

        @JavascriptInterface
        fun openInFiles(path: String) {
            runOnUiThread {
                val intent = Intent(this@CodeEditorActivity, FileManagerActivity::class.java)
                startActivity(intent)
            }
        }

        @JavascriptInterface
        fun shareFile(path: String) {
            runOnUiThread {
                try {
                    val file = File(path)
                    val uri = androidx.core.content.FileProvider.getUriForFile(this@CodeEditorActivity, "${packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "Share ${file.name}"))
                } catch (e: Exception) {
                    Toast.makeText(this@CodeEditorActivity, "Cannot share: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun showToast(message: String) {
            runOnUiThread { Toast.makeText(this@CodeEditorActivity, message, Toast.LENGTH_SHORT).show() }
        }

        // ── GitHub API bridge ──
        @JavascriptInterface
        fun githubApi(method: String, endpoint: String, body: String): String {
            return try {
                val prefs = getSharedPreferences("discordia_github", MODE_PRIVATE)
                val token = prefs.getString("token", "") ?: ""
                if (token.isEmpty()) return """{"error":"No GitHub token. Set it in the GitHub panel."}"""
                val url = URL("https://api.github.com$endpoint")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = method
                conn.setRequestProperty("Authorization", "token $token")
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.setRequestProperty("User-Agent", "DiscordiaTerminal/2.0")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 20000
                conn.readTimeout = 20000
                if (body.isNotEmpty() && method != "GET") {
                    conn.doOutput = true
                    val bytes = body.toByteArray()
                    conn.setRequestProperty("Content-Length", bytes.size.toString())
                    conn.outputStream.write(bytes)
                }
                val code = conn.responseCode
                val stream = if (code < 400) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.readText() ?: ""
                conn.disconnect()
                if (text.isEmpty()) """{"_code":$code,"ok":true}""" else text
            } catch (e: Exception) {
                """{"error":"${e.message?.replace("\"","'")}"}"""
            }
        }

        @JavascriptInterface
        fun getGitHubToken(): String =
            getSharedPreferences("discordia_github", MODE_PRIVATE).getString("token", "") ?: ""

        @JavascriptInterface
        fun saveGitHubToken(token: String) {
            getSharedPreferences("discordia_github", MODE_PRIVATE).edit().putString("token", token).apply()
            runOnUiThread { Toast.makeText(this@CodeEditorActivity, "GitHub token saved", Toast.LENGTH_SHORT).show() }
        }

        @JavascriptInterface
        fun getGitHubOwner(): String =
            getSharedPreferences("discordia_github", MODE_PRIVATE).getString("owner", "") ?: ""

        @JavascriptInterface
        fun saveGitHubOwner(owner: String) {
            getSharedPreferences("discordia_github", MODE_PRIVATE).edit().putString("owner", owner).apply()
        }

        // ── File system extras ──
        @JavascriptInterface
        fun createFolder(path: String): String {
            return try { File(path).mkdirs(); "ok" } catch (e: Exception) { e.message ?: "error" }
        }

        @JavascriptInterface
        fun deleteFileOrFolder(path: String): String {
            return try {
                val f = File(path)
                if (f.isDirectory) f.deleteRecursively() else f.delete()
                "ok"
            } catch (e: Exception) { e.message ?: "error" }
        }

        @JavascriptInterface
        fun renameFile(oldPath: String, newPath: String): String {
            return try { if (File(oldPath).renameTo(File(newPath))) "ok" else "rename failed" }
            catch (e: Exception) { e.message ?: "error" }
        }

        @JavascriptInterface
        fun getFileInfo(path: String): String {
            return try {
                val f = File(path)
                JSONObject().apply {
                    put("exists", f.exists()); put("size", f.length()); put("isDir", f.isDirectory)
                    put("canRead", f.canRead()); put("canWrite", f.canWrite())
                    put("modified", f.lastModified()); put("name", f.name); put("parent", f.parent ?: "")
                }.toString()
            } catch (e: Exception) { """{"error":"${e.message}"}""" }
        }

        @JavascriptInterface
        fun searchFiles(dir: String, query: String, caseSensitive: Boolean): String {
            return try {
                val flag = if (caseSensitive) "" else "-i"
                runCommand("""grep -rn $flag "${query.replace("\"", "\\\"")}" "$dir" --include="*.kt" --include="*.java" --include="*.py" --include="*.js" --include="*.ts" --include="*.html" --include="*.css" --include="*.json" --include="*.xml" --include="*.md" --include="*.txt" --include="*.sh" --include="*.gradle" 2>/dev/null | head -200""")
            } catch (e: Exception) { "" }
        }

        // ── Run file in terminal ──
        @JavascriptInterface
        fun runFile(path: String): String {
            val file = File(path)
            val ext = file.extension.lowercase()
            val cmd = when (ext) {
                "py" -> """python3 "$path" 2>&1"""
                "js", "mjs" -> """node "$path" 2>&1"""
                "sh", "bash" -> """bash "$path" 2>&1"""
                "rb" -> """ruby "$path" 2>&1"""
                "php" -> """php "$path" 2>&1"""
                "go" -> """go run "$path" 2>&1"""
                "kt" -> "echo 'Build with Gradle: ./gradlew run — or open terminal and use kotlinc'"
                "java" -> """cd "${file.parent}" && javac "${file.name}" 2>&1 && java "${file.nameWithoutExtension}" 2>&1"""
                "rs" -> """cd "${file.parent}" && rustc "${file.name}" 2>&1 && ./"${file.nameWithoutExtension}" 2>&1"""
                "pl" -> """perl "$path" 2>&1"""
                "lua" -> """lua "$path" 2>&1"""
                "r" -> """Rscript "$path" 2>&1"""
                else -> """echo 'No runner for .$ext — try: sh, py, js, rb, php, go, java, rs, pl, lua'"""
            }
            return try {
                val pb = ProcessBuilder("/system/bin/sh", "-c", cmd)
                    .directory(file.parentFile ?: File(currentDir))
                    .redirectErrorStream(true)
                pb.environment()["HOME"] = "/sdcard"
                pb.environment()["PATH"] = "/system/bin:/system/xbin:/sbin:/vendor/bin:${pb.environment()["PATH"] ?: ""}"
                val proc = pb.start()
                val killed = !proc.waitFor(30, TimeUnit.SECONDS)
                val out = BufferedReader(InputStreamReader(proc.inputStream)).readText().trimEnd()
                if (killed) { proc.destroyForcibly(); "$out\n[Process killed — 30s timeout]" } else out
            } catch (e: Exception) { "Error: ${e.message}" }
        }

        // ── GitHub file operations (push single file) ──
        @JavascriptInterface
        fun githubPushFile(repoFullName: String, filePath: String, content: String, message: String, sha: String): String {
            return try {
                val prefs = getSharedPreferences("discordia_github", MODE_PRIVATE)
                val token = prefs.getString("token", "") ?: ""
                if (token.isEmpty()) return """{"error":"No token"}"""
                val b64 = Base64.getEncoder().encodeToString(content.toByteArray())
                val body = JSONObject().apply {
                    put("message", message); put("content", b64)
                    if (sha.isNotEmpty()) put("sha", sha)
                }.toString()
                val url = URL("https://api.github.com/repos/$repoFullName/contents/$filePath")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "PUT"
                conn.setRequestProperty("Authorization", "token $token")
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.setRequestProperty("User-Agent", "DiscordiaTerminal/2.0")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 20000; conn.readTimeout = 20000; conn.doOutput = true
                val bytes = body.toByteArray()
                conn.setRequestProperty("Content-Length", bytes.size.toString())
                conn.outputStream.write(bytes)
                val code = conn.responseCode
                val stream = if (code < 400) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.readText() ?: ""
                conn.disconnect()
                text
            } catch (e: Exception) { """{"error":"${e.message}"}""" }
        }

        // ── pkg-like package checker ──
        @JavascriptInterface
        fun checkPackage(name: String): String {
            val paths = listOf("/system/bin", "/system/xbin", "/sbin", "/data/data/com.termux/files/usr/bin")
            val found = paths.any { File("$it/$name").exists() }
            return if (found) "installed" else "not_found"
        }

        @JavascriptInterface
        fun listInstalledPackages(): String {
            val pkgs = listOf("python3","node","git","bash","sh","curl","wget","zip","unzip","tar","grep","sed","awk","find","ssh","nc","nmap","php","ruby","perl","lua","go","rustc","javac")
            val paths = listOf("/system/bin", "/system/xbin", "/sbin", "/vendor/bin", "/data/data/com.termux/files/usr/bin")
            val arr = JSONArray()
            pkgs.forEach { pkg ->
                val found = paths.any { File("$it/$pkg").exists() }
                arr.put(JSONObject().put("name", pkg).put("installed", found))
            }
            return arr.toString()
        }
    }

    companion object {
        const val EXTRA_OPEN_FILE = "open_file"
        const val EXTRA_OPEN_DIR = "open_dir"
    }
}
