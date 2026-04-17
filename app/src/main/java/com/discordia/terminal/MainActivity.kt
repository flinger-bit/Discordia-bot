package com.discordia.terminal

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.discordia.terminal.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.cardTerminal.setOnClickListener {
            startActivity(Intent(this, TerminalActivity::class.java))
        }
        binding.cardFileManager.setOnClickListener {
            startActivity(Intent(this, FileManagerActivity::class.java))
        }
        binding.cardServer.setOnClickListener {
            startActivity(Intent(this, ServerActivity::class.java))
        }
        binding.cardBuilder.setOnClickListener {
            startActivity(Intent(this, ProjectBuilderActivity::class.java))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> {
                Toast.makeText(this, "Discordia Terminal v1.0 — SM-X200 Edition", Toast.LENGTH_LONG).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
