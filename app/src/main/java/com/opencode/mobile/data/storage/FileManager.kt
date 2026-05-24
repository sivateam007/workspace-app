package com.opencode.mobile.data.storage

import java.io.File

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val extension: String? = null
)

class FileManager(private val rootPath: String) {

    fun listFiles(path: String = rootPath): List<FileItem> {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.sortedBy { !it.isDirectory }
            ?.map { file ->
                FileItem(
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = file.isDirectory,
                    extension = if (file.isFile) file.extension else null
                )
            } ?: emptyList()
    }

    fun readFile(path: String): String {
        return File(path).readText()
    }

    fun writeFile(path: String, content: String) {
        File(path).apply {
            parentFile?.mkdirs()
            writeText(content)
        }
    }

    fun createFile(path: String): Boolean {
        return File(path).createNewFile()
    }

    fun deleteFile(path: String): Boolean {
        return File(path).delete()
    }

    fun createDirectory(path: String): Boolean {
        return File(path).mkdirs()
    }

    fun exists(path: String): Boolean {
        return File(path).exists()
    }

    fun getExtension(path: String): String {
        return File(path).extension
    }

    fun getParentPath(path: String): String {
        return File(path).parent ?: rootPath
    }

    fun isDirectory(path: String): Boolean {
        return File(path).isDirectory
    }

    fun getFileName(path: String): String {
        return File(path).name
    }

    fun getFileSize(path: String): Long {
        return File(path).length()
    }
}
