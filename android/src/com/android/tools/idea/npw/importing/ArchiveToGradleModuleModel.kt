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
import com.android.tools.idea.gradle.parser.BuildFileKey
import com.android.tools.idea.gradle.parser.Dependency
import com.android.tools.idea.gradle.parser.GradleBuildFile
import com.android.tools.idea.gradle.parser.GradleSettingsFile
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.project.getContainingModule
import com.android.tools.idea.observable.core.BoolProperty
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.StringProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.observable.expressions.bool.BooleanExpression
import com.android.tools.idea.util.toIoFile
import com.android.tools.idea.wizard.model.WizardModel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.command.WriteCommandAction.writeCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LargeFileWriteRequestor
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import java.io.File
import java.io.IOException

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

    if (!ApplicationManager.getApplication().isUnitTestMode) {
      assert(ApplicationManager.getApplication().isDispatchThread)
      projectSyncInvoker.syncProject(project)
    }
  }

  /** Wraps archive in a Gradle module. */
  private fun createModuleFromArchive(
    project: Project, gradlePath: String, archivePath: File, move: Boolean, containingModule: Module?
  ) {
    writeCommandAction(project)
      .withName("create module $gradlePath")
      .withGlobalUndo()
      .withUndoConfirmationPolicy(UndoConfirmationPolicy.REQUEST_CONFIRMATION).run<Throwable> {
        @Suppress("DEPRECATION") // TODO: maybe we need to look into asking the user for where they want the module to be placed.
        val projectBaseDir = project.guessProjectDir() ?: project.baseDir
        val moduleLocation = GradleUtil.getModuleDefaultPath(projectBaseDir, gradlePath)
        try {
          val moduleRoot = VfsUtil.createDirectoryIfMissing(moduleLocation.absolutePath)
          val sourceFile = VfsUtil.findFileByIoFile(archivePath, true)
          if (sourceFile != null && moduleRoot != null) {
            if (move) {
              sourceFile.move(this, moduleRoot)
            }
            else {
              sourceFile.copy(this, moduleRoot, sourceFile.name)
            }
            val buildGradle = moduleRoot.createChildData(null, SdkConstants.FN_BUILD_GRADLE)
            VfsUtil.saveText(buildGradle, getBuildGradleText(archivePath))
            GradleSettingsFile.getOrCreate(project).addModule(gradlePath, VfsUtilCore.virtualToIoFile(moduleRoot))
            if (move && containingModule != null) {
              addDependency(containingModule, gradlePath, archivePath)
            }
          }
        }
        catch (e: IOException) {
          logger<ArchiveToGradleModuleModel>().error(e)
        }
      }
  }

  private fun filterDependencyStatement(dependency: Dependency, moduleRoot: File, archivePath: File): Dependency? {
    val rawArguments: Any? = dependency.data
    if (dependency.type == Dependency.Type.FILES && rawArguments != null) {
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

  private fun addDependency(module: Module, gradlePath: String, archivePath: File) {
    // TODO(qumeric): use GradleBuildModel instead
    val buildFile = GradleBuildFile.get(module) ?: throw IOException("Missing " + SdkConstants.FN_BUILD_GRADLE)
    val moduleRoot = buildFile.file.parent.toIoFile()
    val newDeps = buildFile.dependencies.mapNotNull { filterDependencyStatement(it as Dependency, moduleRoot, archivePath) }.toMutableList()
    val scope = Dependency.Scope.getDefaultScope(module.project)
    newDeps.add(Dependency(scope, Dependency.Type.MODULE, gradlePath))
    buildFile.setValue(BuildFileKey.DEPENDENCIES, newDeps)
  }
}