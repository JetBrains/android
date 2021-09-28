/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.idea.gradle.model.IdeModuleLibrary
import com.android.tools.idea.gradle.model.IdeModuleSourceSet
import com.android.utils.FileUtils
import com.intellij.openapi.module.Module
import org.jetbrains.annotations.SystemIndependent
import java.io.File
import com.android.tools.idea.gradle.util.GradleProjectPath as GradleProjectPathFromCore
import com.android.tools.idea.gradle.util.getGradleProjectPath as getGradleProjectPathFromCore

data class GradleProjectPath(
  val buildRoot: @SystemIndependent String,
  val path: String,
  val sourceSet: IdeModuleSourceSet
) {
  constructor (buildRoot: File, path: String, sourceSet: IdeModuleSourceSet): this(
    FileUtils.toSystemIndependentPath(buildRoot.path),
    path,
    sourceSet
  )
}

fun GradleProjectPathFromCore.toGradleProjectPath(): GradleProjectPath =
  GradleProjectPath(
    projectRoot,
    gradleProjectPath,
    IdeModuleSourceSet.MAIN
  )
fun Module.getGradleProjectPath(): GradleProjectPath? = getGradleProjectPathFromCore()?.toGradleProjectPath()
fun IdeModuleLibrary.getGradleProjectPath(): GradleProjectPath = GradleProjectPath(buildId, projectPath, sourceSet)