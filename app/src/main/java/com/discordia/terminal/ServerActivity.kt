package com.discordia.terminal

import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.discordia.terminal.databinding.ActivityServerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

class ServerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServerBinding
    private var server: LocalServer? = null
    private var isRunning = false
    private var logJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Local Server"

        binding.btnStartStop.setOnClickListener {
            if (isRunning) stopServer() else startServer()
        }
        updateUI()
    }

    private fun startServer() {
        val port = binding.etPort.text.toString().toIntOrNull() ?: 8080
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                server = LocalServer(port)
                server?.start()
                val ip = InetAddress.getLocalHost().hostAddress ?: "localhost"
                withContext(Dispatchers.Main) {
                    isRunning = true
                    updateUI()
                    binding.tvStatus.text = "✅ Server running at:\nhttp://$ip:$port\nhttp://localhost:$port"
                    Toast.makeText(this@ServerActivity, "Server started on port $port", Toast.LENGTH_SHORT).show()
                    startLogUpdates()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = "❌ Error: ${e.message}"
                    Toast.makeText(this@ServerActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun stopServer() {
        server?.stop()
        server = null
        isRunning = false
        logJob?.cancel()
        updateUI()
        binding.tvStatus.text = "🔴 Server stopped"
        binding.tvLog.text = ""
    }

    private fun startLogUpdates() {
        logJob = lifecycleScope.launch {
            while (isActive && isRunning) {
                delay(1000)
                val log = server?.requestLog?.takeLast(50)?.joinToString("\n") ?: ""
                withContext(Dispatchers.Main) {
                    binding.tvLog.text = if (log.isEmpty()) "(no requests yet)" else log
                    binding.scrollLog.post { binding.scrollLog.fullScroll(android.view.View.FOCUS_DOWN) }
                }
            }
        }
    }

    private fun updateUI() {
        binding.btnStartStop.text = if (isRunning) "Stop Server" else "Start Server"
        binding.etPort.isEnabled = !isRunning
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRunning) stopServer()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
