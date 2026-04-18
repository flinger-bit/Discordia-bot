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
    }

    companion object {
        const val EXTRA_OPEN_FILE = "open_file"
        const val EXTRA_OPEN_DIR = "open_dir"
    }
}
