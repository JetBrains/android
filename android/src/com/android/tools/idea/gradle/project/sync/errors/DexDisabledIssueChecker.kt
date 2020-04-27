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
package com.android.tools.idea.gradle.project.sync.errors

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.INTEGER_TYPE
import com.android.tools.idea.gradle.dsl.api.java.LanguageLevelPropertyModel
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.gradle.util.GradleUtil.findModuleByGradlePath
import com.android.tools.idea.gradle.util.GradleUtil.getParentModulePath
import com.google.common.annotations.VisibleForTesting
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiElement
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewBundle
import com.intellij.usageView.UsageViewDescriptor
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import java.util.Arrays
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern
import java.util.stream.Collectors

private const val INVOKE_CUSTOM = "Invoke-customs are only supported starting with Android O"
private const val DEFAULT_INTERFACE_METHOD = "Default interface methods are only supported starting with Android N (--min-api 24)"
private const val STATIC_INTERFACE_METHOD = "Static interface methods are only supported starting with Android N (--min-api 24)"
private val FAILED_TASK_PATTERN = Pattern.compile("Execution failed for task '(.+)'.")

class DexDisabledIssueChecker: GradleIssueChecker {
  /**
   * Looks for errors related to DexArchiveBuilderException caused when desugaring is not enabled. The expected errors have a root cause
   * with a message like these:
   *
   * - Error: Invoke-customs are only supported starting with Android O (--min-api 26)
   * - Error: Default interface methods are only supported starting with Android N (--min-api 24): <method>
   * - Error: Static interface methods are only supported starting with Android N (--min-api 24): <method>
   *
   * And it should be wrapped by a DexArchiveBuilderException containing a message with recommendations on how to fix it.
   */
  override fun check(issueData: GradleIssueData): BuildIssue? {
    // Confirm rootCause is one of the expected causes.
    val rootCause = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first ?: return null
    if (rootCause !is RuntimeException) {
      return null
    }
    var rootMessage = rootCause.message ?: return null
    if (!rootMessage.startsWith("Error: ")) {
      return null
    }
    rootMessage = rootMessage.removePrefix("Error: ")
    val requiredApiLevel: String
    when {
      rootMessage.startsWith(INVOKE_CUSTOM) -> {
        requiredApiLevel = "26"
      }
      rootMessage.startsWith(DEFAULT_INTERFACE_METHOD) -> {
        requiredApiLevel = "24"
      }
      rootMessage.startsWith(STATIC_INTERFACE_METHOD) -> {
        requiredApiLevel = "24"
      }
      else -> return null
    }

    // Confirm that there is a DexArchiveBuilderException
    val builderException = extractDexArchiveBuilderException(issueData.error) ?: return null
    val issueComposer = BuildIssueComposer(rootMessage, issueTitle = "Desugaring disabled")
    val buildMessage = builderException.message
    if (buildMessage != null) {
      issueComposer.addDescription(buildMessage)
    }
    val modulePath = extractModulePathFromError(issueData.error)
    if (modulePath != null) {
      issueComposer.addQuickFix(EnableDexWithApiLevelQuickFixModule(modulePath, requiredApiLevel))
    }
    issueComposer.addQuickFix(EnableDexWithApiLevelQuickFixAll(requiredApiLevel))
    return issueComposer.composeBuildIssue()
  }
}

abstract class AbstractEnableDexWithApiLevelQuickFix(val apiLevel: String?): DescribedBuildIssueQuickFix {
  abstract fun buildFilesToApply(project: Project): List<VirtualFile>
  abstract val commandSuffix: String

