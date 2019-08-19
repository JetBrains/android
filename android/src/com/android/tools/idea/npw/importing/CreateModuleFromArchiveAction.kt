/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.tools.idea.gradle.parser.BuildFileStatement
import com.android.tools.idea.gradle.parser.Dependency
import com.android.tools.idea.gradle.parser.Dependency.Scope
import com.android.tools.idea.gradle.parser.Dependency.Type
import com.android.tools.idea.gradle.parser.GradleBuildFile
import com.android.tools.idea.gradle.parser.GradleSettingsFile
import com.android.tools.idea.gradle.util.GradleUtil
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.Iterables
import com.google.common.collect.Lists
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
import java.util.ArrayList

/**
 * Wraps archive in a Gradle module.
 */
class CreateModuleFromArchiveAction(
  private val myProject: Project, private val myGradlePath: String, archivePath: String,
  private val myMove: Boolean, private val myContainingModule: Module?
) : WriteCommandAction<Any?>(myProject, String.format("create module %1\$s", myGradlePath)) {
  private val myArchivePath = File(archivePath)

  @Throws(IOException::class)
  private fun addDependency(module: Module, gradlePath: String) {
    val buildFile = GradleBuildFile.get(module) ?: throw IOException("Missing " + SdkConstants.FN_BUILD_GRADLE)
    val dependencies: List<BuildFileStatement> = buildFile.dependencies
    val newDeps: MutableList<BuildFileStatement> = Lists.newArrayListWithCapacity( dependencies.size + 1)
    val moduleRoot = VfsUtilCore.virtualToIoFile(buildFile.file.parent)
    for (dependency in dependencies) {
      val newDep: BuildFileStatement? = filterDependencyStatement( dependency as Dependency, moduleRoot)
      if (newDep != null) {
        newDeps.add(newDep)
      }
    }
    val scope = Scope.getDefaultScope( myProject)
    newDeps.add( Dependency(scope, Type.MODULE, gradlePath))
    buildFile.setValue(BuildFileKey.DEPENDENCIES, newDeps)
  }

  private fun filterDependencyStatement(dependency: Dependency, moduleRoot: File): Dependency? {
    val rawArguments: Any? = dependency.data
    if (dependency.type == Type.FILES && rawArguments != null) {
      val data = if (rawArguments is Array<*>) rawArguments as Array<String>
      else arrayOf(rawArguments.toString())
      val list: ArrayList<String> = Lists.newArrayListWithCapacity(data.size)
      for (jarFile in data) {
        var path = File(jarFile)
        if (!path.isAbsolute) {
          path = File(moduleRoot, jarFile)
        }
        if (!FileUtil.filesEqual(path, myArchivePath)) {
          list.add(jarFile)
        }
      }
      return when {
        list.isEmpty() -> null
        list.size == 1 -> Dependency(dependency.scope, dependency.type, list[0])
        else -> Dependency(dependency.scope, dependency.type, Iterables.toArray(list, String::class.java))
      }
    }
    return dependency
  }

  @Throws(Throwable::class)
  override fun run(result: Result<Any?>) {
    val moduleLocation = GradleUtil.getModuleDefaultPath(myProject.baseDir, myGradlePath)
    try {
      val moduleRoot = VfsUtil.createDirectoryIfMissing( moduleLocation.absolutePath)
      val sourceFile = VfsUtil.findFileByIoFile(myArchivePath, true)
      if (sourceFile != null && moduleRoot != null) {
        val requestor: LargeFileWriteRequestor = object : LargeFileWriteRequestor {}
        if (myMove) {
          sourceFile.move(requestor, moduleRoot)
        }
        else {
          sourceFile.copy(requestor, moduleRoot, sourceFile.name)
        }
        val buildGradle = moduleRoot.createChildData(this, SdkConstants.FN_BUILD_GRADLE)
        VfsUtil.saveText(buildGradle, getBuildGradleText(myArchivePath))
        GradleSettingsFile.getOrCreate(myProject).addModule(myGradlePath, VfsUtilCore.virtualToIoFile( moduleRoot))
        if (myMove && myContainingModule != null) {
          addDependency(myContainingModule, myGradlePath)
        }
      }
    }
    catch (e: IOException) {
      Logger.getInstance(CreateModuleFromArchiveAction::class.java).error(e)
    }
  }

  override fun isGlobalUndoAction(): Boolean = true

  override fun getUndoConfirmationPolicy() = UndoConfirmationPolicy.REQUEST_CONFIRMATION
}

@VisibleForTesting
fun getBuildGradleText(jarName: File): String =
  String.format("configurations.maybeCreate(\"default\")\n" + "artifacts.add(\"default\", file('%1\$s'))", jarName.name)
