/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.importing

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.Projects
import com.android.tools.idea.flags.StudioFlags.GRADLE_USES_LOCAL_JAVA_HOME_FOR_NEW_CREATED_PROJECTS
import com.android.tools.idea.gradle.config.GradleConfigManager
import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.android.tools.idea.gradle.project.Info
import com.android.tools.idea.gradle.project.ProjectMigrationsPersistentState
import com.android.tools.idea.gradle.project.sync.SdkSync
import com.android.tools.idea.gradle.project.sync.jdk.JdkUtils
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.gradle.util.LocalProperties
import com.android.tools.idea.io.FilePaths
import com.android.tools.idea.project.ANDROID_PROJECT_TYPE
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.util.ToolWindows
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectTypeService
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.CompilerProjectExtension
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.ExceptionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.gradle.service.project.open.setupGradleSettings
import org.jetbrains.plugins.gradle.settings.GradleDefaultProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.USE_GRADLE_LOCAL_JAVA_HOME
import java.io.File
import java.io.IOException
import java.nio.file.Path

/**
 * Imports an Android-Gradle project without showing the "Import Project" Wizard UI.
 */
class GradleProjectImporter @NonInjectable @VisibleForTesting internal constructor(
  private val mySdkSync: SdkSync,
  private val myTopLevelModuleFactory: TopLevelModuleFactory,
) {
  constructor() : this(SdkSync.getInstance(), TopLevelModuleFactory())

  /**
   * Ensures presence of the top level Gradle build file and the .idea directory and, additionally, performs cleanup of the libraries
   * storage to force their re-import.
   */
  fun importAndOpenProjectCore(
    projectToClose: Project?,
    forceOpenInNewFrame: Boolean,
    projectFolder: VirtualFile
  ): Project? {
    val projectFolderPath = VfsUtilCore.virtualToIoFile(projectFolder)
    try {
      return ProjectManagerEx.getInstanceEx().openProject(projectFolderPath.toPath(), OpenProjectTask {
        this.forceOpenInNewFrame = forceOpenInNewFrame
        this.projectToClose = projectToClose
        isNewProject = false
        useDefaultProjectAsTemplate = false
        beforeOpen = {
          // The scope of this is rather large to mimic old behaviour, it could likely be improved
          withContext(Dispatchers.EDT) {
            setUpLocalProperties(projectFolderPath)
            configureNewProject(it)
            importProjectNoSync(Request(it))
          }
          true
        }
      })
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        ExceptionUtil.rethrowUnchecked(e)
      }
      Messages.showErrorDialog(e.message, "Project Import")
      logger.error(e)
      return null
    }
  }

  @Throws(IOException::class)
  private fun setUpLocalProperties(projectFolderPath: File) {
    try {
      val localProperties = LocalProperties(projectFolderPath)
      mySdkSync.syncIdeAndProjectAndroidSdks(localProperties, ProjectUtil.findProject(projectFolderPath.toPath()))
    }
    catch (e: IOException) {
      logger.info("Failed to sync SDKs", e)
      Messages.showErrorDialog(e.message, "Project Import")
      throw e
    }
  }

  private val logger: Logger
    get() = Logger.getInstance(javaClass)

  @Throws(IOException::class)
  fun importProjectNoSync(request: Request) {
    val newProject = request.project
    val projectInfo = GradleProjectInfo.getInstance(newProject)
    projectInfo.isNewProject = request.isNewProject
    WriteAction.runAndWait<RuntimeException> {
      if (request.javaLanguageLevel != null) {
        val extension = LanguageLevelProjectExtension.getInstance(newProject)
        if (extension != null) {
          extension.languageLevel = request.javaLanguageLevel!!
        }
      }

      // In practice, it really does not matter where the compiler output folder is. Gradle handles that. This is done just to please
      // IDEA.
      val compilerOutputFolderPath = File(Projects.getBaseDirPath(newProject), FileUtil.join(GradleProjectSystemUtil.BUILD_DIR_DEFAULT_NAME, "classes"))
      val compilerOutputFolderUrl = FilePaths.pathToIdeaUrl(compilerOutputFolderPath)
      val compilerProjectExt = CompilerProjectExtension.getInstance(newProject)!!
      compilerProjectExt.setCompilerOutputUrl(compilerOutputFolderUrl)

      // This allows to customize UI when android project is opened inside IDEA with android plugin.
      ProjectTypeService.setProjectType(newProject, ANDROID_PROJECT_TYPE)
      myTopLevelModuleFactory.createOrConfigureTopLevelModule(newProject)
    }
    ExternalSystemUtil.invokeLater(newProject) { ToolWindows.activateProjectView(newProject) }
  }

  /**
   * Creates a new not configured project in a given location.
   */
  @JvmOverloads
  fun createProject(projectName: String, projectFolderPath: File, useDefaultProjectAsTemplate: Boolean = false): Project {
    Info.beginInitializingGradleProjectAt(projectFolderPath).use { ignored ->
      val newProject = ProjectManagerEx.getInstanceEx().newProject(
        Path.of(projectFolderPath.path),
        OpenProjectTask {
          this.projectName = projectName
          this.useDefaultProjectAsTemplate = useDefaultProjectAsTemplate
        }
      ) ?: throw NullPointerException("Failed to create a new project")
      configureNewProject(newProject)
      return newProject
    }
  }

  class Request(@JvmField val project: Project) {
    @JvmField
    var javaLanguageLevel: LanguageLevel? = null

    @JvmField
    var isNewProject = false
  }

  companion object {
    @JvmStatic
    fun getInstance(): GradleProjectImporter = ApplicationManager.getApplication().getService(GradleProjectImporter::class.java)

    internal fun beforeOpen(project: Project) {
      ApplicationManager.getApplication().getUserData(AFTER_CREATE)?.invoke(project)
    }

    @VisibleForTesting
    @JvmStatic
    fun configureNewProject(newProject: Project) {
      val gradleSettings = GradleSettings.getInstance(newProject).also { it.setupGradleSettings() }
      val externalProjectPath = ExternalSystemApiUtil.toCanonicalPath(File(newProject.basePath!!).canonicalPath)
      if (!gradleSettings.linkedProjectsSettings.isEmpty()) {
        check(ApplicationManager.getApplication().isUnitTestMode) { "configureNewProject should be used with new projects only" }
        for (setting in gradleSettings.linkedProjectsSettings) {
          gradleSettings.unlinkExternalProject(setting.externalProjectPath)
        }
      }
      val projectSettings = GradleDefaultProjectSettings.createProjectSettings(externalProjectPath)
      if (GRADLE_USES_LOCAL_JAVA_HOME_FOR_NEW_CREATED_PROJECTS.get() || ApplicationManager.getApplication().isUnitTestMode) {
        projectSettings.gradleJvm = USE_GRADLE_LOCAL_JAVA_HOME
        ExternalSystemApiUtil.getSettings(newProject, GradleConstants.SYSTEM_ID).linkProject(projectSettings)
        GradleConfigManager.initializeJavaHome(newProject, externalProjectPath)
        if (IdeInfo.getInstance().isAndroidStudio) {
          val projectMigration = ProjectMigrationsPersistentState.getInstance(newProject)
          projectMigration.migratedGradleRootsToGradleLocalJavaHome.add(externalProjectPath)
        }
      } else {
        projectSettings.gradleJvm = ExternalSystemJdkUtil.USE_PROJECT_JDK
        ExternalSystemApiUtil.getSettings(newProject, GradleConstants.SYSTEM_ID).linkProject(projectSettings)
        WriteAction.runAndWait<RuntimeException> {
          val embeddedJdkPath = IdeSdks.getInstance().embeddedJdkPath
          val jdkTableEntry = JdkUtils.addOrRecreateDedicatedJdkTableEntry(embeddedJdkPath.toString())
          ProjectJdkTable.getInstance().findJdk(jdkTableEntry)?.let {
            ProjectRootManager.getInstance(newProject).projectSdk = it
          }
        }
      }
      beforeOpen(newProject)
    }
  }
}

private val AFTER_CREATE = Key.create<(Project) -> Unit>("GradleProjectImporter.after_create_for_tests")

@TestOnly
fun <T> GradleProjectImporter.Companion.withAfterCreate(afterCreate: (Project) -> Unit, body: () -> T): T {
  val application = ApplicationManager.getApplication()
  application.putUserData(AFTER_CREATE, afterCreate)
  try {
    return body()
  }
  finally {
    application.putUserData(AFTER_CREATE, null)
  }
}
