package com.discordia.terminal

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
        // Create folder structure
        val dirs = listOf("website", "security", "tools", "minecraft", "system", "custom")
        dirs.forEach { File(setupRoot, it).mkdirs() }

        // Website setup script
        File(setupRoot, "website/start-server.sh").writeText("""#!/bin/sh
# Start a local HTTP server on port 8080
# Serves files from DiscordiaSetup/website/public/

PORT=8080
WEBDIR="${'$'}{SETUP_DIR:-/sdcard/DiscordiaSetup/website}/public"
mkdir -p "${'$'}WEBDIR"

# Create index.html if it doesn't exist
if [ ! -f "${'$'}WEBDIR/index.html" ]; then
cat > "${'$'}WEBDIR/index.html" << 'HTML'
<!DOCTYPE html>
<html><head><title>My Site</title></head>
<body><h1>My Website</h1><p>Served from Samsung SM-X200.</p></body>
</html>
HTML
fi

echo "Serving ${'$'}WEBDIR on port ${'$'}PORT"
echo "Open: http://localhost:${'$'}PORT"
# Use Discordia's built-in server or busybox httpd
busybox httpd -f -p ${'$'}PORT -h "${'$'}WEBDIR" 2>/dev/null || \
  python3 -m http.server ${'$'}PORT --directory "${'$'}WEBDIR" 2>/dev/null || \
  echo "No HTTP server found. Use Discordia Local Server instead."
""")

        // Password setup script
        File(setupRoot, "security/set-password.sh").writeText("""#!/bin/sh
# Create or update a password file
# Passwords are stored hashed (SHA-256) in /sdcard/DiscordiaSetup/security/.passwords

PASS_FILE="/sdcard/DiscordiaSetup/security/.passwords"
echo "Set a password for which service? (e.g. wifi, server, admin)"
echo -n "Service name: "
read SERVICE
echo -n "New password: "
read -s PASSWORD
HASH=$(echo -n "${'$'}PASSWORD" | sha256sum | cut -d' ' -f1)
echo "${'$'}SERVICE:${'$'}HASH" >> "${'$'}PASS_FILE"
echo ""
echo "Password set for: ${'$'}SERVICE"
echo "Hash stored in: ${'$'}PASS_FILE"
""")

        // Package installer helper
        File(setupRoot, "tools/install-python.sh").writeText("""#!/bin/sh
# Install Python via Termux (if available) or check system
echo "Checking for Python..."
if command -v python3 > /dev/null 2>&1; then
  echo "Python3 already available: $(python3 --version)"
elif command -v pkg > /dev/null 2>&1; then
  echo "Installing Python via Termux..."
  pkg install python -y
else
  echo "Termux not found. Python not available on this system."
  echo "Tip: Install Termux from F-Droid, then run: pkg install python"
fi
""")

        File(setupRoot, "tools/install-nodejs.sh").writeText("""#!/bin/sh
# Install Node.js via Termux (if available)
echo "Checking for Node.js..."
if command -v node > /dev/null 2>&1; then
  echo "Node.js already available: $(node --version)"
elif command -v pkg > /dev/null 2>&1; then
  echo "Installing Node.js via Termux..."
  pkg install nodejs -y
else
  echo "Termux not found. Node.js not available on this system."
  echo "Tip: Install Termux from F-Droid, then run: pkg install nodejs"
fi
""")

        File(setupRoot, "tools/download-file.sh").writeText("""#!/bin/sh
# Download a file from the internet
# Usage: set URL and DEST below, then run

URL="https://example.com/file.zip"
DEST="/sdcard/Downloads/file.zip"

echo "Downloading: ${'$'}URL"
echo "Saving to: ${'$'}DEST"
curl -L -o "${'$'}DEST" "${'$'}URL" && echo "Done!" || wget -O "${'$'}DEST" "${'$'}URL" && echo "Done!" || echo "No downloader found."
""")

        // Minecraft setup
        File(setupRoot, "minecraft/new-java-mod.sh").writeText("""#!/bin/sh
# Create a new Minecraft Java mod skeleton
# Requires: name and package set below

MOD_NAME="${'$'}{1:-MyMod}"
PACKAGE="com.example.$(echo ${'$'}MOD_NAME | tr '[:upper:]' '[:lower:]')"
OUT_DIR="/sdcard/DiscordiaProjects/minecraft-java/${'$'}MOD_NAME"

mkdir -p "${'$'}OUT_DIR/src/main/java/$(echo ${'$'}PACKAGE | tr '.' '/')"
mkdir -p "${'$'}OUT_DIR/src/main/resources/META-INF"

echo "Mod: ${'$'}MOD_NAME"
echo "Package: ${'$'}PACKAGE"
echo "Location: ${'$'}OUT_DIR"
echo ""
echo "Files created. Push to GitHub + use Actions to build."
echo "See: https://github.com/flinger-bit/Discordia-bot/actions"
""")

        File(setupRoot, "minecraft/new-bedrock-addon.sh").writeText("""#!/bin/sh
# Create a new Minecraft Bedrock addon
ADDON_NAME="${'$'}{1:-MyAddon}"
OUT_DIR="/sdcard/DiscordiaProjects/minecraft-bedrock/${'$'}ADDON_NAME"
mkdir -p "${'$'}OUT_DIR/behavior_pack"
mkdir -p "${'$'}OUT_DIR/resource_pack"
echo "Bedrock addon '${'$'}ADDON_NAME' created at: ${'$'}OUT_DIR"
echo "Edit manifest.json files in each pack folder."
""")

        // System info
        File(setupRoot, "system/device-info.sh").writeText("""#!/bin/sh
# Show device information
echo "=== Discordia System Info ==="
echo "Device:    $(getprop ro.product.model 2>/dev/null || echo 'Unknown')"
echo "Android:   $(getprop ro.build.version.release 2>/dev/null || echo 'Unknown')"
echo "Kernel:    $(uname -r 2>/dev/null || echo 'Unknown')"
echo "CPU:       $(cat /proc/cpuinfo 2>/dev/null | grep 'Hardware' | head -1 | cut -d: -f2 || echo 'Unknown')"
echo "Storage:   $(df -h /sdcard 2>/dev/null | tail -1 | awk '{print $2" total, "$4" free"}' || echo 'Unknown')"
echo "RAM:       $(cat /proc/meminfo 2>/dev/null | grep MemTotal | awk '{print $2" kB"}' || echo 'Unknown')"
echo "IP:        $(ip route get 1 2>/dev/null | awk '{print $7}' || ifconfig 2>/dev/null | grep 'inet ' | head -1 | awk '{print $2}' || echo 'Unknown')"
echo "==========================="
""")

        File(setupRoot, "system/cleanup.sh").writeText("""#!/bin/sh
# Clean up temp files and caches
echo "Cleaning up..."
rm -rf /sdcard/DiscordiaSetup/.tmp/ 2>/dev/null
echo "Cleared temp files."
echo "Disk usage:"
du -sh /sdcard/DiscordiaSetup/
""")

        // README
        File(setupRoot, "README.txt").writeText("""DISCORDIA SETUP FOLDER
======================
Device: Samsung SM-X200 | Android 14

This folder is your personal configuration hub.
Put any script, config file, or resource here and run it from the Discordia Setup screen.

FOLDER STRUCTURE:
  website/     — Scripts to start web servers, manage sites
  security/    — Password management, key generation
  tools/       — Install helpers (Python, Node.js, etc.)
  minecraft/   — Mod and addon creation helpers
  system/      — Device info, cleanup, configuration
  custom/      — Your own scripts and files

SUPPORTED SCRIPT TYPES:
  .sh   — Shell script (runs via /system/bin/sh)
  .py   — Python script (requires Python installed)
  .js   — JavaScript/Node.js script (requires Node)
  .rb   — Ruby script
  .php  — PHP script
  .pl   — Perl script
  .lua  — Lua script

Any file type can be placed here. Scripts are executed
by tapping them in the Setup screen.

Tip: Create a 'custom/' subfolder with your own
configuration scripts for repeatable setup tasks!
""")
    }

    private fun loadDirectory(dir: File) {
        currentDir = dir
        val relPath = dir.absolutePath.removePrefix(setupRoot.absolutePath).ifEmpty { "/" }
        binding.tvPath.text = "DiscordiaSetup$relPath"
        binding.tvOutput.text = ""

        val entries = mutableListOf<File>()
        if (dir != setupRoot) entries.add(File(dir, ".."))
        val files = dir.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name }) ?: emptyList()
        entries.addAll(files)

        binding.rvScripts.adapter = SetupAdapter(entries) { file ->
            if (file.name == "..") {
                if (dirHistory.isNotEmpty()) {
                    currentDir = dirHistory.removeLast()
                    loadDirectory(currentDir)
                } else {
                    loadDirectory(setupRoot)
                }
            } else if (file.isDirectory) {
                dirHistory.add(currentDir)
                loadDirectory(file)
            } else {
                showFileOptions(file)
            }
        }
        binding.tvFileCount.text = "${files.size} items"
    }

    private fun showFileOptions(file: File) {
        val options = arrayOf("▶ Run / Execute", "📄 View contents", "✏ Edit in Terminal", "🗑 Delete")
        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> runScript(file)
                    1 -> viewFileContents(file)
                    2 -> openInTerminal(file)
                    3 -> deleteFile(file)
                }
            }.show()
    }

    private fun runScript(file: File) {
        binding.tvOutput.text = "▶ Running: ${file.name}\n\n"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { executeScript(file) }
            withContext(Dispatchers.Main) {
                binding.tvOutput.text = "▶ ${file.name}\n${"─".repeat(40)}\n$result\n${"─".repeat(40)}\n✅ Finished"
                binding.scrollOutput.post { binding.scrollOutput.fullScroll(android.view.View.FOCUS_DOWN) }
            }
        }
    }

    private fun viewFileContents(file: File) {
        try {
            val content = file.readText()
            binding.tvOutput.text = "📄 ${file.name}\n${"─".repeat(40)}\n$content"
        } catch (e: Exception) {
            binding.tvOutput.text = "Cannot read file: ${e.message}"
        }
    }

    private fun openInTerminal(file: File) {
        Toast.makeText(this, "Tip: open Terminal and type:\nsh ${file.absolutePath}", Toast.LENGTH_LONG).show()
    }

    private fun deleteFile(file: File) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${file.name}?")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
                if (deleted) {
                    loadDirectory(currentDir)
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Could not delete", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun executeScript(file: File): String {
        val runner = when (file.extension.lowercase()) {
            "sh", "bash" -> arrayOf("/system/bin/sh", file.absolutePath)
            "py" -> arrayOf("python3", file.absolutePath)
            "js" -> arrayOf("node", file.absolutePath)
            "rb" -> arrayOf("ruby", file.absolutePath)
            "php" -> arrayOf("php", file.absolutePath)
            "pl" -> arrayOf("perl", file.absolutePath)
            "lua" -> arrayOf("lua", file.absolutePath)
            else -> {
                file.setExecutable(true)
                arrayOf(file.absolutePath)
            }
        }
        return try {
            val process = ProcessBuilder(*runner)
                .directory(file.parentFile)
                .redirectErrorStream(true)
                .also { pb ->
                    pb.environment()["SETUP_DIR"] = setupRoot.absolutePath
                    pb.environment()["SCRIPT_DIR"] = file.parent ?: setupRoot.absolutePath
                    pb.environment()["HOME"] = Environment.getExternalStorageDirectory().absolutePath
                }
                .start()
            process.waitFor(30, TimeUnit.SECONDS)
            process.inputStream.bufferedReader().readText().trim().ifEmpty { "(no output)" }
        } catch (e: Exception) {
            "Error running script: ${e.message}\n\nMake sure the required runtime is installed."
        }
    }

    private fun setupButtons() {
        binding.btnNewScript.setOnClickListener { showNewScriptDialog() }
        binding.btnNewFolder.setOnClickListener { showNewFolderDialog() }
        binding.btnHome.setOnClickListener {
            dirHistory.clear()
            loadDirectory(setupRoot)
        }
        binding.btnClearOutput.setOnClickListener { binding.tvOutput.text = "" }
    }

    private fun showNewScriptDialog() {
        val templates = arrayOf(
            "Shell script (.sh)",
            "Python script (.py)",
            "JavaScript / Node.js (.js)",
            "PHP script (.php)",
            "Plain text / config (.txt)",
            "Empty file (custom name)"
        )
        AlertDialog.Builder(this)
            .setTitle("New Script in: ${currentDir.name}/")
            .setItems(templates) { _, which ->
                val (ext, content) = when (which) {
                    0 -> Pair("sh", "#!/bin/sh\n# My shell script\necho 'Hello from Discordia!'\n")
                    1 -> Pair("py", "#!/usr/bin/env python3\n# My Python script\nprint('Hello from Discordia!')\n")
                    2 -> Pair("js", "#!/usr/bin/env node\n// My Node.js script\nconsole.log('Hello from Discordia!');\n")
                    3 -> Pair("php", "<?php\n// My PHP script\necho 'Hello from Discordia!' . PHP_EOL;\n")
                    4 -> Pair("txt", "# My configuration\n\n")
                    else -> Pair("", "")
                }
                promptFileName(ext, content)
            }.show()
    }

    private fun promptFileName(ext: String, defaultContent: String) {
        val input = android.widget.EditText(this).apply {
            hint = if (ext.isEmpty()) "filename.ext" else "script.$ext"
            setTextColor(resources.getColor(R.color.text_primary, null))
        }
        AlertDialog.Builder(this)
            .setTitle("File name")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                var name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                if (ext.isNotEmpty() && !name.contains('.')) name = "$name.$ext"
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
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNewFolderDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "folder-name"
            setTextColor(resources.getColor(R.color.text_primary, null))
        }
        AlertDialog.Builder(this)
            .setTitle("New folder in ${currentDir.name}/")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                val dir = File(currentDir, name)
                if (dir.mkdirs()) {
                    loadDirectory(currentDir)
                    Toast.makeText(this, "Folder created: $name", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Could not create folder", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onBackPressed() {
        if (dirHistory.isNotEmpty()) {
            currentDir = dirHistory.removeLast()
            loadDirectory(currentDir)
        } else {
            super.onBackPressed()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
