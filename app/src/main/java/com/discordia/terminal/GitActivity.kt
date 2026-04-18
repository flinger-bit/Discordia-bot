package com.discordia.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.discordia.terminal.databinding.ActivityGitBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class GitActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGitBinding
    private lateinit var github: GitHubManager
    private var workingDir = "/sdcard"
    private val outputLog = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGitBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Source Control"

        github = GitHubManager(this)
        workingDir = intent.getStringExtra("working_dir") ?: "/sdcard"

        binding.tvLog.typeface = Typeface.MONOSPACE
        binding.tvDiff.typeface = Typeface.MONOSPACE

        setupButtons()
        refresh()
    }

    private fun setupButtons() {
        binding.btnRefresh.setOnClickListener { refresh() }
        binding.btnStageAll.setOnClickListener { stageAll() }
        binding.btnUnstageAll.setOnClickListener { unstageAll() }
        binding.btnCommit.setOnClickListener { doCommit() }
        binding.btnPush.setOnClickListener { doPush() }
        binding.btnPull.setOnClickListener { doPull() }
        binding.btnCommitPush.setOnClickListener { doCommitAndPush() }
        binding.btnInit.setOnClickListener { doInit() }
        binding.btnClone.setOnClickListener { doCloneDialog() }
        binding.btnLog.setOnClickListener { showGitLog() }
        binding.btnDiff.setOnClickListener { showDiff() }
        binding.btnBranches.setOnClickListener { showBranches() }
        binding.btnCopy.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("git log", binding.tvLog.text))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }
        binding.btnOpenIde.setOnClickListener {
            startActivity(Intent(this, CodeEditorActivity::class.java).putExtra(CodeEditorActivity.EXTRA_OPEN_DIR, workingDir))
        }
    }

    private fun refresh() {
        lifecycleScope.launch {
            val status = runGit("status")
            val branch = runGit("branch --show-current").trim().ifEmpty { "main" }
            withContext(Dispatchers.Main) {
                binding.tvBranch.text = "Branch: $branch"
                appendLog("=== git status ===\n$status")
                parseStatusToUI(status, branch)
            }
        }
    }

    private fun parseStatusToUI(status: String, branch: String) {
        val lines = status.lines()
        val staged = lines.filter { it.startsWith("Changes to be committed") }.size
        val modified = lines.count { it.trimStart().startsWith("modified:") || it.trimStart().startsWith("new file:") }
        val untracked = lines.count { it.trimStart().startsWith("Untracked") }
        binding.tvStatus.text = "⎇ $branch  |  Changes: $modified  |  Staged: $staged  |  Untracked: $untracked"
    }

    private fun stageAll() {
        lifecycleScope.launch {
            val out = runGit("add -A")
            withContext(Dispatchers.Main) { appendLog("git add -A\n$out"); refresh() }
        }
    }

    private fun unstageAll() {
        lifecycleScope.launch {
            val out = runGit("reset HEAD")
            withContext(Dispatchers.Main) { appendLog("git reset HEAD\n$out"); refresh() }
        }
    }

    private fun doCommit() {
        val msg = binding.etCommitMsg.text.toString().trim()
        if (msg.isEmpty()) { Toast.makeText(this, "Enter a commit message", Toast.LENGTH_SHORT).show(); return }
        lifecycleScope.launch {
            val out = runGit("commit -m \"$msg\"")
            withContext(Dispatchers.Main) {
                appendLog("git commit\n$out")
                binding.etCommitMsg.text?.clear()
                refresh()
                if (out.contains("error") || out.contains("nothing")) Toast.makeText(this@GitActivity, "Commit failed or nothing to commit", Toast.LENGTH_SHORT).show()
                else Toast.makeText(this@GitActivity, "✓ Committed!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun doPush() {
        lifecycleScope.launch {
            appendLog("Pushing...")
            // Try shell git first
            var out = runGit("push 2>&1")
            if (out.contains("fatal") || out.contains("error")) {
                // Fall back to GitHub API
                if (github.getToken().isNotEmpty()) {
                    appendLog("Shell git failed, trying GitHub API...")
                    val result = github.triggerWorkflow("Build.yml")
                    out = if (result) "✓ Workflow triggered via GitHub API" else "Failed via API too"
                }
            }
            withContext(Dispatchers.Main) { appendLog("git push\n$out"); refresh() }
        }
    }

    private fun doPull() {
        lifecycleScope.launch {
            val out = runGit("pull 2>&1")
            withContext(Dispatchers.Main) { appendLog("git pull\n$out"); refresh() }
        }
    }

    private fun doCommitAndPush() {
        val msg = binding.etCommitMsg.text.toString().trim()
        if (msg.isEmpty()) { Toast.makeText(this, "Enter a commit message", Toast.LENGTH_SHORT).show(); return }
        lifecycleScope.launch {
            val commitOut = runGit("commit -m \"$msg\"")
            val pushOut = if (!commitOut.contains("error")) runGit("push 2>&1") else "Skipped push (commit failed)"
            withContext(Dispatchers.Main) {
                appendLog("git commit + push\n$commitOut\n$pushOut")
                binding.etCommitMsg.text?.clear()
                refresh()
                Toast.makeText(this@GitActivity, if (!pushOut.contains("error")) "✓ Committed & pushed!" else "Push failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun doInit() {
        lifecycleScope.launch {
            val out = runGit("init")
            withContext(Dispatchers.Main) { appendLog("git init\n$out"); refresh(); Toast.makeText(this@GitActivity, "Repository initialized", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun doCloneDialog() {
        val et = android.widget.EditText(this).apply { hint = "https://github.com/user/repo.git" }
        AlertDialog.Builder(this).setTitle("Clone Repository").setView(et)
            .setPositiveButton("Clone") { _, _ ->
                val url = et.text.toString().trim()
                if (url.isNotEmpty()) doClone(url)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun doClone(url: String) {
        lifecycleScope.launch {
            appendLog("Cloning $url ...")
            val out = runShell("git clone \"$url\" \"$workingDir/$(basename $url .git)\" 2>&1")
            withContext(Dispatchers.Main) { appendLog(out); Toast.makeText(this@GitActivity, "Clone done", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun showGitLog() {
        lifecycleScope.launch {
            val out = runGit("log --oneline --decorate --graph --color=never -20")
            withContext(Dispatchers.Main) { appendLog("=== git log ===\n$out") }
        }
    }

    private fun showDiff() {
        lifecycleScope.launch {
            val diff = runGit("diff HEAD")
            withContext(Dispatchers.Main) {
                if (diff.isNotEmpty()) {
                    binding.tvDiff.text = diff
                    binding.tvDiff.visibility = android.view.View.VISIBLE
                } else {
                    binding.tvDiff.visibility = android.view.View.GONE
                    appendLog("No diff (working tree clean)")
                }
            }
        }
    }

    private fun showBranches() {
        lifecycleScope.launch {
            val branches = runGit("branch -a")
            withContext(Dispatchers.Main) { appendLog("=== branches ===\n$branches") }
        }
    }

    private suspend fun runGit(args: String): String = withContext(Dispatchers.IO) {
        runShell("git -C \"$workingDir\" $args 2>&1")
    }

    private suspend fun runShell(cmd: String): String = withContext(Dispatchers.IO) {
        try {
            val proc = ProcessBuilder("/system/bin/sh", "-c", cmd)
                .redirectErrorStream(true).start()
            proc.waitFor(30, TimeUnit.SECONDS)
            BufferedReader(InputStreamReader(proc.inputStream)).readText().trimEnd()
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    private fun appendLog(text: String) {
        outputLog.append(text).append("\n\n")
        binding.tvLog.text = outputLog.toString()
        binding.scrollLog.post { binding.scrollLog.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        else -> super.onOptionsItemSelected(item)
    }
}
