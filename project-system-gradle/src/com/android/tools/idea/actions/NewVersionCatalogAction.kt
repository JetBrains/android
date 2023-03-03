/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.actions

import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE
import com.android.tools.idea.gradle.project.GradleVersionCatalogDetector
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.requestProjectSync
import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_MODIFIER_ACTION_REDONE
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_MODIFIER_ACTION_UNDONE
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_VERSION_CATALOG_FILE_ADDED
import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.nio.file.Path

class NewVersionCatalogAction : CreateFileFromTemplateAction("Version Catalog", "Create a new Version Catalog file", icons.GradleIcons.GradleFile) {

  override fun startInWriteAction() = false

  override fun createFileFromTemplate(name: String, template: FileTemplate, dir: PsiDirectory): PsiFile {
    val project = dir.project
    val psiManager = PsiManager.getInstance(project)
    val path = project.singleGradleProjectRoot

    val vfsDirectory = VfsUtil.createDirectoryIfMissing(VfsUtil.findFile(Path.of(path), true), "gradle")
    val directory = psiManager.findDirectory(vfsDirectory)
    // Unfortunately the template engine disagrees with itself about what the extension of the created file should be.  One part does
    // template matching up to the first dot; another part takes the extension from the last dot.  So we have to explicitly re-introduce
    // ".versions" here.
    return super.createFileFromTemplate("$name.versions", template, directory)
  }

  override fun createFile(name: String?, templateName: String?, dir: PsiDirectory?): PsiFile? {
    return super.createFile(name, templateName, dir)?.also { createdElement ->
      val project = createdElement.project
      UndoManager.getInstance(project).undoableActionPerformed(object : BasicUndoableAction() {
        override fun undo() = GradleSyncInvoker.getInstance().requestProjectSync(project, TRIGGER_MODIFIER_ACTION_UNDONE)
        override fun redo() = GradleSyncInvoker.getInstance().requestProjectSync(project, TRIGGER_MODIFIER_ACTION_REDONE)
      })
      val task = object : Task.WithResult<Pair<ProjectBuildModel, GradleSettingsModel?>, Exception>(project, "Parsing Build Files", true) {
        override fun compute(indicator: ProgressIndicator): Pair<ProjectBuildModel, GradleSettingsModel?> {
          indicator.run {
            fraction = 0.0
            text = "Parsing root Gradle build file"
          }
          val model = ProjectBuildModel.get(project)
          indicator.run {
            checkCanceled()
            fraction = 0.5
            text = "Parsing Gradle settings file"
          }
          val settingsModel = model.projectSettingsModel
          indicator.fraction = 1.0
          return model to settingsModel
        }
      }
      val (model, settingsModel) = ProgressManager.getInstance().run(task)

      val current = mutableMapOf<String, String?>().also {
        settingsModel?.dependencyResolutionManagement()?.versionCatalogs()
          ?.mapNotNull { vc ->
            vc.name to vc.from().getValue(STRING_TYPE)
          }
          ?.toMap(it)
        it.putIfAbsent("libs", "gradle/libs.versions.toml")
      }

      val baseName = createdElement.name.removeSuffix(".versions.toml")
      var candidateName = baseName
      if (current[baseName] == "gradle/${createdElement.name}") {
        // do nothing: already set up.  (Common case is adding libs.versions.toml to a project previously not using Version Catalogs)
      }
      else {
        var i = 1
        while (current.containsKey(candidateName)) {
          candidateName = "${baseName}${i++}"
        }
        val vcModel = settingsModel?.dependencyResolutionManagement()?.addVersionCatalog(candidateName)
        vcModel?.from()?.setValue("gradle/${createdElement.name}")
        runWriteAction {
          model.applyChanges()
        }
      }
    }
  }

  override fun postProcess(createdElement: PsiFile, templateName: String?, customProperties: MutableMap<String, String>?) {
    val project = createdElement.project
    GradleSyncInvoker.getInstance().requestProjectSync(project, TRIGGER_VERSION_CATALOG_FILE_ADDED)
  }

  override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
    val root = project.singleGradleProjectRoot
    val vfsDirectory = VfsUtil.findRelativeFile(VfsUtil.findFile(Path.of(root), true), "gradle")
    val defaultText = when (vfsDirectory?.findChild("libs.versions.toml")) {
      null -> "libs"
      else -> ""
    }
    builder
      .setTitle("New Version Catalog")
      .setDefaultText(defaultText)
      .addKind("Version Catalog file", icons.GradleIcons.GradleFile, "Version Catalog File")
      .setValidator(object : InputValidatorEx {
        override fun getErrorText(inputString: String?) = "Name must match the regex $REGEX_STRING"
        override fun checkInput(inputString: String?) = inputString?.matches(REGEX) ?: false
        override fun canClose(inputString: String?) = inputString?.matches(REGEX) ?: false
      })
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    if (!e.presentation.isVisible) return
    e.project?.let { project ->

      // Don't attempt to allow this in a multi-rooted IDE project.
      if (GradleSettings.getInstance(project).linkedProjectsSettings.mapNotNull { it.externalProjectPath }.singleOrNull() == null) {
        e.presentation.isEnabledAndVisible = false
        return
      }

      val gradleVersion = GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(project)?.resolveGradleVersion()
      if (gradleVersion == null || gradleVersion < GradleVersionCatalogDetector.STABLE_GRADLE_VERSION) {
        e.presentation.isEnabledAndVisible = false
        return
      }
      e.presentation.isEnabledAndVisible = true
    }
  }

  override fun getActionName(directory: PsiDirectory?, newName: String, templateName: String?): String = "Version Catalog File"


  private val Project.singleGradleProjectRoot: String
    get() = GradleSettings.getInstance(this).linkedProjectsSettings.mapNotNull { it.externalProjectPath }.toSet().singleOrNull()
            // The update() method should mean that this exception never happens.
            ?: throw IncorrectOperationException("Operation not supported in multiple-root projects")

  companion object {
    const val REGEX_STRING = "[a-z]([a-zA-Z0-9])+"
    val REGEX = REGEX_STRING.toRegex()
  }
}