/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.model.impl

import java.io.File

/** The "build" folder paths per module.  */
class BuildFolderPaths {

    private val buildFolderPathsByModule: MutableMap<String, MutableMap<String, File>> =
        mutableMapOf()

    /**
     * The build identifier of root project.
     */
    var rootBuildId: String? = null

    /**
     * Stores the [buildFolder] path for the module specified by [buildId] and [moduleGradlePath].
     */
    fun addBuildFolderMapping(
        buildId: String, moduleGradlePath: String, buildFolder: File
    ) {
        buildFolderPathsByModule
            .getOrPut(buildId) { mutableMapOf() }[moduleGradlePath] = buildFolder
    }

    /**
     * Finds and returns the path of the "build" folder for the given [moduleGradlePath] and
     * [buildId]; or `null` if the path or build id is not found.
     */
    fun findBuildFolderPath(moduleGradlePath: String, buildId: String?): File? {
        // buildId can be null for root project or for pre-3.1 plugin.
        return buildFolderPathsByModule[buildId ?: rootBuildId]?.get(moduleGradlePath)
    }
}
