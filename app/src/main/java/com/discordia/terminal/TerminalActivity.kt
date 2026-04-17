package com.discordia.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.KeyEvent
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.discordia.terminal.databinding.ActivityTerminalBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class TerminalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTerminalBinding
    private val shell = ShellEngine()
    private val outputBuilder = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTerminalBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Terminal"

        binding.tvOutput.typeface = Typeface.MONOSPACE
        setupInput()
        showBanner()
    }

    private fun showBanner() {
        val banner = """
╔══════════════════════════════════════╗
║   DISCORDIA TERMINAL  v1.0           ║
║   Device: Samsung SM-X200            ║
║   Android 14 — All Files Access      ║
╚══════════════════════════════════════╝
Type 'help' for available commands.
${getPrompt()}""".trimIndent()
        appendOutput(banner)
        updatePrompt()
    }

    private fun setupInput() {
        binding.etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
                executeCommand()
                true
            } else false
        }
        binding.etInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        shell.getPreviousCommand()?.let { binding.etInput.setText(it); binding.etInput.setSelection(it.length) }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        val cmd = shell.getNextCommand() ?: ""
                        binding.etInput.setText(cmd); binding.etInput.setSelection(cmd.length)
                        true
                    }
                    else -> false
                }
            } else false
        }
        binding.btnExec.setOnClickListener { executeCommand() }
        binding.btnClear.setOnClickListener {
            outputBuilder.clear()
            binding.tvOutput.text = ""
            updatePrompt()
        }
        binding.btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Terminal Output", binding.tvOutput.text))
        }
        binding.btnCtrlC.setOnClickListener {
            appendOutput("^C")
            binding.etInput.text?.clear()
            updatePrompt()
        }
        binding.btnTab.setOnClickListener {
            val current = binding.etInput.text.toString()
            autoComplete(current)
        }
    }

    private fun executeCommand() {
        val input = binding.etInput.text.toString().trim()
        binding.etInput.text?.clear()
        if (input.isEmpty()) {
            updatePrompt()
            return
        }
        val prompt = "${getPrompt()}$input"
        appendOutput(prompt)

        if (input == "exit") {
            appendOutput("Goodbye.")
            finish()
            return
        }

        lifecycleScope.launch {
            binding.btnExec.isEnabled = false
            val result = shell.execute(input)
            withContext(Dispatchers.Main) {
                if (result.isNotEmpty() && result != "\u001b[2J\u001b[H") {
                    appendOutput(result)
                } else if (result == "\u001b[2J\u001b[H") {
                    outputBuilder.clear()
                    binding.tvOutput.text = ""
                }
                updatePrompt()
                binding.btnExec.isEnabled = true
                binding.scrollView.post { binding.scrollView.fullScroll(android.view.View.FOCUS_DOWN) }
            }
        }
    }

    private fun autoComplete(prefix: String) {
        if (prefix.isBlank()) return
        val parts = prefix.split(" ")
        val lastPart = parts.last()
        val dir = if (lastPart.contains("/")) {
            val idx = lastPart.lastIndexOf("/")
            val parent = lastPart.substring(0, idx)
            java.io.File(if (parent.startsWith("/")) parent else "${shell.getCurrentDirectory()}/$parent")
        } else java.io.File(shell.getCurrentDirectory())

        val matches = dir.listFiles()?.filter { it.name.startsWith(lastPart.substringAfterLast("/")) }
        if (matches?.size == 1) {
            val completion = parts.dropLast(1) + (lastPart.substringBeforeLast("/", "") + matches[0].name + if (matches[0].isDirectory) "/" else "")
            binding.etInput.setText(completion.joinToString(" "))
            binding.etInput.setSelection(binding.etInput.text?.length ?: 0)
        } else if (!matches.isNullOrEmpty()) {
            appendOutput(matches.joinToString("  ") { it.name + if (it.isDirectory) "/" else "" })
            updatePrompt()
        }
    }

    private fun appendOutput(text: String) {
        outputBuilder.appendLine(text)
        binding.tvOutput.text = outputBuilder.toString()
    }

    private fun updatePrompt() {
        val dir = shell.getCurrentDirectory().replace("/storage/emulated/0", "~").replace("/sdcard", "~")
        binding.tvPrompt.text = "discordia@SM-X200:$dir$"
    }

    private fun getPrompt(): String {
        val dir = shell.getCurrentDirectory().replace("/storage/emulated/0", "~").replace("/sdcard", "~")
        return "discordia@SM-X200:$dir$ "
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
