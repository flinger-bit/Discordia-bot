package com.discordia.terminal

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.discordia.terminal.databinding.ActivityWorkflowBuilderBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class WorkflowBuilderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorkflowBuilderBinding
    private lateinit var github: GitHubManager
    private var selectedTemplate = 0
    private var monitorJob: kotlinx.coroutines.Job? = null
    private var lastRunId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkflowBuilderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Workflow Builder"

        github = GitHubManager(this)
        binding.tvYaml.typeface = Typeface.MONOSPACE

        setupCredentials()
        setupTemplateSpinner()
        setupButtons()
        loadSavedSettings()
    }

    private fun loadSavedSettings() {
        binding.etOwner.setText(github.getOwner())
        binding.etRepo.setText(github.getRepo())
        if (github.getToken().isNotEmpty()) {
            binding.etToken.setText("••••••••••••••••")
        }
    }

    private fun setupCredentials() {
        binding.btnSaveCredentials.setOnClickListener {
            val token = binding.etToken.text.toString().trim().let {
                if (it.contains("•")) github.getToken() else it
            }
            val owner = binding.etOwner.text.toString().trim()
            val repo = binding.etRepo.text.toString().trim()
            if (token.isEmpty() || owner.isEmpty() || repo.isEmpty()) {
                showStatus("❌ Fill in GitHub Token, Owner, and Repo", error = true)
                return@setOnClickListener
            }
            github.setToken(token)
            github.setOwner(owner)
            github.setRepo(repo)
            lifecycleScope.launch {
                showStatus("Verifying token...")
                val (ok, msg) = github.validateToken()
                showStatus(if (ok) "✅ $msg | Repo: $owner/$repo" else "❌ $msg", error = !ok)
                if (ok) SoundManager.playSuccess() else SoundManager.playError()
            }
        }
    }

    private fun setupTemplateSpinner() {
        val templates = TEMPLATES.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, templates)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTemplate.adapter = adapter
        binding.spinnerTemplate.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                selectedTemplate = pos
                binding.tvYaml.setText(TEMPLATES[pos].yaml)
                binding.tvDescription.text = TEMPLATES[pos].description
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        binding.btnPushAndBuild.setOnClickListener { pushAndBuild() }
        binding.btnCheckStatus.setOnClickListener { checkStatus() }
        binding.btnDownloadArtifacts.setOnClickListener { downloadArtifacts() }
        binding.btnSaveLocal.setOnClickListener { saveWorkflowLocally() }
        binding.btnNewRepo.setOnClickListener { showCreateRepoDialog() }
    }

    private fun pushAndBuild() {
        val workflowName = binding.etWorkflowName.text.toString().trim().ifEmpty { "build.yml" }
        val yaml = binding.tvYaml.text.toString()
        if (yaml.isEmpty()) { showStatus("❌ YAML is empty", error = true); return }
        if (github.getToken().isEmpty()) { showStatus("❌ Save credentials first", error = true); return }

        binding.btnPushAndBuild.isEnabled = false
        showStatus("Pushing workflow to GitHub...", loading = true)
        SoundManager.playClick()

        lifecycleScope.launch {
            val filePath = ".github/workflows/$workflowName"
            val result = github.pushMultipleFiles(
                mapOf(filePath to yaml),
                "workflow: update $workflowName via Discordia Terminal"
            )
            if (result.isSuccess) {
                showStatus("✅ Pushed $filePath\nCommit: ${result.getOrNull()?.take(8)}\n\nTriggering build...", loading = true)
                delay(2000)
                val triggered = github.triggerWorkflow(workflowName)
                if (triggered) {
                    showStatus("🚀 Build triggered! Monitoring runs...", loading = true)
                    SoundManager.playBuild()
                    startMonitoring()
                } else {
                    showStatus("⚠️ Pushed but couldn't trigger workflow_dispatch.\nCheck your workflow has 'workflow_dispatch' trigger.\nBuild may have started automatically on push.")
                    startMonitoring()
                }
            } else {
                showStatus("❌ Push failed: ${result.exceptionOrNull()?.message}", error = true)
                SoundManager.playError()
            }
            binding.btnPushAndBuild.isEnabled = true
        }
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = lifecycleScope.launch {
            var attempts = 0
            while (isActive && attempts < 60) {
                delay(5000)
                attempts++
                val runs = github.getWorkflowRuns(3)
                if (runs.isNotEmpty()) {
                    val latest = runs[0]
                    lastRunId = latest.id
                    val icon = when (latest.conclusion) {
                        "success" -> "✅"
                        "failure" -> "❌"
                        "cancelled" -> "⛔"
                        null -> if (latest.status == "in_progress") "⏳" else "🕐"
                        else -> "•"
                    }
                    val statusText = buildString {
                        appendLine("$icon Run #${latest.runNumber} | ${latest.status} | ${latest.conclusion ?: "running"}")
                        appendLine("Started: ${latest.createdAt.replace("T", " ").replace("Z", "")}")
                        appendLine("URL: ${latest.htmlUrl}")
                        appendLine()
                        runs.drop(1).forEach { r ->
                            appendLine("• Run #${r.runNumber} | ${r.status} | ${r.conclusion ?: "running"}")
                        }
                    }
                    withContext(Dispatchers.Main) {
                        showStatus(statusText.trim())
                        if (latest.conclusion == "success") {
                            SoundManager.playSuccess()
                            binding.btnDownloadArtifacts.isEnabled = true
                            return@withContext
                        } else if (latest.conclusion == "failure" || latest.conclusion == "cancelled") {
                            SoundManager.playError()
                            return@withContext
                        }
                    }
                    if (latest.conclusion != null) break
                }
            }
            withContext(Dispatchers.Main) { binding.btnPushAndBuild.isEnabled = true }
        }
    }

    private fun checkStatus() {
        lifecycleScope.launch {
            showStatus("Checking runs...", loading = true)
            val runs = github.getWorkflowRuns(5)
            if (runs.isEmpty()) {
                showStatus("No workflow runs found. Push a workflow first.", error = false)
                return@launch
            }
            lastRunId = runs[0].id
            val text = runs.joinToString("\n") { r ->
                val icon = when (r.conclusion) {
                    "success" -> "✅"; "failure" -> "❌"; null -> "⏳"; else -> "•"
                }
                "$icon Run #${r.runNumber} | ${r.status} | ${r.conclusion ?: "running"} | ${r.createdAt.take(16)}"
            }
            showStatus(text)
            if (runs[0].conclusion == "success") binding.btnDownloadArtifacts.isEnabled = true
        }
    }

    private fun downloadArtifacts() {
        if (lastRunId == -1L) {
            checkStatus()
            return
        }
        lifecycleScope.launch {
            showStatus("Fetching artifacts for run #$lastRunId...", loading = true)
            val artifacts = github.getRunArtifacts(lastRunId)
            if (artifacts.isEmpty()) {
                showStatus("No artifacts found for this run.")
                return@launch
            }
            val destDir = File("/sdcard/DiscordiaBuilds")
            var downloaded = 0
            val log = StringBuilder("📦 Artifacts from run #$lastRunId:\n\n")
            for (artifact in artifacts) {
                log.appendLine("Downloading ${artifact.name} (${artifact.sizeBytes / 1024}KB)...")
                withContext(Dispatchers.Main) { showStatus(log.toString(), loading = true) }
                val file = github.downloadArtifact(artifact, destDir)
                if (file != null) {
                    log.appendLine("✅ Saved: ${file.absolutePath}")
                    downloaded++
                } else {
                    log.appendLine("❌ Failed: ${artifact.name}")
                }
            }
            log.appendLine("\n$downloaded/${artifacts.size} artifacts downloaded to /sdcard/DiscordiaBuilds/")
            showStatus(log.toString())
            if (downloaded > 0) SoundManager.playSuccess() else SoundManager.playError()
        }
    }

    private fun saveWorkflowLocally() {
        val name = binding.etWorkflowName.text.toString().trim().ifEmpty { "build.yml" }
        val yaml = binding.tvYaml.text.toString()
        val dir = File("/sdcard/DiscordiaSetup/.github/workflows")
        dir.mkdirs()
        File(dir, name).writeText(yaml)
        Toast.makeText(this, "Saved to DiscordiaSetup/.github/workflows/$name", Toast.LENGTH_LONG).show()
        SoundManager.playClick()
    }

    private fun showCreateRepoDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "new-repo-name"
            setTextColor(resources.getColor(R.color.text_primary, null))
        }
        AlertDialog.Builder(this)
            .setTitle("Create GitHub Repository")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    showStatus("Creating repository: $name...", loading = true)
                    val ok = github.createRepo(name)
                    showStatus(if (ok) "✅ Repo created: ${github.getOwner()}/$name\nSet it as your repo above." else "❌ Failed to create repo")
                    if (ok) {
                        github.setRepo(name)
                        withContext(Dispatchers.Main) { binding.etRepo.setText(name) }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showStatus(msg: String, error: Boolean = false, loading: Boolean = false) {
        binding.tvStatus.text = msg
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.tvStatus.setTextColor(
            resources.getColor(if (error) R.color.accent_red else R.color.terminal_text, null)
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        monitorJob?.cancel()
    }

    data class WorkflowTemplate(val name: String, val description: String, val yaml: String)

    companion object {
        val TEMPLATES = listOf(
            WorkflowTemplate("Android APK (Debug + Release)",
                "Builds Android APK with Gradle. Output: app-debug.apk + app-release-unsigned.apk",
"""name: Build Android APK

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - uses: gradle/actions/setup-gradle@v3
        with:
          gradle-version: '8.7'
      - name: Download wrapper JAR
        run: mkdir -p gradle/wrapper && curl -L -o gradle/wrapper/gradle-wrapper.jar https://github.com/gradle/gradle/raw/v8.7.0/gradle/wrapper/gradle-wrapper.jar
      - name: Build APK
        run: gradle assembleDebug assembleRelease || gradle assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: android-apk
          path: app/build/outputs/apk/
"""),

            WorkflowTemplate("Python App → EXE (Windows)",
                "Packages a Python app into a standalone Windows EXE using PyInstaller",
"""name: Build Python EXE

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: '3.11'
      - name: Install dependencies
        run: |
          pip install pyinstaller
          pip install -r requirements.txt 2>nul || true
      - name: Build EXE
        run: pyinstaller --onefile --windowed main.py
      - uses: actions/upload-artifact@v4
        with:
          name: windows-exe
          path: dist/*.exe
"""),

            WorkflowTemplate("Node.js App → EXE (all platforms)",
                "Packages a Node.js app into executables for Windows, Linux, and macOS using pkg",
"""name: Build Node.js Executables

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
      - name: Install + build
        run: |
          npm install 2>/dev/null || true
          npm install -g pkg
          pkg . --targets node20-linux-x64,node20-win-x64,node20-macos-x64 --output dist/app
      - uses: actions/upload-artifact@v4
        with:
          name: node-executables
          path: dist/
"""),

            WorkflowTemplate("C/C++ → Windows EXE (MinGW cross-compile)",
                "Cross-compiles C/C++ code to a Windows .exe from Linux using MinGW",
"""name: Build C++ Windows EXE

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Install MinGW
        run: sudo apt-get install -y gcc-mingw-w64 g++-mingw-w64
      - name: Compile EXE
        run: |
          # Compile all .c/.cpp files, or edit this line
          x86_64-w64-mingw32-gcc -o output.exe main.c 2>/dev/null || \
          x86_64-w64-mingw32-g++ -o output.exe main.cpp 2>/dev/null || \
          x86_64-w64-mingw32-g++ -o output.exe $(find . -name '*.cpp' | head -20) -static
      - uses: actions/upload-artifact@v4
        with:
          name: windows-exe
          path: "*.exe"
"""),

            WorkflowTemplate("Minecraft Java Mod (Forge)",
                "Builds a Minecraft Java mod using Gradle + ForgeGradle",
"""name: Build Minecraft Java Mod

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - uses: gradle/actions/setup-gradle@v3
        with:
          gradle-version: '8.7'
      - name: Build mod JAR
        run: gradle build --no-daemon
      - uses: actions/upload-artifact@v4
        with:
          name: minecraft-mod-jar
          path: build/libs/*.jar
"""),

            WorkflowTemplate("Minecraft Bedrock Addon (.mcaddon)",
                "Packages behavior + resource packs into a .mcaddon file",
"""name: Build Minecraft Bedrock Addon

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Package addon
        run: |
          mkdir -p dist
          ADDON_NAME=$(basename "$PWD")
          zip -r "dist/${ADDON_NAME}.mcaddon" behavior_pack/ resource_pack/ 2>/dev/null || \
          zip -r "dist/${ADDON_NAME}.mcpack" . --exclude dist/\* .git/\*
          ls -la dist/
      - uses: actions/upload-artifact@v4
        with:
          name: bedrock-addon
          path: dist/
"""),

            WorkflowTemplate("Web App → Static Site (HTML/CSS/JS)",
                "Deploys a static HTML/CSS/JS site to GitHub Pages",
"""name: Deploy Web App

on:
  push:
    branches: [ main ]
  workflow_dispatch:

permissions:
  contents: write
  pages: write
  id-token: write

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Build (optional: npm build)
        run: |
          if [ -f package.json ]; then
            npm install && npm run build 2>/dev/null || npm install
          fi
      - name: Package site
        run: |
          mkdir -p dist
          cp -r *.html *.css *.js assets/ dist/ 2>/dev/null || true
          cp -r public/* dist/ 2>/dev/null || true
          cp -r build/* dist/ 2>/dev/null || true
          ls dist/
      - uses: actions/upload-artifact@v4
        with:
          name: web-app
          path: dist/
"""),

            WorkflowTemplate("FFmpeg Build (full codec library)",
                "Compiles FFmpeg with all codecs enabled. Output: ffmpeg binary + libs",
"""name: Build FFmpeg

on:
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Install dependencies
        run: |
          sudo apt-get update
          sudo apt-get install -y build-essential yasm nasm libx264-dev libvpx-dev libopus-dev libmp3lame-dev libfdk-aac-dev
      - name: Clone FFmpeg
        run: git clone --depth 1 https://github.com/FFmpeg/FFmpeg.git ffmpeg-src
      - name: Configure FFmpeg
        run: |
          cd ffmpeg-src
          ./configure --enable-gpl --enable-nonfree --enable-libx264 --enable-libvpx --enable-libopus --enable-libmp3lame --prefix=$PWD/../ffmpeg-out
      - name: Build FFmpeg
        run: cd ffmpeg-src && make -j$(nproc) && make install
      - uses: actions/upload-artifact@v4
        with:
          name: ffmpeg-build
          path: ffmpeg-out/
"""),

            WorkflowTemplate("Docker Image → Container",
                "Builds a Docker container image and saves it as a .tar artifact",
"""name: Build Docker Image

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Build Docker image
        run: docker build -t myapp:latest .
      - name: Save image
        run: docker save myapp:latest | gzip > myapp-docker.tar.gz
      - uses: actions/upload-artifact@v4
        with:
          name: docker-image
          path: myapp-docker.tar.gz
"""),

            WorkflowTemplate("Rust → Binary (Linux + Windows)",
                "Compiles a Rust project for Linux (native) and Windows (cross-compile)",
"""name: Build Rust Binaries

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build-linux:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: dtolnay/rust-toolchain@stable
      - run: cargo build --release
      - uses: actions/upload-artifact@v4
        with:
          name: linux-binary
          path: target/release/

  build-windows:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: dtolnay/rust-toolchain@stable
        with:
          targets: x86_64-pc-windows-gnu
      - run: sudo apt-get install -y gcc-mingw-w64 && cargo build --release --target x86_64-pc-windows-gnu
      - uses: actions/upload-artifact@v4
        with:
          name: windows-binary
          path: target/x86_64-pc-windows-gnu/release/*.exe
"""),

            WorkflowTemplate("Custom (blank template)",
                "Blank workflow — write your own YAML from scratch",
"""name: Custom Build

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      # Add your steps here:
      - name: My custom step
        run: |
          echo "Hello from Discordia Terminal!"
          echo "Add your commands here."

      - uses: actions/upload-artifact@v4
        with:
          name: output
          path: dist/
""")
        )
    }
}
