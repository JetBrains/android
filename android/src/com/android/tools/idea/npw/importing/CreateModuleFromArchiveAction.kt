/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.npw.importing

import com.android.SdkConstants
import com.android.tools.idea.gradle.parser.BuildFileKey
import com.android.tools.idea.gradle.parser.Dependency
import com.android.tools.idea.gradle.parser.Dependency.Scope
import com.android.tools.idea.gradle.parser.Dependency.Type
import com.android.tools.idea.gradle.parser.GradleBuildFile
import com.android.tools.idea.gradle.parser.GradleSettingsFile
import com.android.tools.idea.gradle.util.GradleUtil
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.Result
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LargeFileWriteRequestor
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import java.io.File
import java.io.IOException

/**
 * Wraps archive in a Gradle module.
 */
class CreateModuleFromArchiveAction(
  project: Project, private val gradlePath: String, archivePath: String,
  private val move: Boolean, private val containingModule: Module?
) : WriteCommandAction<Any?>(project, "create module $gradlePath") {
  private val archivePath = File(archivePath)

  @Throws(IOException::class)
  private fun addDependency(module: Module, gradlePath: String) {
    // TODO(qumeric): use GradleBuildModel instead
    val buildFile = GradleBuildFile.get(module) ?: throw IOException("Missing " + SdkConstants.FN_BUILD_GRADLE)
    val moduleRoot = VfsUtilCore.virtualToIoFile(buildFile.file.parent)
    val newDeps = buildFile.dependencies.mapNotNull { filterDependencyStatement(it as Dependency, moduleRoot) }.toMutableList()
    val scope = Scope.getDefaultScope(project)
    newDeps.add(Dependency(scope, Type.MODULE, gradlePath))
    buildFile.setValue(BuildFileKey.DEPENDENCIES, newDeps)
  }

  private fun filterDependencyStatement(dependency: Dependency, moduleRoot: File): Dependency? {
    val rawArguments: Any? = dependency.data
    if (dependency.type == Type.FILES && rawArguments != null) {
      val data = if (rawArguments is Array<*>) rawArguments as Array<String>
      else arrayOf(rawArguments.toString())
      val list = data.filterNot { jarFile ->
        val path = File(jarFile).takeIf { it.isAbsolute } ?: File(moduleRoot, jarFile)
        FileUtil.filesEqual(path, archivePath)
      }
      return when {
        list.isEmpty() -> null
        list.size == 1 -> Dependency(dependency.scope, dependency.type, list[0])
        else -> Dependency(dependency.scope, dependency.type, list.toTypedArray())
      }
    }
    return dependency
  }

  @Throws(Throwable::class)
  override fun run(result: Result<Any?>) {
    val moduleLocation = GradleUtil.getModuleDefaultPath(project.baseDir, gradlePath)
    try {
      val moduleRoot = VfsUtil.createDirectoryIfMissing(moduleLocation.absolutePath)
      val sourceFile = VfsUtil.findFileByIoFile(archivePath, true)
      if (sourceFile != null && moduleRoot != null) {
        val requestor = object : LargeFileWriteRequestor {}
        if (move) {
          sourceFile.move(requestor, moduleRoot)
        }
        else {
          sourceFile.copy(requestor, moduleRoot, sourceFile.name)
        }
        val buildGradle = moduleRoot.createChildData(this, SdkConstants.FN_BUILD_GRADLE)
        VfsUtil.saveText(buildGradle, getBuildGradleText(archivePath))
        GradleSettingsFile.getOrCreate(project).addModule(gradlePath, VfsUtilCore.virtualToIoFile(moduleRoot))
        if (move && containingModule != null) {
          addDependency(containingModule, gradlePath)
        }
      }
    }
    catch (e: IOException) {
      Logger.getInstance(CreateModuleFromArchiveAction::class.java).error(e)
    }
  }

  override fun isGlobalUndoAction() = true

  override fun getUndoConfirmationPolicy() = UndoConfirmationPolicy.REQUEST_CONFIRMATION
}

@VisibleForTesting
fun getBuildGradleText(jarName: File) = "configurations.maybeCreate(\"default\")\nartifacts.add(\"default\", file('${jarName.name}'))"
