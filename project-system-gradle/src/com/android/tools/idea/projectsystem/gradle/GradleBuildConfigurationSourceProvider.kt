/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.tools.idea.flags.DeclarativeStudioSupport
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.projectsystem.BuildConfigurationSourceProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidRootUtil
import org.jetbrains.kotlin.utils.yieldIfNotNull

private const val BUILD_ORDER_BASE = 1_000_000
private const val MODULE_ORDER_BASE = 2_000_000
private const val MODULE_SECONDARY_OFFSET = 500_000
private const val BUILD_WIDE_ORDER_BASE = 3_000_000

class GradleBuildConfigurationSourceProvider(private val project: Project) : BuildConfigurationSourceProvider {

  private data class ModuleDesc(
    val module: Module,
    val projectPath: GradleHolderProjectPath,
    val gradleIdentityPath: String,
  ) {

    val orderBase: Int = when {
      gradleIdentityPath == ":" -> 0
      projectPath.path == ":" -> BUILD_ORDER_BASE
      else -> MODULE_ORDER_BASE
    }

    val displayPath: String = gradleIdentityPath

    val projectDisplayName: String = when {
      gradleIdentityPath == ":" -> PROJECT_PREFIX + module.name
      projectPath.path == ":" -> BUILD_PREFIX + gradleIdentityPath
      else -> MODULE_PREFIX + displayPath
    }

    companion object {
      val CONFIG_FILE_GROUP_COMPARATOR: Comparator<ModuleDesc> =
        compareBy<ModuleDesc> { it.projectPath.buildRootDir }.thenBy { it.projectPath.path }
    }
  }

  private val holderModules: List<ModuleDesc> =
    ModuleManager.getInstance(project).modules
      .mapNotNull {
        val gradleHolderProjectPath = it.getGradleProjectPath()?.toHolder() ?: return@mapNotNull null
        val gradleIdentityPath = it.getGradleIdentityPath() ?: return@mapNotNull null
        gradleHolderProjectPath.resolveIn(project)?.let { holderModule ->
          ModuleDesc(
            module = holderModule,
            projectPath = gradleHolderProjectPath,
            gradleIdentityPath = gradleIdentityPath
          )
        }
      }
      .sortedWith(ModuleDesc.CONFIG_FILE_GROUP_COMPARATOR)

  private data class ConfigurationFileImpl(
    override val file: VirtualFile,
    override val displayName: String,
    override val groupOrder: Int
  ) : BuildConfigurationSourceProvider.ConfigurationFile

  private fun VirtualFile.describe(displayName: String, legacyOrder: Int):ConfigurationFileImpl {
    return ConfigurationFileImpl(this, displayName, legacyOrder)
  }

  override fun getBuildConfigurationFiles(): List<BuildConfigurationSourceProvider.ConfigurationFile> {
    return findConfigurationFiles()
      .groupBy { it.file }
      .map { it.value.last() }
  }

  override fun contains(file: VirtualFile): Boolean {
    return findConfigurationFiles().any { it.file == file }
  }

