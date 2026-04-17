package com.discordia.terminal

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileAdapter(
    private val files: List<File>,
    private val onClick: (File) -> Unit
) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvFileName)
        val tvInfo: TextView = view.findViewById(R.id.tvFileInfo)
        val ivIcon: ImageView = view.findViewById(R.id.ivFileIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = files[position]
        holder.tvName.text = file.name
        val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            .format(Date(file.lastModified()))
        holder.tvInfo.text = if (file.isDirectory) {
            val count = file.listFiles()?.size ?: 0
            "Folder • $count items • $dateStr"
        } else {
            val size = formatSize(file.length())
            "$size • $dateStr"
        }
        holder.ivIcon.setImageResource(
            when {
                file.isDirectory -> android.R.drawable.ic_menu_agenda
                file.extension.lowercase() in listOf("jpg","jpeg","png","gif","webp") -> android.R.drawable.ic_menu_gallery
                file.extension.lowercase() in listOf("mp3","wav","ogg","flac") -> android.R.drawable.ic_lock_silent_mode_off
                file.extension.lowercase() in listOf("mp4","mkv","avi","webm") -> android.R.drawable.ic_media_play
                file.extension.lowercase() == "apk" -> android.R.drawable.ic_menu_add
                else -> android.R.drawable.ic_menu_edit
            }
        )
        holder.itemView.setOnClickListener { onClick(file) }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }

    override fun getItemCount() = files.size
}
