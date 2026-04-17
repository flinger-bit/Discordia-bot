package com.discordia.terminal

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class SetupAdapter(
    private val entries: List<File>,
    private val onClick: (File) -> Unit
) : RecyclerView.Adapter<SetupAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvSetupName)
        val tvType: TextView = view.findViewById(R.id.tvSetupType)
        val ivIcon: ImageView = view.findViewById(R.id.ivSetupIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_setup, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = entries[position]
        if (file.name == "..") {
            holder.tvName.text = ".. (go up)"
            holder.tvType.text = "parent directory"
            holder.ivIcon.setImageResource(android.R.drawable.ic_menu_revert)
        } else if (file.isDirectory) {
            holder.tvName.text = "${file.name}/"
            val count = file.listFiles()?.size ?: 0
            holder.tvType.text = "$count items"
            holder.ivIcon.setImageResource(android.R.drawable.ic_menu_agenda)
        } else {
            holder.tvName.text = file.name
            val kb = file.length() / 1024
            val sizeStr = if (kb < 1) "${file.length()} B" else "${kb} KB"
            holder.tvType.text = "${scriptTypeLabel(file.extension)} • $sizeStr"
            holder.ivIcon.setImageResource(iconForExtension(file.extension))
        }
        holder.itemView.setOnClickListener { onClick(file) }
    }

    private fun scriptTypeLabel(ext: String): String = when (ext.lowercase()) {
        "sh", "bash" -> "Shell script"
        "py" -> "Python script"
        "js" -> "JavaScript / Node"
        "rb" -> "Ruby script"
        "php" -> "PHP script"
        "pl" -> "Perl script"
        "lua" -> "Lua script"
        "txt" -> "Text / Config"
        "json" -> "JSON config"
        "md" -> "Markdown"
        "toml", "ini", "cfg", "conf" -> "Config file"
        "html", "htm" -> "HTML file"
        "css" -> "CSS file"
        "xml" -> "XML file"
        "apk" -> "Android APK"
        "zip", "tar", "gz" -> "Archive"
        else -> ext.uppercase().ifEmpty { "File" }
    }

    private fun iconForExtension(ext: String): Int = when (ext.lowercase()) {
        "sh", "bash", "py", "js", "rb", "php", "pl", "lua" -> android.R.drawable.ic_media_play
        "txt", "md" -> android.R.drawable.ic_menu_edit
        "json", "xml", "toml", "ini", "cfg", "conf" -> android.R.drawable.ic_menu_manage
        "html", "htm", "css" -> android.R.drawable.ic_menu_share
        "apk" -> android.R.drawable.ic_menu_add
        "zip", "tar", "gz" -> android.R.drawable.ic_menu_save
        else -> android.R.drawable.ic_menu_edit
    }

    override fun getItemCount() = entries.size
}
