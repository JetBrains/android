/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import java.nio.file.Path
import kotlin.io.path.relativeToOrSelf

/**
 * Converts a path to a project relative path.
 *
 * If `path` is relative to the project directory (see [Project.guessProjectDir]), returns the relative portion.
 * Otherwise, returns the path unchanged.
 *
 * Note:
 * A project can have files that are not under the project base directory. For these files, this function will return an absolute path.
 */
fun Path.relativeToProject(project: Project): Path {
    val relative = relativeToOrSelf(project.guessProjectDir()!!.toNioPath())
    return if (relative.startsWith("..")) this else relative
}

/**
 * Converts a path to an absolute path
 *
 * If `path` is a relative path, returns an absolute path by prepending the path with the project directory (see [Project.guessProjectDir]).
 *
 * Otherwise, returns the path unchanged.
 */
fun Path.absoluteInProject(project: Project): Path {
    val projectDir = project.guessProjectDir()!!.toNioPath()
    return when {
        isAbsolute -> this
        else -> projectDir.resolve(this)
    }
}
