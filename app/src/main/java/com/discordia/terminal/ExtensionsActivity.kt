package com.discordia.terminal

import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.discordia.terminal.databinding.ActivityExtensionsBinding

data class Extension(
    val id: String,
    val name: String,
    val description: String,
    val author: String,
    val icon: String,
    val category: String,
    val downloads: String,
    var installed: Boolean = false
)

class ExtensionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExtensionsBinding
    private lateinit var prefs: SharedPreferences
    private val allExtensions = mutableListOf<Extension>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExtensionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Extensions"

        prefs = getSharedPreferences("extensions", MODE_PRIVATE)
        buildExtensionList()
        setupUI()
    }

    private fun buildExtensionList() {
        val data = listOf(
            Extension("python","Python","IntelliSense, Pylance, Jupyter","Microsoft","🐍","Languages","12.3M"),
            Extension("kotlin","Kotlin","Kotlin language support, snippets, refactoring","JetBrains","🟣","Languages","3.1M"),
            Extension("java","Java","Full Java support, Maven, Gradle","Microsoft","☕","Languages","5.2M"),
            Extension("js-ts","JavaScript/TypeScript","Full ES2024 + TypeScript support","Microsoft","🟡","Languages","20M+"),
            Extension("html-css","HTML CSS Support","HTML/CSS/SCSS IntelliSense","ecmel","🟠","Languages","8.2M"),
            Extension("cpp","C/C++","IntelliSense, debugging for C/C++","Microsoft","⚡","Languages","6.7M"),
            Extension("rust","Rust Analyzer","Rust language support","rust-lang","🦀","Languages","3.4M"),
            Extension("go","Go","Rich Go language support","Google","🐹","Languages","4.1M"),
            Extension("php","PHP IntelliSense","Advanced PHP support","Felix Becker","🐘","Languages","2.8M"),
            Extension("dart","Dart/Flutter","Dart language + Flutter tools","Dart","🦋","Languages","2.2M"),
            Extension("gitlens","GitLens","Supercharge Git","GitKraken","🔵","SCM","22M+"),
            Extension("prettier","Prettier","Code formatter","Prettier","💜","Formatters","31M+"),
            Extension("eslint","ESLint","JavaScript linting","Microsoft","🔴","Linters","28M+"),
            Extension("material-icons","Material Icon Theme","Icons for files","Philipp Kief","🎨","Themes","18M+"),
            Extension("monokai","Monokai Pro","Popular dark theme","Monokai","🎭","Themes","4.2M"),
            Extension("github-theme","GitHub Theme","GitHub's official dark/light","GitHub","🌑","Themes","3.8M"),
            Extension("dracula","Dracula Theme","Dark theme for IDEs","Dracula","🧛","Themes","5.1M"),
            Extension("bracket-pairs","Rainbow Brackets","Colorize brackets","CoenraadS","🌈","Editor","15M+"),
            Extension("indent-rainbow","Indent Rainbow","Colorize indentation","oderwat","🌊","Editor","7.2M"),
            Extension("markdown","Markdown All in One","Full markdown editing","Yu Zhang","📝","Languages","9M+"),
            Extension("emmet","Emmet 2","HTML/CSS abbreviations","Emmet","⚡","Editor","10M+"),
            Extension("snippets","Code Snippets","Snippets for all languages","Discordia","✂️","Editor","6.1M"),
            Extension("todo","Todo Tree","Highlight TODO comments","Gruntfuggly","📌","Productivity","5.4M"),
            Extension("code-spell","Code Spell Checker","Spelling checker","Street Side","📖","Productivity","4.6M"),
            Extension("path-intellisense","Path Intellisense","Autocomplete filenames","Christian Kohler","🔗","Productivity","9.2M"),
            Extension("docker","Docker","Docker support","Microsoft","🐳","DevOps","7.8M"),
            Extension("remote-ssh","Remote SSH","Remote development","Microsoft","🌐","Remote","9.1M"),
            Extension("rest-client","REST Client","HTTP requests in editor","Huachao Mao","🔌","Testing","5.5M"),
        )
        allExtensions.clear()
        data.forEach { ext ->
            ext.installed = prefs.getBoolean("installed_${ext.id}", false)
            allExtensions.add(ext)
        }
    }

    private fun setupUI() {
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { filterList(s?.toString() ?: "") }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.chipAll.setOnClickListener { filterList(binding.etSearch.text.toString()) }
        binding.chipInstalled.setOnClickListener { showInstalled() }
        binding.chipLanguages.setOnClickListener { filterCategory("Languages") }
        binding.chipThemes.setOnClickListener { filterCategory("Themes") }
        binding.chipTools.setOnClickListener { filterCategory("Editor", "Formatters", "Linters", "Productivity", "SCM") }

        filterList("")
    }

    private fun filterList(query: String) {
        val filtered = if (query.isEmpty()) allExtensions else allExtensions.filter {
            it.name.contains(query, true) || it.description.contains(query, true) || it.category.contains(query, true)
        }
        renderList(filtered)
    }

    private fun filterCategory(vararg cats: String) {
        renderList(allExtensions.filter { e -> cats.any { e.category.equals(it, true) } })
    }

    private fun showInstalled() { renderList(allExtensions.filter { it.installed }) }

    private fun renderList(list: List<Extension>) {
        binding.tvCount.text = "${list.size} extensions"
        binding.recycler.apply {
            layoutManager = LinearLayoutManager(this@ExtensionsActivity)
            adapter = ExtAdapter(list) { ext, install ->
                ext.installed = install
                prefs.edit().putBoolean("installed_${ext.id}", install).apply()
                Toast.makeText(this@ExtensionsActivity, if (install) "✓ ${ext.name} installed" else "${ext.name} uninstalled", Toast.LENGTH_SHORT).show()
                renderList(list)
            }
        }
    }
}

class ExtAdapter(
    private val items: List<Extension>,
    private val onToggle: (Extension, Boolean) -> Unit
) : RecyclerView.Adapter<ExtAdapter.VH>() {

    inner class VH(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val icon = itemView.findViewById<android.widget.TextView>(R.id.tvExtIcon)
        val name = itemView.findViewById<android.widget.TextView>(R.id.tvExtName)
        val desc = itemView.findViewById<android.widget.TextView>(R.id.tvExtDesc)
        val meta = itemView.findViewById<android.widget.TextView>(R.id.tvExtMeta)
        val btn = itemView.findViewById<android.widget.Button>(R.id.btnExtAction)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val v = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_extension, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val e = items[pos]
        h.icon.text = e.icon
        h.name.text = e.name
        h.desc.text = e.description
        h.meta.text = "${e.author} · ${e.category} · ⬇ ${e.downloads}"
        if (e.installed) {
            h.btn.text = "✓ Installed"
            h.btn.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#3FB950"))
            h.btn.setOnClickListener { onToggle(e, false) }
        } else {
            h.btn.text = "Install"
            h.btn.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#58A6FF"))
            h.btn.setOnClickListener { onToggle(e, true) }
        }
    }

    override fun getItemCount() = items.size
}
