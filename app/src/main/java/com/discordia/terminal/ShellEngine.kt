package com.discordia.terminal

import android.os.Build
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
            "head" -> handleHead(args)
            "tail" -> handleTail(args)
            "wc" -> handleWc(args)
            "sort" -> handleSort(args)
            "uniq" -> handleUniq(args)
            "cut" -> executeShellCommand(trimmed)
            "awk" -> executeShellCommand(trimmed)
            "sed" -> executeShellCommand(trimmed)
            "chmod" -> executeShellCommand(trimmed)
            "ps" -> executeShellCommand("ps -A 2>/dev/null || ps")
            "kill" -> executeShellCommand(trimmed)
            "top" -> executeShellCommand("top -n 1 -b 2>/dev/null | head -30")
            "free" -> handleFree()
            "ifconfig" -> executeShellCommand("ifconfig 2>/dev/null || ip addr")
            "ip" -> executeShellCommand(trimmed)
            "ping" -> executeShellCommand(trimmed)
            "curl" -> executeShellCommand(trimmed)
            "wget" -> executeShellCommand(trimmed)
            "tar" -> executeShellCommand(trimmed)
            "zip" -> executeShellCommand(trimmed)
            "unzip" -> executeShellCommand(trimmed)
            "which" -> handleWhich(args)
            "diff" -> handleDiff(args)
            "ln" -> executeShellCommand(trimmed)
            "stat" -> handleStat(args)
            "file" -> handleFileType(args)
            "less", "more" -> handleCat(args)
            "printf" -> args.joinToString(" ").replace("\\n", "\n").replace("\\t", "\t")
            "set", "export" -> handleSet(args)
            "source", "." -> handleSource(args)
            "alias" -> handleAlias(args, trimmed)
            "basename" -> args.lastOrNull()?.let { File(it).name } ?: "basename: missing operand"
            "dirname" -> args.lastOrNull()?.let { File(it).parent ?: "." } ?: "dirname: missing operand"
            "realpath" -> args.firstOrNull()?.let { p -> if (p.startsWith("/")) p else "${currentDirectory.absolutePath}/$p" } ?: ""
            "yes" -> "(output suppressed — infinite loop prevented)"
            "nano", "vi", "vim" -> "Use the Code Editor for editing files! (tap IDE card)"
            "python3", "python" -> if (args.isEmpty()) "Python 3 interactive mode not available in app — run scripts via Setup Folder" else executeShellCommand(trimmed)
            "node", "nodejs" -> if (args.isEmpty()) "Node.js interactive mode not available in app — run scripts via Setup Folder" else executeShellCommand(trimmed)
            "npm" -> executeShellCommand(trimmed)
            "git" -> executeShellCommand(trimmed)
            "sh", "bash" -> executeShellCommand(trimmed)
            "open" -> "open: use File Manager or IDE to open files"
            "sysinfo" -> handleSysinfo()
            "diskinfo" -> handleDiskInfo()
            "netinfo" -> handleNetInfo()
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
        val dirPath = args.lastOrNull { !it.startsWith("-") }
        val dir: File = if (dirPath != null) {
            if (dirPath.startsWith("/")) File(dirPath) else File(currentDirectory, dirPath)
        } else {
            currentDirectory
        }

        if (!dir.exists()) return "ls: cannot access '${dir.absolutePath}': No such file or directory"
        val rawFiles: Array<File> = dir.listFiles() ?: return "(empty)"
        val fileList: List<File> = rawFiles.toList()
        val filtered: List<File> = if (showAll) fileList else fileList.filter { !it.name.startsWith(".") }
        val files: List<File> = filtered.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name })

        return if (longFormat) {
            files.joinToString("\n") { f ->
                val type = if (f.isDirectory) "d" else "-"
                val size = if (f.isFile) f.length().toString().padStart(10) else "         -"
                "$type ${size}  ${f.name}${if (f.isDirectory) "/" else ""}"
            }
        } else {
            files.chunked(4).joinToString("\n") { row: List<File> ->
                row.joinToString("  ") { f: File ->
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
            } else {
                "cat: $name: No such file or directory"
            }
        }
    }

    private fun handleMkdir(args: List<String>): String {
        if (args.isEmpty()) return "mkdir: missing operand"
        val recursive = args.contains("-p")
        val dirs = args.filter { !it.startsWith("-") }
        return dirs.joinToString("\n") { name ->
            val f = if (name.startsWith("/")) File(name) else File(currentDirectory, name)
            val success = if (recursive) f.mkdirs() else f.mkdir()
            if (!success && !f.exists()) "mkdir: cannot create directory '$name'" else ""
        }.trim()
    }

    private fun handleRm(args: List<String>): String {
        if (args.isEmpty()) return "rm: missing operand"
        val recursive = args.any { it.startsWith("-") && it.contains("r") }
        val fileNames = args.filter { !it.startsWith("-") }
        return fileNames.joinToString("\n") { name ->
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
        val srcPath = args[0]
        val dstPath = args[1]
        val src = if (srcPath.startsWith("/")) File(srcPath) else File(currentDirectory, srcPath)
        val dst = if (dstPath.startsWith("/")) File(dstPath) else File(currentDirectory, dstPath)
        return try { src.copyTo(dst, overwrite = true); "" } catch (e: Exception) { "cp: ${e.message}" }
    }

    private fun handleMv(args: List<String>): String {
        if (args.size < 2) return "mv: missing file operand"
        val srcPath = args[0]
        val dstPath = args[1]
        val src = if (srcPath.startsWith("/")) File(srcPath) else File(currentDirectory, srcPath)
        val dst = if (dstPath.startsWith("/")) File(dstPath) else File(currentDirectory, dstPath)
        return if (src.renameTo(dst)) "" else "mv: cannot move '$srcPath' to '$dstPath'"
    }

    private fun handleFind(args: List<String>): String {
        val pathStr = args.firstOrNull() ?: "."
        val dir = if (pathStr.startsWith("/")) File(pathStr) else File(currentDirectory, pathStr)
        return try {
            dir.walkTopDown().take(200).joinToString("\n") { it.absolutePath }
        } catch (e: Exception) { "find: ${e.message}" }
    }

    private fun handleGrep(args: List<String>): String {
        if (args.size < 2) return "grep: missing pattern or file"
        val pattern = args[0]
        val filePath = args[1]
        val file = if (filePath.startsWith("/")) File(filePath) else File(currentDirectory, filePath)
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
        val pct = if (total > 0) (used * 100 / total) else 0
        return "Filesystem        Size   Used  Avail  Use%  Mounted on\n/sdcard        ${total}M  ${used}M  ${free}M   $pct%  /sdcard"
    }

    private fun handleDu(args: List<String>): String {
        val pathStr = args.firstOrNull { !it.startsWith("-") } ?: "."
        val f = if (pathStr.startsWith("/")) File(pathStr) else File(currentDirectory, pathStr)
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

    private fun handleHead(args: List<String>): String {
        val n = if (args.contains("-n")) args.getOrNull(args.indexOf("-n") + 1)?.toIntOrNull() ?: 10 else 10
        val fileArg = args.firstOrNull { !it.startsWith("-") && it != args.getOrNull(args.indexOf("-n") + 1) }
        val f = if (fileArg != null) { if (fileArg.startsWith("/")) File(fileArg) else File(currentDirectory, fileArg) } else return "head: missing file"
        return try { f.readLines().take(n).joinToString("\n") } catch (e: Exception) { "head: ${e.message}" }
    }

    private fun handleTail(args: List<String>): String {
        val n = if (args.contains("-n")) args.getOrNull(args.indexOf("-n") + 1)?.toIntOrNull() ?: 10 else 10
        val fileArg = args.firstOrNull { !it.startsWith("-") && it != args.getOrNull(args.indexOf("-n") + 1) }
        val f = if (fileArg != null) { if (fileArg.startsWith("/")) File(fileArg) else File(currentDirectory, fileArg) } else return "tail: missing file"
        return try { f.readLines().takeLast(n).joinToString("\n") } catch (e: Exception) { "tail: ${e.message}" }
    }

    private fun handleWc(args: List<String>): String {
        val fileArg = args.firstOrNull { !it.startsWith("-") } ?: return "wc: missing file"
        val f = if (fileArg.startsWith("/")) File(fileArg) else File(currentDirectory, fileArg)
        return try {
            val content = f.readText()
            val lines = content.lines().size
            val words = content.split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
            val chars = content.length
            "  $lines  $words  $chars $fileArg"
        } catch (e: Exception) { "wc: ${e.message}" }
    }

    private fun handleSort(args: List<String>): String {
        val fileArg = args.firstOrNull { !it.startsWith("-") } ?: return "sort: missing file"
        val reverse = args.contains("-r")
        val f = if (fileArg.startsWith("/")) File(fileArg) else File(currentDirectory, fileArg)
        return try {
            val lines = f.readLines()
            (if (reverse) lines.sortedDescending() else lines.sorted()).joinToString("\n")
        } catch (e: Exception) { "sort: ${e.message}" }
    }

    private fun handleUniq(args: List<String>): String {
        val fileArg = args.firstOrNull { !it.startsWith("-") } ?: return "uniq: missing file"
        val f = if (fileArg.startsWith("/")) File(fileArg) else File(currentDirectory, fileArg)
        return try { f.readLines().distinct().joinToString("\n") } catch (e: Exception) { "uniq: ${e.message}" }
    }

    private fun handleFree(): String {
        val rt = Runtime.getRuntime()
        val maxMb = rt.maxMemory() / (1024 * 1024)
        val totalMb = rt.totalMemory() / (1024 * 1024)
        val freeMb = rt.freeMemory() / (1024 * 1024)
        val usedMb = totalMb - freeMb
        return "              total        used        free\nMem:          ${maxMb}M       ${usedMb}M       ${freeMb}M"
    }

    private fun handleWhich(args: List<String>): String {
        if (args.isEmpty()) return "which: missing argument"
        val paths = listOf("/system/bin", "/system/xbin", "/sbin", "/vendor/bin")
        return args.joinToString("\n") { cmd ->
            paths.firstOrNull { File("$it/$cmd").exists() }?.let { "$it/$cmd" } ?: "$cmd: not found"
        }
    }

    private fun handleDiff(args: List<String>): String {
        if (args.size < 2) return "diff: missing files"
        val f1 = if (args[0].startsWith("/")) File(args[0]) else File(currentDirectory, args[0])
        val f2 = if (args[1].startsWith("/")) File(args[1]) else File(currentDirectory, args[1])
        return try {
            val l1 = f1.readLines(); val l2 = f2.readLines()
            val sb = StringBuilder()
            l1.forEachIndexed { i, line ->
                val l2line = l2.getOrNull(i)
                if (line != l2line) { sb.append("< $line\n"); if (l2line != null) sb.append("> $l2line\n") }
            }
            if (l2.size > l1.size) l2.drop(l1.size).forEach { sb.append("> $it\n") }
            if (sb.isEmpty()) "Files are identical" else sb.toString().trimEnd()
        } catch (e: Exception) { "diff: ${e.message}" }
    }

    private fun handleStat(args: List<String>): String {
        if (args.isEmpty()) return "stat: missing operand"
        val f = if (args[0].startsWith("/")) File(args[0]) else File(currentDirectory, args[0])
        return if (!f.exists()) "stat: ${args[0]}: No such file" else """
File: ${f.absolutePath}
Size: ${f.length()} bytes   Type: ${if (f.isDirectory) "directory" else "regular file"}
Modified: ${java.util.Date(f.lastModified())}
Readable: ${f.canRead()}   Writable: ${f.canWrite()}""".trimIndent()
    }

    private fun handleFileType(args: List<String>): String {
        if (args.isEmpty()) return "file: missing operand"
        return args.joinToString("\n") { name ->
            val f = if (name.startsWith("/")) File(name) else File(currentDirectory, name)
            val ext = f.extension.lowercase()
            val type = when {
                !f.exists() -> "No such file"
                f.isDirectory -> "directory"
                ext in listOf("jpg","jpeg","png","gif","bmp","webp") -> "image file"
                ext in listOf("mp4","mkv","avi","mov","webm") -> "video file"
                ext in listOf("mp3","aac","ogg","flac","wav") -> "audio file"
                ext in listOf("apk","jar","zip","tar","gz","rar") -> "archive"
                ext in listOf("kt","java","py","js","ts","c","cpp","rs","go") -> "source code"
                ext in listOf("html","css","xml","json","yaml","yml") -> "markup/data"
                ext in listOf("sh","bash") -> "shell script"
                ext in listOf("txt","md","log") -> "text file"
                f.length() == 0L -> "empty file"
                else -> try { val b = f.readBytes().take(4); if (b[0] == 0x7f.toByte() && b[1] == 'E'.code.toByte()) "ELF binary" else "data" } catch (e: Exception) { "file" }
            }
            "${f.name}: $type"
        }
    }

    private val envOverrides = mutableMapOf<String, String>()
    private val aliases = mutableMapOf<String, String>()

    private fun handleSet(args: List<String>): String {
        if (args.isEmpty()) return envOverrides.entries.joinToString("\n") { "${it.key}=${it.value}" }
        val eq = args.firstOrNull { it.contains("=") }
        if (eq != null) {
            val (k, v) = eq.split("=", limit = 2)
            envOverrides[k] = v
            return ""
        }
        return ""
    }

    private fun handleSource(args: List<String>): String {
        if (args.isEmpty()) return "source: missing filename"
        val f = if (args[0].startsWith("/")) File(args[0]) else File(currentDirectory, args[0])
        return if (!f.exists()) "source: ${args[0]}: No such file" else executeShellCommand("sh \"${f.absolutePath}\"")
    }

    private fun handleAlias(args: List<String>, full: String): String {
        if (args.isEmpty()) return aliases.entries.joinToString("\n") { "alias ${it.key}='${it.value}'" }
        val eq = full.substringAfter("alias ").trim()
        if (eq.contains("=")) {
            val (k, v) = eq.split("=", limit = 2)
            aliases[k.trim()] = v.trim().removeSurrounding("'").removeSurrounding("\"")
            return ""
        }
        return "alias: ${args[0]}: not found"
    }

    private fun handleSysinfo(): String {
        val rt = Runtime.getRuntime()
        val root = File("/sdcard")
        return """
DISCORDIA TERMINAL — System Info
Device:   SM-X200 (Samsung Galaxy Tab A8)
Android:  ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})
CPU ABI:  ${android.os.Build.SUPPORTED_ABIS.joinToString(",")}
Storage:  ${root.totalSpace/1024/1024}MB total / ${root.freeSpace/1024/1024}MB free
JVM Mem:  ${rt.maxMemory()/1024/1024}MB max / ${rt.totalMemory()/1024/1024}MB allocated
App Ver:  Discordia Terminal v2.0.0
        """.trimIndent()
    }

    private fun handleDiskInfo(): String {
        val dirs = listOf("/sdcard", "/storage", "/data", "/")
        return dirs.joinToString("\n") { path ->
            val f = File(path)
            if (f.exists()) "${path.padEnd(20)} total=${f.totalSpace/1024/1024}MB free=${f.freeSpace/1024/1024}MB" else "$path: N/A"
        }
    }

    private fun handleNetInfo(): String {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
            interfaces.filter { it.isUp }.joinToString("\n") { iface ->
                val addrs = iface.inetAddresses.toList().filter { !it.isLoopbackAddress }.joinToString(", ") { it.hostAddress ?: "" }
                "${iface.name.padEnd(12)} ${if (addrs.isEmpty()) "no inet" else addrs}"
            }.ifEmpty { "No network interfaces found" }
        } catch (e: Exception) { "netinfo: ${e.message}" }
    }

    private fun getHelp(): String = """
╔══════════════════════════════════════════════════════╗
║       DISCORDIA TERMINAL v2.0 — Command Reference    ║
╠══════════════════════════════════════════════════════╣
║ Navigation:  cd, pwd, ls [-la]                       ║
║ Files:       cat, head, tail, touch, mkdir, rm -rf   ║
║              cp, mv, find, grep, diff, stat, file    ║
║              ln, wc, sort, uniq, cut                 ║
║ Editor:      nano/vi → opens IDE, open               ║
║ System:      echo, env, export, set, uname, date     ║
║              whoami, hostname, ps, kill, top, free   ║
║              df, du, sysinfo, diskinfo, netinfo       ║
║ Network:     ifconfig, ip, ping, curl, wget           ║
║ Archive:     tar, zip, unzip                          ║
║ Scripts:     sh, bash, source, ., alias               ║
║ Dev:         git, python3, node, npm                  ║
║ Paths:       basename, dirname, realpath, which       ║
║ Session:     history, clear, help, exit               ║
║ Other:       Any unlisted command → Android shell     ║
╚══════════════════════════════════════════════════════╝
    """.trimIndent()
}