  private fun findConfigurationFiles() = sequence {
    holderModules.forEachIndexed { index, module ->
      yieldIfNotNull(
        GradleProjectSystemUtil.getGradleModuleModel(module.module)
          ?.buildFilePath
          ?.let { VfsUtil.findFileByIoFile(it, false) }
          ?.describe(module.projectDisplayName, module.orderBase + index)
      )

      // include all .gradle and ProGuard files from each module
      for (file in findAllGradleScriptsInModule(module.module)) {
        yield(
          file.describe(
            if (file.fileType === proguardFileType) {
              "ProGuard Rules for \"${module.displayPath}\""
            } else {
              module.projectDisplayName
            },
            (module.orderBase + index) + if (file.fileType === proguardFileType) MODULE_SECONDARY_OFFSET else 0
          )
        )
      }
    }

    val projectRootFolder = project.baseDir
    if (projectRootFolder != null) {

      yieldIfNotNull(
        projectRootFolder.findChild(SdkConstants.FN_GRADLE_PROPERTIES)
          ?.describe("Project Properties", BUILD_WIDE_ORDER_BASE)
      )


      yieldIfNotNull(
        projectRootFolder.findFileByRelativePath(FileUtilRt.toSystemIndependentName(
          GradleProjectSystemUtil.GRADLEW_PROPERTIES_PATH))
          ?.describe("Gradle Version", BUILD_WIDE_ORDER_BASE)
      )

      yieldIfNotNull(
        projectRootFolder.findChild(SdkConstants.FN_SETTINGS_GRADLE)
          ?.describe("Project Settings", BUILD_WIDE_ORDER_BASE)
      )

      yieldIfNotNull(
        projectRootFolder.findChild(SdkConstants.FN_SETTINGS_GRADLE_KTS)
          ?.describe("Project Settings", BUILD_WIDE_ORDER_BASE)
      )

      if (DeclarativeStudioSupport.isEnabled()) {
        yieldIfNotNull(
          projectRootFolder.findChild(SdkConstants.FN_SETTINGS_GRADLE_DECLARATIVE)
            ?.describe("Project Settings", BUILD_WIDE_ORDER_BASE)
        )
      }

      projectRootFolder.findChild("gradle")?.takeIf { it.isDirectory }?.let { gradle ->
        gradle.children.filter { !it.isDirectory && it.name.endsWith(SdkConstants.DOT_VERSIONS_DOT_TOML) }.forEach {
          yield(it.describe("Version Catalog", BUILD_WIDE_ORDER_BASE))
        }
      }
      yieldIfNotNull(
        projectRootFolder.findChild(SdkConstants.FN_LOCAL_PROPERTIES)
          ?.describe("SDK Location", BUILD_WIDE_ORDER_BASE)
      )
    }

    if (!ApplicationManager.getApplication().isUnitTestMode) {
      val userSettingsFile =
        GradleProjectSystemUtil.getUserGradlePropertiesFile(project)
      val file = VfsUtil.findFileByIoFile(userSettingsFile, false)
      if (file != null) {
        yield(file.describe("Global Properties", BUILD_WIDE_ORDER_BASE))
      }
    }
  }

  private fun findAllGradleScriptsInModule(module: Module): List<VirtualFile> {
    val moduleRootFolderPath = AndroidRootUtil.findModuleRootFolderPath(module) ?: return emptyList()
    val moduleRootFolder = VfsUtil.findFileByIoFile(moduleRootFolderPath, false)?.takeUnless { it.children == null } ?: return emptyList()

    val files = mutableListOf<VirtualFile>()
    for (child in moduleRootFolder.children) {
      if (!child.isValid ||
        child.isDirectory ||
        (
          !child.name.endsWith(SdkConstants.EXT_GRADLE) &&
            !child.name.endsWith(SdkConstants.EXT_GRADLE_KTS) &&
            !child.isGradleDeclarativeBuildFile() &&
            child.fileType !== proguardFileType
          )
      ) {
        continue
      }

      // When a project is imported via unit tests, there is a ijinitXXXX.gradle file created somehow, exclude that.
      if (ApplicationManager.getApplication().isUnitTestMode &&
        (child.name.startsWith("ijinit") || child.name.startsWith("asLocalRepo"))
      ) {
        continue
      }
      files.add(child)
    }
    return files
  }

  private fun VirtualFile.isGradleDeclarativeBuildFile() =
    DeclarativeStudioSupport.isEnabled() && name.endsWith(SdkConstants.EXT_GRADLE_DECLARATIVE)

  private val proguardFileType: FileType = FileTypeRegistry.getInstance().findFileTypeByName("Shrinker Config File")
}

private const val MODULE_PREFIX = "Module "
private const val PROJECT_PREFIX = "Project: "
private const val BUILD_PREFIX = "Included build: "
