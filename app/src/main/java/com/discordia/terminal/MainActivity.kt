package com.discordia.terminal

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.discordia.terminal.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "DISCORDIA TERMINAL"

        SoundManager.init(this)
        SoundManager.playStartup()

        setupCards()
    }

    private fun setupCards() {
        binding.cardIde.setOnClickListener {
            SoundManager.playClick()
            startActivity(Intent(this, CodeEditorActivity::class.java))
        }
        binding.cardTerminal.setOnClickListener {
            SoundManager.playClick()
            startActivity(Intent(this, TerminalActivity::class.java))
        }
        binding.cardFiles.setOnClickListener {
            SoundManager.playClick()
            startActivity(Intent(this, FileManagerActivity::class.java))
        }
        binding.cardGit.setOnClickListener {
            SoundManager.playClick()
            startActivity(Intent(this, GitActivity::class.java))
        }
        binding.cardServer.setOnClickListener {
            SoundManager.playClick()
            startActivity(Intent(this, ServerActivity::class.java))
        }
        binding.cardMods.setOnClickListener {
            SoundManager.playClick()
            startActivity(Intent(this, ProjectBuilderActivity::class.java))
        }
        binding.cardSetup.setOnClickListener {
            SoundManager.playClick()
            startActivity(Intent(this, SetupActivity::class.java))
        }
        binding.cardWorkflows.setOnClickListener {
            SoundManager.playClick()
            startActivity(Intent(this, WorkflowBuilderActivity::class.java))
        }
        binding.cardExtensions.setOnClickListener {
            SoundManager.playClick()
            startActivity(Intent(this, ExtensionsActivity::class.java))
        }
        binding.cardSettings.setOnClickListener {
            SoundManager.playClick()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SoundManager.release()
    }
}
