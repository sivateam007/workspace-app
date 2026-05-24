package com.opencode.mobile.util

import android.os.Build
import android.os.Environment
import java.io.File

object StorageUtil {
    val projectsBasePath: String
        get() = "${Environment.getExternalStorageDirectory().absolutePath}/OpenCode/projects"

    val openCodeRoot: String
        get() = "${Environment.getExternalStorageDirectory().absolutePath}/OpenCode"

    fun getProjectDir(name: String): File = File(projectsBasePath, name)

    fun ensureDirs() {
        File(projectsBasePath).mkdirs()
    }

    fun hasManageStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }
}
