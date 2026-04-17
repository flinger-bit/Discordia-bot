package com.discordia.terminal

import android.os.Bundle
import android.os.Environment
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.discordia.terminal.databinding.ActivityProjectBuilderBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ProjectBuilderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProjectBuilderBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProjectBuilderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Project Builder"

        binding.btnMinecraftJava.setOnClickListener { createMinecraftJavaMod() }
        binding.btnMinecraftBedrock.setOnClickListener { createMinecraftBedrockAddon() }
        binding.btnAndroidApp.setOnClickListener { createAndroidTemplate() }
        binding.btnWebProject.setOnClickListener { createWebProject() }
        binding.btnShellScript.setOnClickListener { createShellScript() }
    }

    private fun createMinecraftJavaMod() {
        val name = binding.etProjectName.text.toString().ifBlank { "MyMod" }
        lifecycleScope.launch(Dispatchers.IO) {
            val outDir = File(Environment.getExternalStorageDirectory(), "DiscordiaProjects/minecraft-java/$name")
            outDir.mkdirs()

            val mainClass = """
package com.example.${name.lowercase()};

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("${name.lowercase()}")
public class ${name}Mod {
    public static final Logger LOGGER = LogManager.getLogger();

    public ${name}Mod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::setup);
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("${name} mod initialized!");
    }
}
""".trimIndent()

            val modToml = """
modLoader="javafml"
loaderVersion="[47,)"
license="MIT"

[[mods]]
modId="${name.lowercase()}"
version="1.0.0"
displayName="${name}"
description='''
  ${name} Minecraft mod created with Discordia Terminal.
'''

[[dependencies.${name.lowercase()}]]
    modId="forge"
    mandatory=true
    versionRange="[47,)"
    ordering="NONE"
    side="BOTH"

[[dependencies.${name.lowercase()}]]
    modId="minecraft"
    mandatory=true
    versionRange="[1.20.1,1.21)"
    ordering="NONE"
    side="BOTH"
""".trimIndent()

            val buildGradle = """
plugins {
    id 'net.minecraftforge.gradle' version '[6.0,6.2)'
    id 'org.parchmentmc.librarian.forgegradle' version '1.+'
}

version = '1.0.0'
group = 'com.example.${name.lowercase()}'

java.toolchain.languageVersion = JavaLanguageVersion.of(17)

minecraft {
    mappings channel: 'official', version: '1.20.1'
    runs {
        client { workingDirectory project.file('run') }
        server { workingDirectory project.file('run') }
    }
}

dependencies {
    minecraft 'net.minecraftforge:forge:1.20.1-47.2.0'
}

jar { manifest { attributes(['Specification-Title': '${name.lowercase()}', 'Specification-Vendor': 'discordia', 'Specification-Version': '1', 'Implementation-Title': project.name, 'Implementation-Version': project.jar.archiveVersion, 'Implementation-Vendor': 'discordia']) } }
""".trimIndent()

            File(outDir, "src/main/java/com/example/${name.lowercase()}").mkdirs()
            File(outDir, "src/main/resources/META-INF").mkdirs()
            File(outDir, "src/main/java/com/example/${name.lowercase()}/${name}Mod.java").writeText(mainClass)
            File(outDir, "src/main/resources/META-INF/mods.toml").writeText(modToml)
            File(outDir, "build.gradle").writeText(buildGradle)
            File(outDir, "README.md").writeText("# ${name} — Minecraft Java Mod\n\nCreated with Discordia Terminal.\n\nBuild: `./gradlew build`\n")

            withContext(Dispatchers.Main) {
                binding.tvOutput.text = "✅ Minecraft Java mod template created!\n\nLocation:\n${outDir.absolutePath}\n\nFiles:\n- src/main/java/.../\${name}Mod.java\n- src/main/resources/META-INF/mods.toml\n- build.gradle\n- README.md"
                Toast.makeText(this@ProjectBuilderActivity, "Project created!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createMinecraftBedrockAddon() {
        val name = binding.etProjectName.text.toString().ifBlank { "MyAddon" }
        lifecycleScope.launch(Dispatchers.IO) {
            val outDir = File(Environment.getExternalStorageDirectory(), "DiscordiaProjects/minecraft-bedrock/$name")
            val bpDir = File(outDir, "behavior_pack")
            val rpDir = File(outDir, "resource_pack")
            bpDir.mkdirs(); rpDir.mkdirs()

            val bpManifest = """{
  "format_version": 2,
  "header": {
    "name": "${name} Behavior",
    "description": "${name} addon created with Discordia Terminal",
    "uuid": "${java.util.UUID.randomUUID()}",
    "version": [1, 0, 0],
    "min_engine_version": [1, 20, 0]
  },
  "modules": [{ "type": "data", "uuid": "${java.util.UUID.randomUUID()}", "version": [1, 0, 0] }]
}"""
            val rpManifest = """{
  "format_version": 2,
  "header": {
    "name": "${name} Resources",
    "description": "${name} addon created with Discordia Terminal",
    "uuid": "${java.util.UUID.randomUUID()}",
    "version": [1, 0, 0],
    "min_engine_version": [1, 20, 0]
  },
  "modules": [{ "type": "resources", "uuid": "${java.util.UUID.randomUUID()}", "version": [1, 0, 0] }]
}"""

            File(bpDir, "manifest.json").writeText(bpManifest)
            File(rpDir, "manifest.json").writeText(rpManifest)
            File(outDir, "README.md").writeText("# ${name} Bedrock Addon\n\nCreated with Discordia Terminal.\n\nImport both behavior_pack and resource_pack into Minecraft Bedrock.\n")

            withContext(Dispatchers.Main) {
                binding.tvOutput.text = "✅ Minecraft Bedrock addon template created!\n\nLocation:\n${outDir.absolutePath}\n\nFolders:\n- behavior_pack/ (with manifest.json)\n- resource_pack/ (with manifest.json)"
                Toast.makeText(this@ProjectBuilderActivity, "Addon created!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createAndroidTemplate() {
        val name = binding.etProjectName.text.toString().ifBlank { "MyApp" }
        lifecycleScope.launch(Dispatchers.IO) {
            val outDir = File(Environment.getExternalStorageDirectory(), "DiscordiaProjects/android/$name")
            outDir.mkdirs()
            File(outDir, "README.md").writeText("# ${name} Android App\n\nCreated with Discordia Terminal.\n\nPush to GitHub and use the Actions workflow to build the APK.\n")
            File(outDir, "build.gradle.kts").writeText("plugins {\n    id(\"com.android.application\") version \"8.3.2\" apply false\n    id(\"org.jetbrains.kotlin.android\") version \"1.9.23\" apply false\n}\n")

            withContext(Dispatchers.Main) {
                binding.tvOutput.text = "✅ Android app template created at:\n${outDir.absolutePath}"
                Toast.makeText(this@ProjectBuilderActivity, "Template created!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createWebProject() {
        val name = binding.etProjectName.text.toString().ifBlank { "MyWebsite" }
        lifecycleScope.launch(Dispatchers.IO) {
            val outDir = File(Environment.getExternalStorageDirectory(), "DiscordiaProjects/web/$name")
            outDir.mkdirs()
            File(outDir, "index.html").writeText("<!DOCTYPE html>\n<html>\n<head><title>${name}</title></head>\n<body>\n<h1>${name}</h1>\n<p>Created with Discordia Terminal on Samsung SM-X200.</p>\n</body>\n</html>\n")
            File(outDir, "style.css").writeText("body { font-family: sans-serif; padding: 20px; }\n")
            File(outDir, "script.js").writeText("console.log('${name} loaded!');\n")

            withContext(Dispatchers.Main) {
                binding.tvOutput.text = "✅ Web project created at:\n${outDir.absolutePath}\n\nFiles: index.html, style.css, script.js"
                Toast.makeText(this@ProjectBuilderActivity, "Web project created!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createShellScript() {
        val name = binding.etProjectName.text.toString().ifBlank { "script" }
        lifecycleScope.launch(Dispatchers.IO) {
            val outDir = File(Environment.getExternalStorageDirectory(), "DiscordiaProjects/scripts")
            outDir.mkdirs()
            val script = File(outDir, "${name}.sh")
            script.writeText("#!/bin/sh\n# ${name}.sh - Created with Discordia Terminal\n\necho 'Script ${name} running...'\n\n# Add your commands here\n")
            script.setExecutable(true)

            withContext(Dispatchers.Main) {
                binding.tvOutput.text = "✅ Shell script created:\n${script.absolutePath}\n\nRun it from the terminal:\nsh ${script.absolutePath}"
                Toast.makeText(this@ProjectBuilderActivity, "Script created!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
