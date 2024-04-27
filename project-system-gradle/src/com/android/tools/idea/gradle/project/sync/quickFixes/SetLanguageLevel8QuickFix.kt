/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.quickFixes

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.java.LanguageLevelPropertyModel
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel
import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.projectsystem.gradle.GradleHolderProjectPath
import com.android.tools.idea.projectsystem.gradle.resolveIn
import com.google.common.annotations.VisibleForTesting
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiElement
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewBundle
import com.intellij.usageView.UsageViewDescriptor
import org.jetbrains.android.facet.AndroidFacet
import java.util.Arrays
import java.util.concurrent.CompletableFuture
import java.util.stream.Collectors

abstract class AbstractSetLanguageLevel8QuickFix(private val setJvmTarget: Boolean, private val modulesDescription: String) : DescribedBuildIssueQuickFix {
  override val description = "Change Java language level${if (setJvmTarget) " and jvmTarget" else ""} to 8 in $modulesDescription if using a lower level."

  abstract fun buildFilesToApply(project: Project): List<VirtualFile>

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Any>()
    try {
      if (!project.isDisposed) {
        setJavaLevel8InBuildFiles(project, setJvmTarget)
      }
      future.complete(null)
    }
    catch (e: Exception) {
      future.completeExceptionally(e)
    }
    return future
  }

  @VisibleForTesting
  fun setJavaLevel8InBuildFiles(project: Project, jvmTarget: Boolean) {
    val buildFiles = buildFilesToApply(project)
    if (buildFiles.isEmpty()) {
      // There is nothing to change, show an error message
      Messages.showErrorDialog(project, "Could not determine build files to apply fix", "Change Java language level to 8")
    }
    else {
      val processor = SetJavaLanguageLevel8Processor(project, buildFiles, jvmTarget, modulesDescription)
      processor.setPreviewUsages(true)
      processor.run()
    }
  }
}

class SetLanguageLevel8AllQuickFix(setJvmTarget: Boolean) : AbstractSetLanguageLevel8QuickFix(setJvmTarget, "all modules") {
  override val id = "set.java.level.8.all"

  override fun buildFilesToApply(project: Project) = project.allBuildFiles()
}

class SetLanguageLevel8ModuleQuickFix(val modulePath: String, setJvmTarget: Boolean): AbstractSetLanguageLevel8QuickFix(setJvmTarget, "module $modulePath") {
  override val id = "set.java.level.8.module"

  override fun buildFilesToApply(project: Project): List<VirtualFile> {
    return project.moduleBuildFiles(modulePath)
  }
}

