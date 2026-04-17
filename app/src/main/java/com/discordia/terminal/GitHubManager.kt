package com.discordia.terminal

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

data class GitHubRepo(val name: String, val fullName: String, val defaultBranch: String, val isPrivate: Boolean)
data class WorkflowRun(val id: Long, val runNumber: Int, val status: String, val conclusion: String?, val createdAt: String, val htmlUrl: String)
data class Artifact(val id: Long, val name: String, val sizeBytes: Long, val downloadUrl: String)

class GitHubManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("discordia_github", Context.MODE_PRIVATE)

    fun getToken(): String = prefs.getString("token", "") ?: ""
    fun setToken(token: String) = prefs.edit().putString("token", token).apply()
    fun getOwner(): String = prefs.getString("owner", "") ?: ""
    fun setOwner(owner: String) = prefs.edit().putString("owner", owner).apply()
    fun getRepo(): String = prefs.getString("repo", "") ?: ""
    fun setRepo(repo: String) = prefs.edit().putString("repo", repo).apply()

    private fun request(method: String, endpoint: String, body: JSONObject? = null): JSONObject? {
        return try {
            val url = URL("https://api.github.com$endpoint")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.setRequestProperty("Authorization", "token ${getToken()}")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "DiscordiaTerminal")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            if (body != null) {
                conn.doOutput = true
                val bytes = body.toString().toByteArray()
                conn.setRequestProperty("Content-Length", bytes.size.toString())
                conn.outputStream.write(bytes)
            }

            val code = conn.responseCode
            val stream = if (code < 400) conn.inputStream else conn.errorStream
            val text = BufferedReader(InputStreamReader(stream)).readText()
            stream.close()
            if (text.startsWith("{")) JSONObject(text) else JSONObject().put("_raw", text).put("_code", code)
        } catch (e: Exception) {
            JSONObject().put("error", e.message)
        }
    }

    private fun requestArray(method: String, endpoint: String): JSONArray? {
        return try {
            val url = URL("https://api.github.com$endpoint")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.setRequestProperty("Authorization", "token ${getToken()}")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "DiscordiaTerminal")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            val text = BufferedReader(InputStreamReader(conn.inputStream)).readText()
            conn.inputStream.close()
            if (text.startsWith("[")) JSONArray(text) else null
        } catch (e: Exception) { null }
    }

    suspend fun validateToken(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val res = request("GET", "/user") ?: return@withContext Pair(false, "Network error")
        if (res.has("login")) Pair(true, "Authenticated as ${res.getString("login")}")
        else Pair(false, res.optString("message", "Invalid token"))
    }

    suspend fun getUserRepos(): List<GitHubRepo> = withContext(Dispatchers.IO) {
        val arr = requestArray("GET", "/user/repos?per_page=50&sort=updated") ?: return@withContext emptyList()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            GitHubRepo(o.getString("name"), o.getString("full_name"), o.optString("default_branch", "main"), o.optBoolean("private", false))
        }
    }

    suspend fun getOrCreateRef(branch: String = "main"): String? = withContext(Dispatchers.IO) {
        val owner = getOwner(); val repo = getRepo()
        val ref = request("GET", "/repos/$owner/$repo/git/refs/heads/$branch") ?: return@withContext null
        ref.optJSONObject("object")?.optString("sha")
    }

    suspend fun pushFile(filePath: String, content: String, commitMsg: String, branch: String = "main"): Boolean = withContext(Dispatchers.IO) {
        val owner = getOwner(); val repo = getRepo()
        // Check if file exists to get its SHA
        val existing = request("GET", "/repos/$owner/$repo/contents/$filePath")
        val fileSha = existing?.optString("sha")

        val b64 = Base64.getEncoder().encodeToString(content.toByteArray())
        val body = JSONObject().apply {
            put("message", commitMsg)
            put("content", b64)
            put("branch", branch)
            if (!fileSha.isNullOrEmpty()) put("sha", fileSha)
        }
        val res = request("PUT", "/repos/$owner/$repo/contents/$filePath", body) ?: return@withContext false
        res.has("content")
    }

    suspend fun pushMultipleFiles(files: Map<String, String>, commitMsg: String, branch: String = "main"): Result<String> = withContext(Dispatchers.IO) {
        val owner = getOwner(); val repo = getRepo()
        try {
            // Get latest commit SHA
            val refRes = request("GET", "/repos/$owner/$repo/git/refs/heads/$branch") ?: return@withContext Result.failure(Exception("Cannot get ref"))
            val latestSha = refRes.getJSONObject("object").getString("sha")
            val commitRes = request("GET", "/repos/$owner/$repo/git/commits/$latestSha") ?: return@withContext Result.failure(Exception("Cannot get commit"))
            val baseTreeSha = commitRes.getJSONObject("tree").getString("sha")

            // Create blobs
            val treeItems = JSONArray()
            for ((path, content) in files) {
                val blobBody = JSONObject().put("content", content).put("encoding", "utf-8")
                val blob = request("POST", "/repos/$owner/$repo/git/blobs", blobBody) ?: continue
                val blobSha = blob.optString("sha")
                if (blobSha.isNotEmpty()) {
                    treeItems.put(JSONObject().put("path", path).put("mode", "100644").put("type", "blob").put("sha", blobSha))
                }
            }

            // Create tree
            val treeRes = request("POST", "/repos/$owner/$repo/git/trees",
                JSONObject().put("base_tree", baseTreeSha).put("tree", treeItems)) ?: return@withContext Result.failure(Exception("Cannot create tree"))
            val treeSha = treeRes.getString("sha")

            // Create commit
            val commitBody = JSONObject().put("message", commitMsg).put("tree", treeSha).put("parents", JSONArray().put(latestSha))
            val newCommit = request("POST", "/repos/$owner/$repo/git/commits", commitBody) ?: return@withContext Result.failure(Exception("Cannot create commit"))
            val newCommitSha = newCommit.getString("sha")

            // Update ref
            request("PATCH", "/repos/$owner/$repo/git/refs/heads/$branch", JSONObject().put("sha", newCommitSha))
            Result.success(newCommitSha)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun triggerWorkflow(workflowFile: String = "build.yml", branch: String = "main"): Boolean = withContext(Dispatchers.IO) {
        val owner = getOwner(); val repo = getRepo()
        val body = JSONObject().put("ref", branch)
        val res = request("POST", "/repos/$owner/$repo/actions/workflows/$workflowFile/dispatches", body)
        res != null && !res.has("error")
    }

    suspend fun getWorkflowRuns(limit: Int = 5): List<WorkflowRun> = withContext(Dispatchers.IO) {
        val owner = getOwner(); val repo = getRepo()
        val res = request("GET", "/repos/$owner/$repo/actions/runs?per_page=$limit") ?: return@withContext emptyList()
        val arr = res.optJSONArray("workflow_runs") ?: return@withContext emptyList()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            WorkflowRun(o.getLong("id"), o.getInt("run_number"), o.getString("status"),
                o.optString("conclusion").ifEmpty { null }, o.getString("created_at"), o.getString("html_url"))
        }
    }

    suspend fun getRunArtifacts(runId: Long): List<Artifact> = withContext(Dispatchers.IO) {
        val owner = getOwner(); val repo = getRepo()
        val res = request("GET", "/repos/$owner/$repo/actions/runs/$runId/artifacts") ?: return@withContext emptyList()
        val arr = res.optJSONArray("artifacts") ?: return@withContext emptyList()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Artifact(o.getLong("id"), o.getString("name"), o.getLong("size_in_bytes"), o.getString("archive_download_url"))
        }
    }

    suspend fun downloadArtifact(artifact: Artifact, destDir: File): File? = withContext(Dispatchers.IO) {
        try {
            val url = URL(artifact.downloadUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "token ${getToken()}")
            conn.setRequestProperty("User-Agent", "DiscordiaTerminal")
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 30000
            conn.readTimeout = 60000
            destDir.mkdirs()
            val file = File(destDir, "${artifact.name}.zip")
            file.outputStream().use { out -> conn.inputStream.use { it.copyTo(out) } }
            conn.disconnect()
            file
        } catch (e: Exception) { null }
    }

    suspend fun createRepo(name: String, private: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().put("name", name).put("private", private).put("auto_init", true).put("description", "Created with Discordia Terminal")
        val res = request("POST", "/user/repos", body) ?: return@withContext false
        res.has("id")
    }
}
