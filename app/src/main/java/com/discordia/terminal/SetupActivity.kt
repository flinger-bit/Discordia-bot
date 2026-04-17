package com.discordia.terminal

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Environment
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.discordia.terminal.databinding.ActivitySetupBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private val setupRoot: File = File(Environment.getExternalStorageDirectory(), "DiscordiaSetup")
    private var currentDir: File = setupRoot
    private val dirHistory = mutableListOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Setup Folder"

        binding.rvScripts.layoutManager = LinearLayoutManager(this)
        binding.tvOutput.typeface = Typeface.MONOSPACE
        ensureSetupFolder()
        loadDirectory(currentDir)
        setupButtons()
    }

    private fun ensureSetupFolder() {
        if (!setupRoot.exists()) {
            setupRoot.mkdirs()
            createExampleScripts()
        }
    }

    private fun createExampleScripts() {
        listOf("website", "security", "tools", "minecraft", "system", "custom", ".github/workflows").forEach {
            File(setupRoot, it).mkdirs()
        }

        File(setupRoot, "website/start-server.sh").apply {
            writeText("#!/bin/sh\nPORT=8080\nWEBDIR=\"/sdcard/DiscordiaSetup/website/public\"\nmkdir -p \"\$WEBDIR\"\necho \"Serving \$WEBDIR on port \$PORT\"\nbusybox httpd -f -p \$PORT -h \"\$WEBDIR\" 2>/dev/null || python3 -m http.server \$PORT --directory \"\$WEBDIR\" 2>/dev/null || echo \"No HTTP server found. Use Discordia Local Server instead.\"\n")
            setExecutable(true)
        }
        File(setupRoot, "website/public").mkdirs()
        File(setupRoot, "website/public/index.html").writeText("<!DOCTYPE html>\n<html>\n<head><title>My Site</title>\n<style>body{background:#0d1117;color:#58a6ff;font-family:monospace;padding:40px;}</style>\n</head>\n<body><h1>My Website</h1><p>Served from Samsung SM-X200 via Discordia Terminal.</p></body>\n</html>\n")

        File(setupRoot, "security/set-password.sh").apply {
            writeText("#!/bin/sh\nPASS_FILE=\"/sdcard/DiscordiaSetup/security/.passwords\"\nprintf 'Service name: '; read SERVICE\nprintf 'Password: '; read PASSWORD\nHASH=\$(echo -n \"\$PASSWORD\" | sha256sum 2>/dev/null | cut -d' ' -f1)\nif [ -z \"\$HASH\" ]; then HASH=\"\$(echo -n \"\$PASSWORD\" | md5sum | cut -d' ' -f1)\"; fi\necho \"\$SERVICE:\$HASH\" >> \"\$PASS_FILE\"\necho \"Password set for: \$SERVICE\"\n")
            setExecutable(true)
        }

        File(setupRoot, "tools/device-info.sh").apply {
            writeText("#!/bin/sh\necho '=== Discordia Device Info ==='\necho \"Model:   \$(getprop ro.product.model 2>/dev/null || echo SM-X200)\"\necho \"Android: \$(getprop ro.build.version.release 2>/dev/null || echo 14)\"\necho \"Storage: \$(df -h /sdcard 2>/dev/null | tail -1 | awk '{print \$2\" total, \"\$4\" free\"}')\"\necho \"IP:      \$(ip route get 1 2>/dev/null | awk '{print \$7}' | head -1)\"\necho '============================'\n")
            setExecutable(true)
        }

        File(setupRoot, "tools/install-python.sh").apply {
            writeText("#!/bin/sh\nif command -v python3 > /dev/null 2>&1; then\n  echo \"Python3: \$(python3 --version)\"\nelif command -v pkg > /dev/null 2>&1; then\n  echo 'Installing via Termux...'; pkg install python -y\nelse\n  echo 'Termux not found. Install from F-Droid then: pkg install python'\nfi\n")
            setExecutable(true)
        }

        File(setupRoot, "tools/hello.py").writeText("#!/usr/bin/env python3\nprint('Hello from Python on Samsung SM-X200!')\nimport sys\nprint(f'Python version: {sys.version}')\n")
        File(setupRoot, "tools/hello.js").writeText("#!/usr/bin/env node\nconsole.log('Hello from Node.js on Samsung SM-X200!');\nconsole.log('Node version:', process.version);\n")

        File(setupRoot, "minecraft/new-java-mod.sh").apply {
            writeText("#!/bin/sh\nMOD_NAME=\"\${1:-MyMod}\"\nOUT=\"/sdcard/DiscordiaProjects/minecraft-java/\$MOD_NAME\"\nmkdir -p \"\$OUT/src/main/java\"\nmkdir -p \"\$OUT/src/main/resources/META-INF\"\necho \"Mod '\$MOD_NAME' scaffolded at: \$OUT\"\necho 'Push to GitHub to build the JAR via GitHub Actions.'\n")
            setExecutable(true)
        }

        File(setupRoot, "system/cleanup.sh").apply {
            writeText("#!/bin/sh\necho 'Cleaning DiscordiaSetup temp files...'\nrm -rf /sdcard/DiscordiaSetup/.tmp/ 2>/dev/null\necho 'Disk usage:'\ndu -sh /sdcard/DiscordiaSetup/\n")
            setExecutable(true)
        }

        File(setupRoot, ".github/workflows/build.yml").writeText("name: Build\non:\n  push:\n    branches: [ main ]\n  workflow_dispatch:\njobs:\n  build:\n    runs-on: ubuntu-latest\n    steps:\n      - uses: actions/checkout@v4\n      - name: Build\n        run: echo 'Configure your build here!'\n")
        File(setupRoot, "README.txt").writeText("DISCORDIA SETUP FOLDER\n======================\nDevice: Samsung SM-X200 | Android 14\n\nPut scripts here and run them from the app.\nTap any file to run/view/edit/delete.\n\nSUPPORTED:\n  .sh  — Shell  |  .py — Python  |  .js — Node.js\n  .php — PHP    |  .rb — Ruby    |  .html — Opens in browser\n  .lua — Lua    |  .txt — View   |  any  — Execute\n\nFOLDERS:\n  website/    tools/    security/\n  minecraft/  system/   custom/\n  .github/workflows/ — Your GitHub Actions YAML files\n")
    }

    private fun loadDirectory(dir: File) {
        currentDir = dir
        val relPath = dir.absolutePath.removePrefix(setupRoot.absolutePath).ifEmpty { "/" }
        binding.tvPath.text = "DiscordiaSetup$relPath"
        binding.tvOutput.text = ""

        val entries = mutableListOf<File>()
        if (dir != setupRoot) entries.add(File(dir, ".."))
        val files = try {
            dir.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name }) ?: emptyList()
        } catch (_: Exception) { emptyList() }
        entries.addAll(files)

        binding.rvScripts.adapter = SetupAdapter(entries) { file ->
            if (file.name == "..") {
                if (dirHistory.isNotEmpty()) { currentDir = dirHistory.removeLast(); loadDirectory(currentDir) }
                else loadDirectory(setupRoot)
            } else if (file.isDirectory) {
                dirHistory.add(currentDir); loadDirectory(file)
            } else {
                showFileOptions(file)
            }
        }
        binding.tvFileCount.text = "${files.size} items"
    }

    private fun showFileOptions(file: File) {
        val ext = file.extension.lowercase()
        val isRunnable = ext in listOf("sh", "bash", "py", "js", "rb", "php", "pl", "lua")
        val isViewable = ext in listOf("txt", "md", "json", "xml", "toml", "ini", "cfg", "conf", "yml", "yaml", "csv", "log", "gradle", "kt", "java", "html", "htm", "css")
        val isHtml = ext in listOf("html", "htm")

        val options = buildList {
            if (isRunnable) add("▶ Run / Execute")
            if (isHtml) add("🌐 Open in Browser")
            if (isViewable) add("📄 View contents")
            add("✏ Edit in Terminal")
            add("🗑 Delete")
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (options[which]) {
                    "▶ Run / Execute" -> runScript(file)
                    "🌐 Open in Browser" -> openInWebView(file)
                    "📄 View contents" -> viewFileContents(file)
                    "✏ Edit in Terminal" -> Toast.makeText(this, "Open Terminal and type:\ncat ${file.absolutePath}", Toast.LENGTH_LONG).show()
                    "🗑 Delete" -> deleteFile(file)
                }
            }.show()
    }

    private fun openInWebView(file: File) {
        startActivity(Intent(this, WebViewActivity::class.java).apply {
            putExtra(WebViewActivity.EXTRA_FILE_PATH, file.absolutePath)
        })
    }

    private fun runScript(file: File) {
        binding.tvOutput.text = "▶ Running: ${file.name}\n"
        SoundManager.playClick()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { executeScript(file) }
            withContext(Dispatchers.Main) {
                binding.tvOutput.text = "▶ ${file.name}\n${"─".repeat(36)}\n$result\n${"─".repeat(36)}\n✅ Done"
                binding.scrollOutput.post { binding.scrollOutput.fullScroll(android.view.View.FOCUS_DOWN) }
                SoundManager.playSuccess()
            }
        }
    }

    private fun viewFileContents(file: File) {
        try {
            val content = file.readText()
            binding.tvOutput.text = "📄 ${file.name}\n${"─".repeat(36)}\n$content"
        } catch (e: Exception) {
            binding.tvOutput.text = "Cannot read: ${e.message}"
        }
    }

    private fun deleteFile(file: File) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${file.name}?")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
                if (ok) { loadDirectory(currentDir); Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show() }
                else Toast.makeText(this, "Could not delete", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun executeScript(file: File): String {
        val runner = when (file.extension.lowercase()) {
            "sh", "bash" -> arrayOf("/system/bin/sh", file.absolutePath)
            "py" -> arrayOf("python3", file.absolutePath).let { r ->
                if (runProcess(arrayOf("which", "python3")).contains("python3")) r
                else arrayOf("python", file.absolutePath)
            }
            "js" -> arrayOf("node", file.absolutePath)
            "rb" -> arrayOf("ruby", file.absolutePath)
            "php" -> arrayOf("php", file.absolutePath)
            "pl" -> arrayOf("perl", file.absolutePath)
            "lua" -> arrayOf("lua", file.absolutePath)
            else -> { file.setExecutable(true); arrayOf(file.absolutePath) }
        }
        return runProcess(runner, file.parentFile)
    }

    private fun runProcess(cmd: Array<String>, dir: File? = null): String {
        return try {
            val pb = ProcessBuilder(*cmd)
                .also { pb ->
                    if (dir != null) pb.directory(dir)
                    pb.redirectErrorStream(true)
                    pb.environment()["SETUP_DIR"] = setupRoot.absolutePath
                    pb.environment()["HOME"] = Environment.getExternalStorageDirectory().absolutePath
                }
            val proc = pb.start()
            proc.waitFor(30, TimeUnit.SECONDS)
            proc.inputStream.bufferedReader().readText().trim().ifEmpty { "(no output)" }
        } catch (e: Exception) {
            "Error: ${e.message}\n\nHint: Make sure the runtime is installed (e.g. python3, node)."
        }
    }

    private fun setupButtons() {
        binding.btnNewScript.setOnClickListener { showNewScriptDialog() }
        binding.btnNewFolder.setOnClickListener { showNewFolderDialog() }
        binding.btnHome.setOnClickListener { dirHistory.clear(); loadDirectory(setupRoot) }
        binding.btnClearOutput.setOnClickListener { binding.tvOutput.text = "" }
    }

    private fun showNewScriptDialog() {
        val templates = arrayOf("Shell (.sh)", "Python (.py)", "JavaScript (.js)", "PHP (.php)", "HTML page (.html)", "Config (.txt/.yml)")
        AlertDialog.Builder(this)
            .setTitle("New file in ${currentDir.name}/")
            .setItems(templates) { _, which ->
                val (ext, content) = when (which) {
                    0 -> "sh" to "#!/bin/sh\n# My script\necho 'Hello from Discordia!'\n"
                    1 -> "py" to "#!/usr/bin/env python3\nprint('Hello from Discordia!')\n"
                    2 -> "js" to "#!/usr/bin/env node\nconsole.log('Hello from Discordia!');\n"
                    3 -> "php" to "<?php\necho 'Hello from Discordia!' . PHP_EOL;\n"
                    4 -> "html" to "<!DOCTYPE html>\n<html>\n<head><title>My Page</title>\n<style>body{background:#0d1117;color:#58a6ff;font-family:monospace;padding:40px;}</style>\n</head>\n<body><h1>My Page</h1><p>Created with Discordia Terminal.</p></body>\n</html>\n"
                    5 -> "yml" to "# My configuration\n\nkey: value\n"
                    else -> "txt" to ""
                }
                promptFileName(ext, content)
            }.show()
    }

    private fun promptFileName(ext: String, defaultContent: String) {
        val input = android.widget.EditText(this).apply {
            hint = "filename.$ext"
            setTextColor(resources.getColor(R.color.text_primary, null))
        }
        AlertDialog.Builder(this).setTitle("File name").setView(input)
            .setPositiveButton("Create") { _, _ ->
                var name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                if (!name.contains('.')) name = "$name.$ext"
                val file = File(currentDir, name)
                try {
                    file.createNewFile()
                    if (defaultContent.isNotEmpty()) file.writeText(defaultContent)
                    if (name.endsWith(".sh")) file.setExecutable(true)
                    loadDirectory(currentDir)
                    Toast.makeText(this, "Created: $name", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showNewFolderDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "folder-name"
            setTextColor(resources.getColor(R.color.text_primary, null))
        }
        AlertDialog.Builder(this).setTitle("New folder").setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty() && File(currentDir, name).mkdirs()) {
                    loadDirectory(currentDir)
                    Toast.makeText(this, "Created: $name/", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    override fun onBackPressed() {
        if (dirHistory.isNotEmpty()) { currentDir = dirHistory.removeLast(); loadDirectory(currentDir) }
        else super.onBackPressed()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