  override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
    val future = CompletableFuture<Any>()
    try {
      if (!project.isDisposed) {
        enableDexInBuildFiles(project)
      }
      future.complete(null)
    }
    catch (e: Exception) {
      future.completeExceptionally(e)
    }
    return future
  }

  @VisibleForTesting
  fun enableDexInBuildFiles(project: Project) {
    val buildFiles = buildFilesToApply(project)
    if (buildFiles.isEmpty()) {
      // There is nothing to change, show an error message
      Messages.showErrorDialog(project, "Could not determine build files to apply fix", "Enable desugaring")
    }
    else {
      val processor = EnableDexProcessor(project, buildFiles, apiLevel, commandSuffix)
      processor.setPreviewUsages(true)
      processor.run()
    }
  }
}

class EnableDexWithApiLevelQuickFixAll(apiLevel: String?): AbstractEnableDexWithApiLevelQuickFix(apiLevel) {
  override val description = "Enable desugaring${apiLevel?.let{" and set minSdkVersion to $it"} ?: ""} in all modules."
  override val id = "enable.desugaring.all${apiLevel?.let{".$apiLevel"} ?: ""}"
  override val commandSuffix = " in all modules"

  override fun buildFilesToApply(project: Project) = ModuleManager.getInstance(project)
    .modules
    .filter { it != null && AndroidFacet.getInstance(it) != null }
    .mapNotNull { GradleUtil.getGradleModuleModel(it) }
    .mapNotNull { it.buildFile}
}

class EnableDexWithApiLevelQuickFixModule(val modulePath: String, apiLevel: String?): AbstractEnableDexWithApiLevelQuickFix(apiLevel) {
  override val description: String = "Enable desugaring${apiLevel?.let{" and set minSdkVersion to $it"} ?: ""} in module $modulePath."
  override val id: String = "enable.desugaring.module${apiLevel?.let{".$apiLevel"} ?: ""}"
  override val commandSuffix = " in $modulePath"

  override fun buildFilesToApply(project: Project) = listOf(findModuleByGradlePath(project, modulePath))
    .filter { it != null && AndroidFacet.getInstance(it) != null }
    .mapNotNull { GradleUtil.getGradleModuleModel(it) }
    .mapNotNull { it.buildFile}
}

