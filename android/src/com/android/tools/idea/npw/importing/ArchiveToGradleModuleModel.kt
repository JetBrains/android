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
package com.android.tools.idea.npw.importing

import com.android.SdkConstants
import com.android.SdkConstants.GRADLE_PATH_SEPARATOR
import com.android.internal.annotations.VisibleForTesting
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.project.getContainingModule
import com.android.tools.idea.observable.core.BoolProperty
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.StringProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.observable.expressions.bool.BooleanExpression
import com.android.tools.idea.util.toVirtualFile
import com.android.tools.idea.wizard.model.WizardModel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.command.WriteCommandAction.writeCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LargeFileWriteRequestor
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import org.jetbrains.android.facet.AndroidRootUtil.findModuleRootFolderPath
import org.jetbrains.android.facet.AndroidRootUtil.getModuleDirPath
import java.io.File
import java.io.IOException
import java.nio.file.Paths

@VisibleForTesting
fun getBuildGradleText(jarName: File) = "configurations.maybeCreate(\"default\")\nartifacts.add(\"default\", file('${jarName.name}'))"

/**
 * Model that represents the import of an existing library (.jar or .aar) into a Gradle project as a new Module
 */
class ArchiveToGradleModuleModel(
  val project: Project,
  private val projectSyncInvoker: ProjectSyncInvoker
) : WizardModel(), LargeFileWriteRequestor {
  @JvmField val archive: StringProperty = StringValueProperty()
  @JvmField val gradlePath: StringProperty = StringValueProperty()
  @JvmField val moveArchive: BoolProperty = BoolValueProperty()

  init {
    archive.addConstraint(String::trim)
    gradlePath.addConstraint(String::trim)
    archive.set(project.basePath!!)
  }

  fun inModule(): BooleanExpression = object : BooleanExpression(archive) {
    override fun get(): Boolean = getContainingModule(File(archive.get()), project) != null
  }

  public override fun handleFinished() {
    createModuleFromArchive(
      project,
      GRADLE_PATH_SEPARATOR + gradlePath.get().removePrefix(GRADLE_PATH_SEPARATOR),
      File(archive.get()),
      moveArchive.get(),
      getContainingModule(File(archive.get()), project))
  }

  /** Wraps archive in a Gradle module. */
  private fun createModuleFromArchive(
    project: Project, gradlePath: String, archivePath: File, move: Boolean, containingModule: Module?
  ) {
    val progressWindow = ProgressWindow(false, project)
    val process: () -> Unit = {
      doCreateModuleFromArchive(project, gradlePath, archivePath, move, containingModule)
    }

    progressWindow.title = "Creating Module from Archive"
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      ApplicationManager.getApplication().executeOnPooledThread {
        ProgressManager.getInstance().runProcess(process, progressWindow)
      }
    } else {
      process()
    }
  }

  private fun doCreateModuleFromArchive(project: Project, gradlePath: String, archivePath: File, move: Boolean, containingModule: Module?) {
    @Suppress("DEPRECATION") // TODO: maybe we need to look into asking the user for where they want the module to be placed.
    val projectBaseDir = project.guessProjectDir() ?: project.baseDir
    val moduleLocation = GradleUtil.getModuleDefaultPath(projectBaseDir, gradlePath)
    try {
      val sourceFile = archivePath.toVirtualFile(true) ?: return

      // We compute the relative path here before moving the file as if the file doesn't exist we can't obtain the VirtualFile.
      val modulePath = if (containingModule == null) null else findModuleRootFolderPath(containingModule)
      var relativePathString: String? = null
      if (modulePath != null) {
        val modulePathVirtualFile = modulePath.toVirtualFile(true)
        if (modulePathVirtualFile != null) {
          relativePathString = VfsUtilCore.findRelativePath(modulePathVirtualFile, sourceFile, File.separatorChar)
        }
      }

      val projectBuildModel = ProjectBuildModel.get(project)

      // Add the module to the settings file so Gradle can file it while syncing and so it shows up in
      // the IDE
      if (!registerModuleInSettingsFile(projectBuildModel, gradlePath)) return

      if (move && containingModule != null) {
        if (!addDependency(projectBuildModel, containingModule, gradlePath, archivePath, relativePathString)) return
      }

      writeCommandAction(project)
        .withName("Create module $gradlePath from Archive")
        .withGlobalUndo()
        .withUndoConfirmationPolicy(UndoConfirmationPolicy.REQUEST_CONFIRMATION).run<Throwable> {
          val moduleRoot = VfsUtil.createDirectoryIfMissing(moduleLocation.absolutePath)
          if (moduleRoot != null) {
            // Move or copy the archive to the new module root.
            if (move) {
              sourceFile.move(this, moduleRoot)
            }
            else {
              sourceFile.copy(this, moduleRoot, sourceFile.name)
            }

            // Write the build file for the new module.
            val buildGradle = moduleRoot.createChildData(this, SdkConstants.FN_BUILD_GRADLE)
            VfsUtil.saveText(buildGradle, getBuildGradleText(archivePath))
            projectBuildModel.applyChanges()
          }
        }

      if (!ApplicationManager.getApplication().isUnitTestMode) {
        ApplicationManager.getApplication().invokeLater {
          projectSyncInvoker.syncProject(project)
        }
      } else {
        projectSyncInvoker.syncProject(project)
      }
    }
    catch (e: IOException) {
      logger<ArchiveToGradleModuleModel>().error(e)
    }
  }

  private fun registerModuleInSettingsFile(projectBuildModel: ProjectBuildModel, gradlePath: String) : Boolean {
    val settingsModel = projectBuildModel.projectSettingsModel
    if (settingsModel == null) {
      showErrorDialog("Can't understand Gradle settings file, please add the path '$gradlePath' manually.")
      return false
    }
    settingsModel.addModulePath(gradlePath)
    return true
  }

  private fun showErrorDialog(error: String) {
    ApplicationManager.getApplication().invokeLater { Messages.showErrorDialog(project, error, "Create Module from Archive") }
  }

  private fun addDependency(
    projectBuildModel: ProjectBuildModel,
    module: Module,
    gradlePath: String,
    archivePath: File,
    relativePathString: String?
  ) : Boolean {
    val gradleBuildModel = projectBuildModel.getModuleBuildModel(module)
    if (gradleBuildModel == null) {
      showErrorDialog("Couldn't add dependency from module '${module.name}' to module '${gradlePath}', please add manually")
      return false
    }

    val dependencies = gradleBuildModel.dependencies()

    // Add the module dependency on the new module.
    val scope = if (GradleUtil.useCompatibilityConfigurationNames(project)) "compile" else "implementation"
    dependencies.addModule(scope, gradlePath)

    // Remove any file dependencies on this Jar/Aar
    // We want to filter out anything that is equal to the absolute or relative paths of the archive file
    val archivePathString = archivePath.absolutePath

    // Remove either of these paths if they exist
    for (fileDependency in dependencies.files()) {
      val dependencyPath = fileDependency.file().toString()
      // TODO: This needs to be updated once the method has been changed.
      // Currently GradlePropertyModel#toString can return null, this is not possible in Kotlin.
      @Suppress("SENSELESS_COMPARISON")
      if (dependencyPath == null) {
        continue
      }

      if (dependencyPath == relativePathString || dependencyPath == archivePathString) {
        dependencies.remove(fileDependency)
      }
    }

    return true
  }
}