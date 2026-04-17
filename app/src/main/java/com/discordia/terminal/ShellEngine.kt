package com.discordia.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class ShellEngine {

    private var currentDirectory: File = File("/sdcard")
    private val history = mutableListOf<String>()
    private var historyIndex = -1

    fun getCurrentDirectory(): String = currentDirectory.absolutePath

    fun addToHistory(command: String) {
        if (command.isNotBlank() && (history.isEmpty() || history.last() != command)) {
            history.add(command)
            historyIndex = history.size
        }
    }

    fun getPreviousCommand(): String? {
        if (history.isEmpty()) return null
        historyIndex = (historyIndex - 1).coerceAtLeast(0)
        return history.getOrNull(historyIndex)
    }

    fun getNextCommand(): String? {
        if (history.isEmpty()) return null
        historyIndex = (historyIndex + 1).coerceAtMost(history.size)
        return history.getOrNull(historyIndex) ?: ""
    }

    suspend fun execute(input: String): String = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return@withContext ""

        addToHistory(trimmed)

        // Handle built-in commands
        val parts = trimmed.split("\\s+".toRegex())
        val cmd = parts[0]
        val args = parts.drop(1)

        when (cmd) {
            "cd" -> handleCd(args)
            "pwd" -> currentDirectory.absolutePath
            "ls" -> handleLs(args)
            "clear", "cls" -> "\u001b[2J\u001b[H"
            "echo" -> args.joinToString(" ")
            "cat" -> handleCat(args)
            "mkdir" -> handleMkdir(args)
            "rm" -> handleRm(args)
            "touch" -> handleTouch(args)
            "cp" -> handleCp(args)
            "mv" -> handleMv(args)
            "find" -> handleFind(args)
            "grep" -> handleGrep(args)
            "history" -> history.mapIndexed { i, h -> "  ${i + 1}  $h" }.joinToString("\n")
            "help" -> getHelp()
            "whoami" -> "discordia"
            "hostname" -> "SM-X200"
            "uname" -> handleUname(args)
            "date" -> java.util.Date().toString()
            "df" -> handleDf()
            "du" -> handleDu(args)
            "env" -> System.getenv().entries.joinToString("\n") { "${it.key}=${it.value}" }
            "exit" -> "exit"
            else -> executeShellCommand(trimmed)
        }
    }

    private fun handleCd(args: List<String>): String {
        val target = when {
            args.isEmpty() -> "/sdcard"
            args[0] == "~" -> "/sdcard"
            args[0] == ".." -> currentDirectory.parent ?: currentDirectory.absolutePath
            args[0].startsWith("/") -> args[0]
            else -> "${currentDirectory.absolutePath}/${args[0]}"
        }
        val dir = File(target)
        return if (dir.exists() && dir.isDirectory) {
            currentDirectory = dir.canonicalFile
            ""
        } else {
            "cd: $target: No such file or directory"
        }
    }

    private fun handleLs(args: List<String>): String {
        val showAll = args.contains("-a") || args.contains("-la") || args.contains("-al")
        val longFormat = args.contains("-l") || args.contains("-la") || args.contains("-al")
        val dir = args.lastOrNull { !it.startsWith("-") }?.let {
            if (it.startsWith("/")) File(it) else File(currentDirectory, it)
        } ?: currentDirectory

        if (!dir.exists()) return "ls: cannot access '${dir.absolutePath}': No such file or directory"
        val files = dir.listFiles()?.let { list ->
            if (showAll) list else list.filter { !it.name.startsWith(".") }
        }?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()

        return if (longFormat) {
            files.joinToString("\n") { f ->
                val type = if (f.isDirectory) "d" else "-"
                val size = if (f.isFile) f.length().toString().padStart(10) else "         -"
                "$type ${size}  ${f.name}${if (f.isDirectory) "/" else ""}"
            }
        } else {
            files.chunked(4).joinToString("\n") { row ->
                row.joinToString("  ") { f ->
                    val name = f.name + if (f.isDirectory) "/" else ""
                    name.padEnd(20)
                }
            }
        }
    }

    private fun handleCat(args: List<String>): String {
        if (args.isEmpty()) return "cat: missing file operand"
        return args.joinToString("\n\n") { name ->
            val f = if (name.startsWith("/")) File(name) else File(currentDirectory, name)
            if (f.exists() && f.isFile) {
                try { f.readText() } catch (e: Exception) { "cat: $name: Permission denied" }
            } else "cat: $name: No such file or directory"
        }
    }

    private fun handleMkdir(args: List<String>): String {
        if (args.isEmpty()) return "mkdir: missing operand"
        val recursive = args.contains("-p")
        val dirs = args.filter { !it.startsWith("-") }
        return dirs.joinToString("\n") { name ->
            val f = if (name.startsWith("/")) File(name) else File(currentDirectory, name)
            val success = if (recursive) f.mkdirs() else f.mkdir()
            if (!success && f.exists()) "" else if (!success) "mkdir: cannot create directory '$name'" else ""
        }.trim()
    }

    private fun handleRm(args: List<String>): String {
        if (args.isEmpty()) return "rm: missing operand"
        val recursive = args.contains("-r") || args.contains("-rf") || args.contains("-fr")
        val files = args.filter { !it.startsWith("-") }
        return files.joinToString("\n") { name ->
            val f = if (name.startsWith("/")) File(name) else File(currentDirectory, name)
            if (!f.exists()) return@joinToString "rm: cannot remove '$name': No such file"
            val deleted = if (recursive && f.isDirectory) f.deleteRecursively() else f.delete()
            if (deleted) "" else "rm: cannot remove '$name': Permission denied"
        }.trim()
    }

    private fun handleTouch(args: List<String>): String {
        if (args.isEmpty()) return "touch: missing file operand"
        return args.joinToString("\n") { name ->
            val f = if (name.startsWith("/")) File(name) else File(currentDirectory, name)
            try { if (!f.exists()) f.createNewFile(); "" } catch (e: Exception) { "touch: $name: ${e.message}" }
        }.trim()
    }

    private fun handleCp(args: List<String>): String {
        if (args.size < 2) return "cp: missing file operand"
        val src = File(if (args[0].startsWith("/")) args[0] else "${currentDirectory}/${args[0]}")
        val dst = File(if (args[1].startsWith("/")) args[1] else "${currentDirectory}/${args[1]}")
        return try { src.copyTo(dst, overwrite = true); "" } catch (e: Exception) { "cp: ${e.message}" }
    }

    private fun handleMv(args: List<String>): String {
        if (args.size < 2) return "mv: missing file operand"
        val src = File(if (args[0].startsWith("/")) args[0] else "${currentDirectory}/${args[0]}")
        val dst = File(if (args[1].startsWith("/")) args[1] else "${currentDirectory}/${args[1]}")
        return try { src.renameTo(dst); "" } catch (e: Exception) { "mv: ${e.message}" }
    }

    private fun handleFind(args: List<String>): String {
        val path = args.firstOrNull() ?: "."
        val dir = if (path.startsWith("/")) File(path) else File(currentDirectory, path)
        return try {
            dir.walkTopDown().take(200).joinToString("\n") { it.absolutePath }
        } catch (e: Exception) { "find: ${e.message}" }
    }

    private fun handleGrep(args: List<String>): String {
        if (args.size < 2) return "grep: missing pattern or file"
        val pattern = args[0]
        val file = File(if (args[1].startsWith("/")) args[1] else "${currentDirectory}/${args[1]}")
        return try {
            val regex = Regex(pattern)
            file.readLines().mapIndexed { i, line ->
                if (regex.containsMatchIn(line)) "${i + 1}:$line" else null
            }.filterNotNull().joinToString("\n")
        } catch (e: Exception) { "grep: ${e.message}" }
    }

    private fun handleUname(args: List<String>): String {
        val all = args.contains("-a")
        return if (all) "Linux SM-X200 5.15.0 #1 SMP Android 14" else "Linux"
    }

    private fun handleDf(): String {
        val root = File("/sdcard")
        val total = root.totalSpace / (1024 * 1024)
        val free = root.freeSpace / (1024 * 1024)
        val used = total - free
        return "Filesystem         Size  Used  Avail  Use%  Mounted on\n/sdcard         ${total}M  ${used}M  ${free}M   ${if(total>0)(used*100/total) else 0}%  /sdcard"
    }

    private fun handleDu(args: List<String>): String {
        val path = args.firstOrNull { !it.startsWith("-") } ?: "."
        val f = if (path.startsWith("/")) File(path) else File(currentDirectory, path)
        val size = f.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1024
        return "$size\t${f.absolutePath}"
    }

    private fun executeShellCommand(command: String): String {
        return try {
            val process = ProcessBuilder("/system/bin/sh", "-c", command)
                .directory(currentDirectory)
                .redirectErrorStream(true)
                .start()
            process.waitFor(10, TimeUnit.SECONDS)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.readText().trimEnd()
        } catch (e: Exception) {
            "Error: ${e.message ?: "Command failed"}"
        }
    }

    private fun getHelp(): String = """
Discordia Terminal — Available Commands:
  Navigation:   cd, pwd, ls
  Files:        cat, touch, mkdir, rm, cp, mv, find, grep, du, df
  System:       echo, env, uname, date, whoami, hostname
  Session:      history, clear, help, exit
  
Any other command is executed via the Android shell.
Type a command and press Enter or tap [EXEC].
    """.trimIndent()
}
