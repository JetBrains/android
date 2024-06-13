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

abstract class AbstractSetJavaLanguageLevelQuickFix(val level: LanguageLevel,
                                                    private val setJvmTarget: Boolean,
                                                    private val modulesDescription: String) : DescribedBuildIssueQuickFix {
  override val description = "Change Java language level${if (setJvmTarget) " and jvmTarget" else ""} to ${level.toJavaVersion().feature} in $modulesDescription if using a lower level."

  abstract fun buildFilesToApply(project: Project): List<VirtualFile>

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Any>()
    try {
      if (!project.isDisposed) {
        setJavaLevelInBuildFiles(project, setJvmTarget)
      }
      future.complete(null)
    }
    catch (e: Exception) {
      future.completeExceptionally(e)
    }
    return future
  }

  @VisibleForTesting
  fun setJavaLevelInBuildFiles(project: Project, jvmTarget: Boolean) {
    val buildFiles = buildFilesToApply(project)
    if (buildFiles.isEmpty()) {
      // There is nothing to change, show an error message
      Messages.showErrorDialog(project, "Could not determine build files to apply fix",
                               "Change Java language level to ${level.toJavaVersion().feature}}")
    }
    else {
      val processor = SetJavaLanguageLevelProcessor(project, buildFiles, level, jvmTarget, modulesDescription)
      processor.setPreviewUsages(true)
      processor.run()
    }
  }
}

class SetJavaLanguageLevelAllQuickFix(level: LanguageLevel, setJvmTarget: Boolean) : AbstractSetJavaLanguageLevelQuickFix(level,
                                                                                                                          setJvmTarget,
                                                                                                                          "all modules") {
  override val id = "set.java.level.${level.name}.all"

  override fun buildFilesToApply(project: Project) = project.allBuildFiles()
}

class SetJavaLanguageLevelModuleQuickFix(val modulePath: String,
                                         level: LanguageLevel,
                                         setJvmTarget: Boolean) : AbstractSetJavaLanguageLevelQuickFix(level, setJvmTarget,
                                                                                                       "module $modulePath") {
  override val id = "set.java.level.${level.name}.module"

  override fun buildFilesToApply(project: Project): List<VirtualFile> {
    return project.moduleBuildFiles(modulePath)
  }
}

private class SetJavaLanguageLevelProcessor(
  project: Project,
  private val buildFiles: List<VirtualFile>,
  val level: LanguageLevel,
  val setJvmTarget: Boolean,
  private val modulesDescription: String)
  : BaseRefactoringProcessor(project) {
  /**
   * For Java compatibility looks for android.compileOptions.sourceCompatibility and android.compileOptions.targetCompatibility and adds
   * usage if its value is lower than [level]. If the elements do not exist then adds usage of the parents.
   *
   * If setJvmTarget is true, also looks for android.kotlinOptions.jvmTarget and adds usage if its value is lower than [level]. If the elements
   * do not exist then adds usage of the parents.
   */
  override fun findUsages(): Array<UsageInfo> {
    val projectBuildModel = ProjectBuildModel.get(myProject)

    val usages = ArrayList<UsageInfo>()
    for (file in buildFiles) {
      if (!file.isValid || !file.isWritable) {
        continue
      }
      val elementToUsage = mutableMapOf<PsiElement, SetJavaLevelUsageInfo>()
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
            if (languageCompatibility.toLanguageLevel()!!.isLessThan(level)) {
              return languageCompatibilityElement
            }
          }
        }
        return null
      }

      getCompatibilityUsage(compileOptions.sourceCompatibility())?.let {
        elementToUsage.getOrPut(it) { SetJavaLevelUsageInfo(it, file, level) }.setSourceCompatibility = true
      }
      getCompatibilityUsage(compileOptions.targetCompatibility())?.let {
        elementToUsage.getOrPut(it) { SetJavaLevelUsageInfo(it, file, level) }.setTargetCompatibility = true
      }

      // kotlin options
      if (setJvmTarget) {
        val kotlinOptions = android.kotlinOptions()
        val kotlinOptionsElement = (kotlinOptions as GradleDslBlockModel).psiElement
        if (kotlinOptionsElement == null) {
          elementToUsage.getOrPut(androidElement) { SetJavaLevelUsageInfo(androidElement, file, level) }.setKotlinTarget = true
        }
        else {
          val jvmTarget = kotlinOptions.jvmTarget()
          val jvmTargetElement = jvmTarget.fullExpressionPsiElement
          if (jvmTargetElement == null) {
            elementToUsage.getOrPut(kotlinOptionsElement) {
              SetJavaLevelUsageInfo(kotlinOptionsElement, file, level)
            }.setKotlinTarget = true
          }
          else {
            if (jvmTarget.toLanguageLevel()!!.isLessThan(level)) {
              elementToUsage.getOrPut(jvmTargetElement) { SetJavaLevelUsageInfo(jvmTargetElement, file, level) }.setKotlinTarget = true
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
        .filter { it is SetJavaLevelUsageInfo && it.buildFile == file }
        .collect(Collectors.toList())
      if (fileUsages.isEmpty()) {
        continue
      }
      val android = projectBuildModel.getModuleBuildModel(file).android()
      fileUsages.forEach {
        val setJavaUsage = it as SetJavaLevelUsageInfo
        if (setJavaUsage.setSourceCompatibility) {
          android.compileOptions().sourceCompatibility().setLanguageLevel(level)
        }
        if (setJavaUsage.setTargetCompatibility) {
          android.compileOptions().targetCompatibility().setLanguageLevel(level)
        }
        if (setJavaUsage.setKotlinTarget) {
          android.kotlinOptions().jvmTarget().setLanguageLevel(level)
        }
      }
    }
    projectBuildModel.applyChanges()
  }

  override fun getCommandName() = "Set Java level ${level.name} in $modulesDescription"

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptor {
      override fun getCodeReferencesText(usagesCount: Int, filesCount: Int): String {
        return "References to be changed: ${UsageViewBundle.getReferencesString(usagesCount, filesCount)}"
      }

      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = "Set Java level to ${level.name}."
    }
  }
}

private class SetJavaLevelUsageInfo(element: PsiElement, val buildFile: VirtualFile, val level: LanguageLevel) : UsageInfo(element) {
  var setSourceCompatibility: Boolean = false
  var setTargetCompatibility: Boolean = false
  var setKotlinTarget: Boolean = false

  override fun getTooltipText(): String {
    val lines = ArrayList<String>()
    if (setSourceCompatibility) {
      lines.add("android.compileOptions.sourceCompatibility to ${level.name}")
    }
    if (setTargetCompatibility) {
      lines.add("android.compileOptions.targetCompatibility to ${level.name}")
    }
    if (setKotlinTarget) {
      lines.add("android.kotlinOptions.jvmTarget to ${level.toJavaVersion()}")
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