private class EnableDexProcessor(project: Project, private val buildFiles: List<VirtualFile>, private val apiLevel: String?,
                         private val commandSuffix: String) : BaseRefactoringProcessor(project) {
  /**
   * Find the points in build.gradle where the min SDK and java compatibility levels can be updated.
   *
   * For min SDK looks for android.defaultConfig.minSdkVersion and if the value is lower than apiLevel, then adds its usage. If the element
   * cannot be found then adds the deepest of the exiting parents.
   *
   * For Java compatibility looks for android.compileOptions.sourceCompatibility and android.compileOptions.targetCompatibility and adds
   * usage if its value is lower than 1.8. If the elements do not exist then adds usage of the parents.
   */
  override fun findUsages(): Array<UsageInfo> {
    val projectBuildModel = ProjectBuildModel.get(myProject)

    val usages = ArrayList<UsageInfo>()
    for (file in buildFiles) {
      if (!file.isValid || !file.isWritable) {
        continue
      }
      val elementToUsage = mutableMapOf<PsiElement, DexUsageInfo>()
      val android = projectBuildModel.getModuleBuildModel(file).android()
      val androidElement = (android as GradleDslBlockModel).psiElement!!

      // Api level usages (add deepest existing element of android.defaultConfig.minSdkVersion)
      if (apiLevel != null) {
        var usageElement: PsiElement? = null
        val apiLevelValue = Integer.valueOf(apiLevel)
        val defaultConfig = android.defaultConfig()
        val defaultConfigElement = (defaultConfig as GradleDslBlockModel).psiElement
        if (defaultConfigElement == null) {
          usageElement = androidElement
        }
        else {
          val minSdkVersion = defaultConfig.minSdkVersion()
          val minSdkVersionElement = minSdkVersion.fullExpressionPsiElement
          if (minSdkVersionElement == null) {
            usageElement = defaultConfigElement
          }
          else {
            val minSdkVersionValue = minSdkVersion.getValue(INTEGER_TYPE)
            if ((minSdkVersionValue == null) || (apiLevelValue > minSdkVersionValue)) {
              usageElement = minSdkVersionElement
            }
          }
        }
        if (usageElement != null) {
          elementToUsage.getOrPut(usageElement,  {DexUsageInfo(usageElement, file)}).apiLevel = apiLevel
        }
      }

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
        elementToUsage.getOrPut(it, {DexUsageInfo(it, file)}).setSourceCompatibility = true
      }
      getCompatibilityUsage(compileOptions.targetCompatibility())?.let {
        elementToUsage.getOrPut(it, {DexUsageInfo(it, file)}).setTargetCompatibility = true
      }
      usages.addAll(elementToUsage.values)
    }
    return usages.toTypedArray()
  }

  override fun performRefactoring(usages: Array<out UsageInfo>) {
    val projectBuildModel = ProjectBuildModel.get(myProject)
    for (file in buildFiles) {
      val fileUsages = Arrays.stream(usages)
        .filter { it is DexUsageInfo && it.buildFile == file }
        .collect(Collectors.toList())
      if (fileUsages.isEmpty()) {
        continue
      }
      val android = projectBuildModel.getModuleBuildModel(file).android()
      fileUsages.forEach {
        val dexUsage = it as DexUsageInfo
        if (dexUsage.apiLevel != null) {
          android.defaultConfig().minSdkVersion().setValue(Integer.valueOf(dexUsage.apiLevel))
        }
        if (dexUsage.setSourceCompatibility) {
          android.compileOptions().sourceCompatibility().setLanguageLevel(LanguageLevel.JDK_1_8)
        }
        if (dexUsage.setTargetCompatibility) {
          android.compileOptions().targetCompatibility().setLanguageLevel(LanguageLevel.JDK_1_8)
        }
      }
    }
    projectBuildModel.applyChanges()
  }

  override fun getCommandName() = "Enable desugaring$commandSuffix"

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getCodeReferencesText(usagesCount: Int, filesCount: Int): String {
        return "References to be changed: ${UsageViewBundle.getReferencesString(usagesCount, filesCount)}"
      }

      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader(): String {
        return "Enable desugaring${if (apiLevel != null) " and set min SDK API level to $apiLevel" else ""}."
      }
    }
  }
}

private class DexUsageInfo(element: PsiElement, val buildFile: VirtualFile): UsageInfo(element) {
  var apiLevel: String? = null
  var setSourceCompatibility: Boolean = false
  var setTargetCompatibility: Boolean = false

  override fun getTooltipText(): String {
    val lines = ArrayList<String>()
    if (setSourceCompatibility) {
      lines.add("android.compileOptions.sourceCompatibility to VERSION_1_8")
    }
    if (setTargetCompatibility) {
      lines.add("android.compileOptions.targetCompatibility to VERSION_1_8")
    }
    if (apiLevel != null) {
      lines.add("android.baseConfig.minSdkVersion to $apiLevel")
    }
    return lines.joinToString(prefix = "Set ", separator = ", ")
  }
}

private fun extractDexArchiveBuilderException(error: Throwable): Throwable? {
  var cause: Throwable? = error
  while (cause != null) {
    if (cause.javaClass.name.endsWith(".DexArchiveBuilderException")) {
      return cause
    }
    if (cause.cause == cause) {
      break
    }
    cause = cause.cause
  }
  return null
}

private fun extractModulePathFromError(error: Throwable): String? {
  var cause: Throwable? = error
  while (cause != null) {
    val message = cause.message
    if (message != null) {
      val matcher = FAILED_TASK_PATTERN.matcher(message)
      if (matcher.matches()) {
        return getParentModulePath(matcher.group(1)!!)
      }
    }
    if (cause.cause == cause) {
      break
    }
    cause = cause.cause
  }
  return null
}
