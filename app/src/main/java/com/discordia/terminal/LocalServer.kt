package com.discordia.terminal

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream

class LocalServer(port: Int = 8080, private val rootDir: File = File("/sdcard")) : NanoHTTPD(port) {

    var requestLog = mutableListOf<String>()

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        val method = session.method.toString()
        val ip = session.remoteIpAddress ?: "unknown"
        requestLog.add("[$method] $ip → $uri")
        if (requestLog.size > 200) requestLog.removeAt(0)

        val file = File(rootDir, uri.trimStart('/'))
        return when {
            file.isDirectory -> serveDirectory(file, uri)
            file.isFile -> serveFile(file)
            uri == "/" -> serveIndex()
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, "<h1>404 Not Found</h1>")
        }
    }

    private fun serveIndex(): Response {
        val html = """
<!DOCTYPE html><html><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Discordia Local Server</title>
<style>body{font-family:monospace;background:#0d1117;color:#58a6ff;padding:20px}
a{color:#79c0ff}h1{color:#58a6ff}.info{color:#8b949e;font-size:12px}</style>
</head><body>
<h1>🖥 Discordia Terminal — Local Server</h1>
<p class="info">Device: Samsung SM-X200 | Android 14</p>
<ul>
<li><a href="/sdcard">/sdcard — Device Storage</a></li>
<li><a href="/log">Request Log</a></li>
</ul>
<p class="info">Browse files on your device via this local HTTP server.</p>
</body></html>""".trimIndent()
        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, html)
    }

    private fun serveDirectory(dir: File, uri: String): Response {
        val files = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
        val parent = if (uri != "/" && uri != "") "<li><a href=\"${uri.substringBeforeLast('/')}\">.. (parent)</a></li>" else ""
        val items = files.joinToString("\n") { f ->
            val icon = if (f.isDirectory) "📁" else "📄"
            val link = "$uri/${f.name}".replace("//", "/")
            "<li>$icon <a href=\"$link\">${f.name}${if (f.isDirectory) "/" else ""}</a></li>"
        }
        val html = """
<!DOCTYPE html><html><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>$uri</title>
<style>body{font-family:monospace;background:#0d1117;color:#e6edf3;padding:20px}
a{color:#79c0ff}ul{list-style:none;padding:0}li{margin:4px 0}</style>
</head><body>
<h2>📂 $uri</h2><ul>$parent$items</ul>
</body></html>""".trimIndent()
        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, html)
    }

    private fun serveFile(file: File): Response {
        return try {
            val mime = getMimeType(file.extension)
            newChunkedResponse(Response.Status.OK, mime, FileInputStream(file))
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error: ${e.message}")
        }
    }

    private fun getMimeType(ext: String): String = when (ext.lowercase()) {
        "html", "htm" -> MIME_HTML
        "css" -> "text/css"
        "js" -> "application/javascript"
        "json" -> "application/json"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "svg" -> "image/svg+xml"
        "pdf" -> "application/pdf"
        "txt", "log", "md" -> MIME_PLAINTEXT
        "apk" -> "application/vnd.android.package-archive"
        "zip" -> "application/zip"
        "mp3" -> "audio/mpeg"
        "mp4" -> "video/mp4"
        else -> "application/octet-stream"
    }
}
