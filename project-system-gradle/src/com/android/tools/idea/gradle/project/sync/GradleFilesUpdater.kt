/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.SdkConstants.FN_GRADLE_CONFIG_PROPERTIES
import com.android.tools.idea.flags.DeclarativeStudioSupport
import com.android.tools.idea.gradle.dsl.utils.FN_GRADLE_PROPERTIES
import com.android.tools.idea.gradle.dsl.utils.FN_SETTINGS_GRADLE
import com.android.tools.idea.gradle.dsl.utils.FN_SETTINGS_GRADLE_DECLARATIVE
import com.android.tools.idea.gradle.dsl.utils.FN_SETTINGS_GRADLE_KTS
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.gradle.util.GradleWrapper
import com.android.tools.idea.util.toIoFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VFileProperty
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@Service(Service.Level.PROJECT)
class GradleFilesUpdater(private val project: Project, private val cs: CoroutineScope) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): GradleFilesUpdater = project.getService(GradleFilesUpdater::class.java)

    @JvmStatic
    private fun hash(virtualFile: VirtualFile) = ProgressManager.checkCanceled().let { GradleFiles.computeHash(virtualFile) }
  }

  private val VirtualFile.isRegularFile
    get() = this.isFile && !this.`is`(VFileProperty.SPECIAL) && !this.`is`(VFileProperty.HIDDEN) && this.toIoFile().exists()

  fun scheduleUpdateFileHashes(callback: (Result) -> Unit) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      ApplicationManager.getApplication().invokeLater { updateFileHashes(callback) }
    }
    else {
      cs.launch(Dispatchers.Default) {
        callback(computeFileHashes())
      }
    }
  }

  private fun updateFileHashes(callback: (Result) -> Unit) {
    val result = runBlocking { computeFileHashes() }
    callback(result)
  }

  private suspend fun computeFileHashes(): Result {
    suspend fun computeWrapperPropertiesHash(): Result {
      return readAction {
        GradleWrapper.find(project)?.propertiesFile?.takeIf { it.isRegularFile }?.let { Result.from(it) } ?: return@readAction Result.EMPTY
      }
    }
    suspend fun computeModuleHashes(module: Module): Result {
      return readAction {
        val files = mutableSetOf<VirtualFile>()
        val externalBuildFiles = mutableSetOf<VirtualFile>()
        GradleProjectSystemUtil.getGradleBuildFile(module)?.let { buildFile ->
          if (!buildFile.isRegularFile) return@let
          files.add(buildFile)
        }
        ProgressManager.checkCanceled()
        NdkModuleModel.get(module)?.let { ndkModuleModel ->
          for (file in ndkModuleModel.buildFiles) {
            if (file.isFile) {
              ProgressManager.checkCanceled()
              val virtualFile = VfsUtil.findFileByIoFile(file, false) ?: continue
              if (!virtualFile.isRegularFile) return@let
              externalBuildFiles.add(virtualFile)
              files.add(virtualFile)
            }
          }
        }
        Result.from(files, externalBuildFiles)
      }
    }
    suspend fun computeRootHashes(root: VirtualFile): Result {
      if (!root.isDirectory) return Result.EMPTY
      val fileNames = listOfNotNull(FN_SETTINGS_GRADLE, FN_SETTINGS_GRADLE_KTS, FN_GRADLE_PROPERTIES,
                                    FN_SETTINGS_GRADLE_DECLARATIVE.takeIf { DeclarativeStudioSupport.isEnabled() })
      return readAction {
        val files = fileNames.mapNotNull {
          val virtualFile = root.findChild(it) ?: return@mapNotNull null
          if (!virtualFile.isRegularFile) return@mapNotNull null
          return@mapNotNull virtualFile
        }.toSet()
        Result.from(files)
      }
    }
    suspend fun computeVersionCatalogHashes(gradle: VirtualFile): Result {
      if (!gradle.isDirectory) return Result.EMPTY
      return readAction {
        val files = gradle.children.mapNotNull { child ->
          ProgressManager.checkCanceled()
          if (child.isRegularFile && child.name.endsWith(".versions.toml")) child else null
        }.toSet()
        Result.from(files)
      }
    }
    suspend fun computeGradleCacheHash(dotGradle: VirtualFile): Result {
      if (!dotGradle.isDirectory) return Result.EMPTY
      return readAction {
        dotGradle.findChild(FN_GRADLE_CONFIG_PROPERTIES)?.takeIf { it.isRegularFile }?.let { Result.from(it) } ?: Result.EMPTY
      }
    }
    val deferreds = mutableListOf<Deferred<Result>>()
    withContext(Dispatchers.IO) {
      async { computeWrapperPropertiesHash() }.let { deferreds.add(it) }
      // TODO(xof): won't this do N (module-per-source-set) times too much work?
      ModuleManager.getInstance(project).modules.forEach {
        async { computeModuleHashes(it) }.let { deferreds.add(it) }
      }
      project.guessProjectDir()?.let { root ->
        async { computeRootHashes(root) }.let { deferreds.add(it) }
        root.findChild("gradle")?.let { gradle -> async { computeVersionCatalogHashes(gradle) }.let { deferreds.add(it) } }
        root.findChild(".gradle")?.let { dotGradle -> async { computeGradleCacheHash(dotGradle) }.let { deferreds.add(it) } }
      }
    }
    return deferreds.awaitAll().let { results ->
      results.fold(mutableMapOf<VirtualFile,Int>() to mutableSetOf<VirtualFile>()) { acc, result ->
        acc.first.putAll(result.hashes)
        acc.second.addAll(result.externalBuildFiles)
        acc
      }
    }.let { Result(it.first, it.second) }
  }

  data class Result(
    val hashes: Map<VirtualFile, Int>,
    val externalBuildFiles: Set<VirtualFile>,
  ) {
    companion object {
      val EMPTY = Result(mapOf(), setOf())
      fun from(file: VirtualFile): Result = from(setOf(file))
      fun from(files: Set<VirtualFile>): Result = from(files, setOf())

      fun from(files: Set<VirtualFile>, externalBuildFiles: Set<VirtualFile>): Result =
        Result(files.mapNotNull { f -> hash(f)?.let { h -> f to h } }.toMap(), externalBuildFiles)
    }
  }
}

