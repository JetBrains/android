/*
 * Copyright (C) 2017 The Android Open Source Project
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
package org.jetbrains.android.refactoring

import com.android.annotations.VisibleForTesting
import com.android.ide.common.repository.GradleVersion
import com.android.sdklib.AndroidVersion
import com.android.support.MigrationParserVisitor
import com.android.support.parseMigrationFile
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.actions.BaseRefactoringAction
import org.jetbrains.android.refactoring.AppCompatMigrationEntry.*

class MigrateToAndroidxAction : BaseRefactoringAction() {

  override fun isAvailableInEditorOnly() = false

  override fun isEnabledOnDataContext(dataContext: DataContext) = true

  override fun isEnabledOnElements(elements: Array<out PsiElement>) = true

  override fun getHandler(dataContext: DataContext): RefactoringActionHandler? = MigrateToAndroidxHandler()

  override fun update(anActionEvent: AnActionEvent) {
    anActionEvent.presentation.isEnabledAndVisible = StudioFlags.MIGRATE_TO_ANDROID_X_REFACTORING_ENABLED.get()
  }

  override fun isAvailableForLanguage(language: Language) = true
}

class MigrateToAndroidxHandler : RefactoringActionHandler {

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
    invoke(project, arrayOf<PsiElement>(file!!), dataContext)
  }

  override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
    if (!checkRefactoringPrerequisites(project)) {
      return
    }

    val processor = MigrateToAndroidxProcessor(project, parseMigrationMap())

    with(processor) {
      setPreviewUsages(true)
      run()
    }
  }

  private fun checkRefactoringPrerequisites(project: Project): Boolean {
      @Suppress("UselessCallOnCollection")
      val highestCompileSdkVersion = ModuleManager.getInstance(project).modules
        .mapNotNull { GradleBuildModel.get(it)?.android() }
        .mapNotNull { it.compileSdkVersion().toString() }
        .map { it.removePrefix("android-") }
        .mapNotNull {
          try {
            AndroidVersion(it)
          }
          catch (e: AndroidVersion.AndroidVersionException) {
            null
          }
        }
        .sorted()
        .lastOrNull()
      val supportedCompileSdk = if (highestCompileSdkVersion != null) {
        AndroidVersion(28) <= highestCompileSdkVersion || highestCompileSdkVersion.codename != null
      }
      else {
        true // Enable by default when we can not find out the version
      }

      val gradleVersionString = ModuleManager.getInstance(project).modules
        .mapNotNull { GradleBuildModel.get(it)?.buildscript()?.dependencies() }
        .flatMap { it.artifacts() }
        .filter { it.name().forceString() == "gradle" && it.group().forceString() == "com.android.tools.build" }
        .map { it.version().getValue(GradlePropertyModel.STRING_TYPE) }
        .firstOrNull()
      val supportedGradleVersion = if (gradleVersionString?.startsWith('$') == false) {
        GradleVersion.tryParse(gradleVersionString)?.isAtLeastIncludingPreviews(3, 2, 0) ?: false
      }
      else {
        // For now, ignore this case since the DSL parser does not seem to handle that correctly
        true
      }

    if (supportedCompileSdk && supportedGradleVersion) {
      return true
    }

    val warningContent = if (!supportedCompileSdk) {
      "You need to have at least have compileSdk 28 set in your module build.gradle to refactor to androidx\n"
    } else {
      ""
    } + if (!supportedGradleVersion) {
      "The gradle plugin version in your project build.gradle file needs to be set to at least com.android.tools.build:gradle:3.2.0 " +
      "in order to refactor to androidx"
    } else {
      ""
    }

    JBPopupFactory.getInstance().createMessage(warningContent).showInFocusCenter()
    return false
  }

  @VisibleForTesting
  private fun parseMigrationMap(): List<AppCompatMigrationEntry> {
    val result = mutableListOf<AppCompatMigrationEntry>()

    parseMigrationFile(object: MigrationParserVisitor {
      override fun visitClass(old: String, new: String) {
        result.add(ClassMigrationEntry(old, new))
      }

      override fun visitPackage(old: String, new: String) {
        result.add(PackageMigrationEntry(old, new))
      }

      override fun visitGradleCoordinate(
        oldGroupName: String,
        oldArtifactName: String,
        newGroupName: String,
        newArtifactName: String,
        newBaseVersion: String
      ) {
        result.add(GradleDependencyMigrationEntry(oldGroupName, oldArtifactName, newGroupName, newArtifactName, newBaseVersion))
      }

    })

    return result
  }
}
