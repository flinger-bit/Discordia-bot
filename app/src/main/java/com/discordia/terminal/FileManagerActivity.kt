package com.discordia.terminal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.discordia.terminal.databinding.ActivityFileManagerBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFileManagerBinding
    private var currentDir: File = Environment.getExternalStorageDirectory()
    private val history = mutableListOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "File Manager"

        binding.rvFiles.layoutManager = LinearLayoutManager(this)
        loadDirectory(currentDir)

        binding.btnUp.setOnClickListener {
            if (history.isNotEmpty()) {
                currentDir = history.removeLast()
                loadDirectory(currentDir)
            } else {
                currentDir.parentFile?.let { parent ->
                    loadDirectory(parent)
                }
            }
        }
    }

    private fun loadDirectory(dir: File) {
        currentDir = dir
        binding.tvPath.text = dir.absolutePath

        val files = try {
            dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
        } catch (e: Exception) {
            Toast.makeText(this, "Access denied", Toast.LENGTH_SHORT).show()
            emptyList()
        }

        binding.rvFiles.adapter = FileAdapter(files) { file ->
            if (file.isDirectory) {
                history.add(currentDir)
                loadDirectory(file)
            } else {
                openFile(file)
            }
        }

        binding.tvFileCount.text = "${files.size} items"
    }

    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, contentResolver.getType(uri) ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if (history.isNotEmpty()) {
            currentDir = history.removeLast()
            loadDirectory(currentDir)
        } else {
            super.onBackPressed()
        }
    }
}