private class SetJavaLanguageLevel8Processor(
  project: Project,
  private val buildFiles: List<VirtualFile>,
  val setJvmTarget: Boolean,
  private val modulesDescription: String)
  : BaseRefactoringProcessor(project) {
  /**
   * For Java compatibility looks for android.compileOptions.sourceCompatibility and android.compileOptions.targetCompatibility and adds
   * usage if its value is lower than 1.8. If the elements do not exist then adds usage of the parents.
   *
   * If setJvmTarget is true, also looks for android.kotlinOptions.jvmTarget and adds usage if its value is lower than 1.8. If the elements
   * do not exist then adds usage of the parents.
   */
  override fun findUsages(): Array<UsageInfo> {
    val projectBuildModel = ProjectBuildModel.get(myProject)

    val usages = ArrayList<UsageInfo>()
    for (file in buildFiles) {
      if (!file.isValid || !file.isWritable) {
        continue
      }
      val elementToUsage = mutableMapOf<PsiElement, SetJava8UsageInfo>()
      val android = projectBuildModel.getModuleBuildModel(file).android()
      val androidElement = (android as GradleDslBlockModel).psiElement!!

      // source and target compatibility
      val compileOptions = android.compileOptions()
      val compileOptionsElement = (compileOptions as GradleDslBlockModel).psiElement

      fun getCompatibilityUsage(languageCompatibility: LanguageLevelPropertyModel): PsiElement? {
        if (compileOptionsElement == null) {
          return androidElement
        }
        else {
          val languageCompatibilityElement = languageCompatibility.fullExpressionPsiElement
          if (languageCompatibilityElement == null) {
            return compileOptionsElement
          }
          else {
            if (languageCompatibility.toLanguageLevel()!!.isLessThan(LanguageLevel.JDK_1_8)) {
              return languageCompatibilityElement
            }
          }
        }
        return null
      }

      getCompatibilityUsage(compileOptions.sourceCompatibility())?.let {
        elementToUsage.getOrPut(it, {SetJava8UsageInfo(it, file)}).setSourceCompatibility = true
      }
      getCompatibilityUsage(compileOptions.targetCompatibility())?.let {
        elementToUsage.getOrPut(it, {SetJava8UsageInfo(it, file)}).setTargetCompatibility = true
      }

      // kotlin options
      if (setJvmTarget) {
        val kotlinOptions = android.kotlinOptions()
        val kotlinOptionsElement = (kotlinOptions as GradleDslBlockModel).psiElement
        if (kotlinOptionsElement == null) {
          elementToUsage.getOrPut(androidElement, { SetJava8UsageInfo(androidElement, file) }).setKotlinTarget = true
        }
        else {
          val jvmTarget = kotlinOptions.jvmTarget()
          val jvmTargetElement = jvmTarget.fullExpressionPsiElement
          if (jvmTargetElement == null) {
            elementToUsage.getOrPut(kotlinOptionsElement, { SetJava8UsageInfo(kotlinOptionsElement, file) }).setKotlinTarget = true
          }
          else {
            if (jvmTarget.toLanguageLevel()!!.isLessThan(LanguageLevel.JDK_1_8)) {
              elementToUsage.getOrPut(jvmTargetElement, { SetJava8UsageInfo(jvmTargetElement, file) }).setKotlinTarget = true
            }
          }
        }
      }
      usages.addAll(elementToUsage.values)
    }
    return usages.toTypedArray()
  }

  override fun performRefactoring(usages: Array<out UsageInfo>) {
    val projectBuildModel = ProjectBuildModel.get(myProject)
    for (file in buildFiles) {
      val fileUsages = Arrays.stream(usages)
        .filter { it is SetJava8UsageInfo && it.buildFile == file }
        .collect(Collectors.toList())
      if (fileUsages.isEmpty()) {
        continue
      }
      val android = projectBuildModel.getModuleBuildModel(file).android()
      fileUsages.forEach {
        val setJava8Usage = it as SetJava8UsageInfo
        if (setJava8Usage.setSourceCompatibility) {
          android.compileOptions().sourceCompatibility().setLanguageLevel(LanguageLevel.JDK_1_8)
        }
        if (setJava8Usage.setTargetCompatibility) {
          android.compileOptions().targetCompatibility().setLanguageLevel(LanguageLevel.JDK_1_8)
        }
        if (setJava8Usage.setKotlinTarget) {
          android.kotlinOptions().jvmTarget().setLanguageLevel(LanguageLevel.JDK_1_8)
        }
      }
    }
    projectBuildModel.applyChanges()
  }

  override fun getCommandName() = "Set Java level 8 in $modulesDescription"

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptor {
      override fun getCodeReferencesText(usagesCount: Int, filesCount: Int): String {
        return "References to be changed: ${UsageViewBundle.getReferencesString(usagesCount, filesCount)}"
      }

      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = "Set Java level to 8."
    }
  }
}

private class SetJava8UsageInfo(element: PsiElement, val buildFile: VirtualFile): UsageInfo(element) {
  var setSourceCompatibility: Boolean = false
  var setTargetCompatibility: Boolean = false
  var setKotlinTarget: Boolean = false

  override fun getTooltipText(): String {
    val lines = ArrayList<String>()
    if (setSourceCompatibility) {
      lines.add("android.compileOptions.sourceCompatibility to VERSION_1_8")
    }
    if (setTargetCompatibility) {
      lines.add("android.compileOptions.targetCompatibility to VERSION_1_8")
    }
    if (setKotlinTarget) {
      lines.add("android.kotlinOptions.jvmTarget to 1.8")
    }
    return lines.joinToString(prefix = "Set ", separator = ", ")
  }
}

fun Project.allBuildFiles(): List<VirtualFile> = ProjectFacetManager.getInstance(this)
  .getModulesWithFacet(AndroidFacet.ID)
  .mapNotNull { GradleProjectSystemUtil.getGradleModuleModel(it) }
  .mapNotNull { it.buildFile }
  .distinct()
  .toList()

fun Project.moduleBuildFiles(modulePath: String): List<VirtualFile> {
  return listOfNotNull(
    // TODO(b/149203281): Fix support for composite projects.
    GradleHolderProjectPath(
      FileUtil.toSystemIndependentName((guessProjectDir()?.path ?: return emptyList())),
      modulePath
    ).resolveIn(this)
  )
    .filter { AndroidFacet.getInstance(it) != null }
    .mapNotNull { GradleProjectSystemUtil.getGradleModuleModel(it) }
    .mapNotNull { it.buildFile }
}
