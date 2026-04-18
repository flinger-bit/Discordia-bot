package com.discordia.terminal

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.discordia.terminal.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        prefs = getSharedPreferences("discordia_settings", MODE_PRIVATE)
        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        binding.switchSound.isChecked = prefs.getBoolean("sound_enabled", true)
        binding.switchMonacoMinimap.isChecked = prefs.getBoolean("editor_minimap", true)
        binding.switchWordWrap.isChecked = prefs.getBoolean("editor_wordwrap", false)
        binding.switchAutoSave.isChecked = prefs.getBoolean("editor_autosave", false)
        binding.switchDarkMode.isChecked = prefs.getBoolean("dark_mode", true)
        binding.etFontSize.setText(prefs.getInt("editor_fontsize", 14).toString())
        binding.etTabSize.setText(prefs.getInt("editor_tabsize", 2).toString())
        binding.etDefaultDir.setText(prefs.getString("default_dir", "/sdcard"))
        binding.etGithubToken.setText(if (prefs.getString("github_token", "")!!.isNotEmpty()) "••••••••••••••••" else "")
        binding.etGithubOwner.setText(prefs.getString("github_owner", ""))
        binding.etGithubRepo.setText(prefs.getString("github_repo", ""))

        // Theme spinner
        val themes = arrayOf("VS Dark (Default)", "VS Light", "High Contrast Black", "High Contrast Light", "Monokai", "Dracula", "GitHub Dark", "Solarized Dark", "One Dark Pro", "Ayu Dark")
        binding.spinnerTheme.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, themes).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val savedTheme = prefs.getInt("editor_theme_idx", 0)
        binding.spinnerTheme.setSelection(savedTheme)
    }

    private fun setupListeners() {
        binding.switchSound.setOnCheckedChangeListener { _, v -> prefs.edit().putBoolean("sound_enabled", v).apply() }
        binding.switchMonacoMinimap.setOnCheckedChangeListener { _, v -> prefs.edit().putBoolean("editor_minimap", v).apply() }
        binding.switchWordWrap.setOnCheckedChangeListener { _, v -> prefs.edit().putBoolean("editor_wordwrap", v).apply() }
        binding.switchAutoSave.setOnCheckedChangeListener { _, v -> prefs.edit().putBoolean("editor_autosave", v).apply() }
        binding.switchDarkMode.setOnCheckedChangeListener { _, v -> prefs.edit().putBoolean("dark_mode", v).apply() }

        binding.btnSaveEditor.setOnClickListener {
            val fontSize = binding.etFontSize.text.toString().toIntOrNull()?.coerceIn(8, 32) ?: 14
            val tabSize = binding.etTabSize.text.toString().toIntOrNull()?.coerceIn(1, 8) ?: 2
            prefs.edit()
                .putInt("editor_fontsize", fontSize)
                .putInt("editor_tabsize", tabSize)
                .putInt("editor_theme_idx", binding.spinnerTheme.selectedItemPosition)
                .apply()
            Toast.makeText(this, "Editor settings saved", Toast.LENGTH_SHORT).show()
        }

        binding.btnSaveGithub.setOnClickListener {
            val token = binding.etGithubToken.text.toString().trim()
            val owner = binding.etGithubOwner.text.toString().trim()
            val repo = binding.etGithubRepo.text.toString().trim()
            if (!token.contains("•")) {
                val gh = GitHubManager(this)
                gh.setToken(token)
                gh.setOwner(owner)
                gh.setRepo(repo)
                prefs.edit().putString("github_owner", owner).putString("github_repo", repo).apply()
            } else {
                val gh = GitHubManager(this)
                gh.setOwner(owner)
                gh.setRepo(repo)
            }
            Toast.makeText(this, "GitHub settings saved", Toast.LENGTH_SHORT).show()
        }

        binding.btnSaveDir.setOnClickListener {
            val dir = binding.etDefaultDir.text.toString().trim()
            prefs.edit().putString("default_dir", dir).apply()
            Toast.makeText(this, "Default directory saved", Toast.LENGTH_SHORT).show()
        }

        binding.btnClearCache.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Clear Cache").setMessage("Clear all cached data?")
                .setPositiveButton("Clear") { _, _ ->
                    cacheDir.deleteRecursively()
                    Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show()
                }.setNegativeButton("Cancel", null).show()
        }

        binding.btnOpenIde.setOnClickListener { startActivity(Intent(this, CodeEditorActivity::class.java)) }
        binding.btnOpenGit.setOnClickListener { startActivity(Intent(this, GitActivity::class.java)) }
        binding.btnOpenWorkflow.setOnClickListener { startActivity(Intent(this, WorkflowBuilderActivity::class.java)) }

        binding.tvVersion.text = "Discordia Terminal v2.0.0 · SM-X200 Edition · Android 14"
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        else -> super.onOptionsItemSelected(item)
    }
}
