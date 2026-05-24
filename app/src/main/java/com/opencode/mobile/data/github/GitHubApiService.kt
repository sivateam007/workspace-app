package com.opencode.mobile.data.github

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class GitHubRepo(
    val name: String,
    val fullName: String,
    val cloneUrl: String,
    val defaultBranch: String
)

data class GitHubFile(
    val name: String,
    val path: String,
    val type: String,
    val downloadUrl: String?
)

class GitHubApiService(private val token: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    private fun authRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github.v3+json")
            .build()
    }

    private fun authPost(url: String, body: String): Request {
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github.v3+json")
            .post(body.toRequestBody(jsonMediaType))
            .build()
    }

    fun listRepos(): List<GitHubRepo> {
        val request = authRequest("https://api.github.com/user/repos?per_page=50")
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty response from GitHub")
        if (!response.isSuccessful) {
            val msg = try { JSONObject(body).optString("message", response.message) } catch (_: Exception) { response.message }
            throw Exception("GitHub error ${response.code}: $msg")
        }
        val jsonArray = JSONArray(body)
        return (0 until jsonArray.length()).map { i ->
            val repo = jsonArray.getJSONObject(i)
            GitHubRepo(
                name = repo.getString("name"),
                fullName = repo.getString("full_name"),
                cloneUrl = repo.getString("clone_url"),
                defaultBranch = repo.getString("default_branch")
            )
        }
    }

    fun getRepoContents(owner: String, repo: String, path: String = ""): List<GitHubFile> {
        val request = authRequest("https://api.github.com/repos/$owner/$repo/contents/$path")
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return emptyList()
        val jsonArray = JSONArray(body)
        return (0 until jsonArray.length()).map { i ->
            val file = jsonArray.getJSONObject(i)
            GitHubFile(
                name = file.getString("name"),
                path = file.getString("path"),
                type = file.getString("type"),
                downloadUrl = file.optString("download_url", null)
            )
        }
    }

    fun downloadFile(downloadUrl: String): String {
        val request = Request.Builder().url(downloadUrl).build()
        val response = client.newCall(request).execute()
        return response.body?.string() ?: ""
    }

    fun downloadRepoToLocal(owner: String, repo: String, localPath: String) {
        downloadDirectory(owner, repo, "", localPath)
    }

    private fun downloadDirectory(owner: String, repo: String, path: String, localPath: String) {
        val contents = getRepoContents(owner, repo, path)
        val targetDir = File(localPath, path)
        targetDir.mkdirs()

        for (item in contents) {
            if (item.type == "dir") {
                downloadDirectory(owner, repo, item.path, localPath)
            } else if (item.downloadUrl != null) {
                val content = downloadFile(item.downloadUrl)
                val file = File(targetDir, item.name)
                file.writeText(content)
            }
        }
    }

    fun getDefaultBranch(owner: String, repo: String): String {
        val request = authRequest("https://api.github.com/repos/$owner/$repo")
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return "main"
        return JSONObject(body).optString("default_branch", "main")
    }

    fun getRefHeadSha(owner: String, repo: String, branch: String): String {
        val request = authRequest("https://api.github.com/repos/$owner/$repo/git/refs/heads/$branch")
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Could not get ref")
        return JSONObject(body).getJSONObject("object").getString("sha")
    }

    fun createBlob(owner: String, repo: String, content: String): String {
        val json = JSONObject().apply {
            put("content", content)
            put("encoding", "utf-8")
        }
        val request = authPost("https://api.github.com/repos/$owner/$repo/git/blobs", json.toString())
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Could not create blob")
        return JSONObject(body).getString("sha")
    }

    fun createTree(owner: String, repo: String, baseTreeSha: String, files: List<Pair<String, String>>): String {
        val treeArray = JSONArray()
        files.forEach { (path, blobSha) ->
            treeArray.put(JSONObject().apply {
                put("path", path)
                put("mode", "100644")
                put("type", "blob")
                put("sha", blobSha)
            })
        }
        val json = JSONObject().apply {
            put("base_tree", baseTreeSha)
            put("tree", treeArray)
        }
        val request = authPost("https://api.github.com/repos/$owner/$repo/git/trees", json.toString())
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Could not create tree")
        return JSONObject(body).getString("sha")
    }

    fun createCommit(owner: String, repo: String, message: String, treeSha: String, parentSha: String): String {
        val json = JSONObject().apply {
            put("message", message)
            put("tree", treeSha)
            put("parents", JSONArray(listOf(parentSha)))
        }
        val request = authPost("https://api.github.com/repos/$owner/$repo/git/commits", json.toString())
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Could not create commit")
        return JSONObject(body).getString("sha")
    }

    fun updateRef(owner: String, repo: String, branch: String, commitSha: String) {
        val json = JSONObject().apply {
            put("sha", commitSha)
            put("force", true)
        }
        val request = authPost("https://api.github.com/repos/$owner/$repo/git/refs/heads/$branch", json.toString())
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Failed to update ref: ${response.body?.string()}")
    }

    fun getCommitTreeSha(owner: String, repo: String, commitSha: String): String {
        val request = authRequest("https://api.github.com/repos/$owner/$repo/git/commits/$commitSha")
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Could not get commit")
        return JSONObject(body).getJSONObject("tree").getString("sha")
    }

    fun commitChanges(owner: String, repo: String, branch: String, message: String, files: List<Pair<String, String>>) {
        val headCommitSha = getRefHeadSha(owner, repo, branch)
        val headTreeSha = getCommitTreeSha(owner, repo, headCommitSha)
        val blobShas = files.map { (path, content) ->
            val blobSha = createBlob(owner, repo, content)
            path to blobSha
        }
        val treeSha = createTree(owner, repo, headTreeSha, blobShas)
        val commitSha = createCommit(owner, repo, message, treeSha, headCommitSha)
        updateRef(owner, repo, branch, commitSha)
    }
}
