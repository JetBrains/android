/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.dsl.api.configurations.ConfigurationModel
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT
import com.android.utils.appendCapitalized
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usages.impl.rules.UsageType
import org.jetbrains.android.util.AndroidBundle

class CompileRuntimeConfigurationRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override val necessityInfo = RegionNecessity(IMPLEMENTATION_API_INTRODUCED, COMPILE_REMOVED)

  override fun findComponentUsages(): Array<out UsageInfo> {
    val usages = mutableListOf<UsageInfo>()

    fun computeReplacementName(name: String, compileReplacement: String): String? {
      return when {
        name == "compile" -> compileReplacement
        name.endsWith("Compile") && (name.startsWith("test") || name.startsWith("androidTest")) ->
          name.removeSuffix("Compile").appendCapitalized("implementation")
        name.endsWith("Compile") -> name.removeSuffix("Compile").appendCapitalized(compileReplacement)
        name == "runtime" -> "runtimeOnly"
        name.endsWith("Runtime") -> "${name}Only"
        name.endsWith("Api") && (name.startsWith("test") || name.startsWith("androidTest")) ->
          name.removeSuffix("Api").appendCapitalized("implementation")
        name == "provided" -> "compileOnly"
        name.endsWith("Provided") -> "${name.removeSuffix("Provided")}CompileOnly"
        name == "apk" -> "runtimeOnly"
        name.endsWith("Apk") -> "${name.removeSuffix("Apk")}RuntimeOnly"
        name == "publish" -> "runtimeOnly"
        name.endsWith("Publish") -> "${name.removeSuffix("Publish")}RuntimeOnly"
        else -> null
      }
    }

    fun maybeAddUsageForDependency(dependency: DependencyModel, compileReplacement: String, psiElement: PsiElement) {
      val configuration = dependency.configurationName()
      computeReplacementName(configuration, compileReplacement)?.let {
        val wrappedElement = WrappedPsiElement(psiElement, this, CHANGE_DEPENDENCY_CONFIGURATION_USAGE_TYPE)
        usages.add(ObsoleteConfigurationDependencyUsageInfo(wrappedElement, dependency, it))
      }
    }

    fun maybeAddUsageForConfiguration(configuration: ConfigurationModel, compileReplacement: String, psiElement: PsiElement) {
      val name = configuration.name()
      computeReplacementName(name, compileReplacement)?.let {
        val wrappedElement = WrappedPsiElement(psiElement, this, RENAME_CONFIGURATION_USAGE_TYPE)
        usages.add(ObsoleteConfigurationConfigurationUsageInfo(wrappedElement, configuration, it))
      }
    }

    projectBuildModel.allIncludedBuildModels.forEach model@{ model ->
      // if we don't have a PsiElement for the model, we don't have a file at all, and attempting to perform a refactoring is not
      // going to work
      val modelPsiElement = model.psiElement ?: return@model
      val pluginSet = model.plugins().map { it.name().forceString() }.toSet()
      // TODO(xof): as with the Java8Default refactoring above, we should define and use some kind of API
      //  to determine what kind of a module we have.
      val applicationSet = setOf(
        "application", "org.gradle.application", // see Gradle documentation for PluginDependenciesSpec for `org.gradle.` prefix
        "android", // deprecated but equivalent to com.android.application
        "com.android.application", "com.android.test", "com.android.instant-app")
      val librarySet = setOf(
        "java", "java-library", "org.gradle.java", "org.gradle.java-library",
        "com.android.base", "com.android.library", "com.android.dynamic-feature", "com.android.feature")
      val compileReplacement = when {
        !model.android().dynamicFeatures().toList().isNullOrEmpty() -> "api"
        pluginSet.intersect(applicationSet).isNotEmpty() -> "implementation"
        pluginSet.intersect(librarySet).isNotEmpty() -> "api"
        else -> return@model
      }

      model.dependencies().all()
        .forEach { dependency ->
          val psiElement = dependency.psiElement ?: model.dependencies().psiElement ?: modelPsiElement
          maybeAddUsageForDependency(dependency, compileReplacement, psiElement)
        }
      // Although there might be a buildscript with dependencies, those dependencies cannot be added to a compile/runtime configuration
      // out of the box -- and if somehow a user manages to configure things to have compile/runtime configurations there, there's no
      // guarantee that they mean the same as the deprecated gradle ones.
      model.configurations().all()
        .forEach { configuration ->
          // this PsiElement is used for displaying in the refactoring preview window, rather than for performing the refactoring; it is
          // therefore appropriate and safe to pass a notional parent Psi if the element is not (yet) on file.
          // TODO(b/159597456): here and elsewhere, encode this hierarchical logic more declaratively.
          val psiElement = configuration.psiElement ?: model.configurations().psiElement ?: modelPsiElement
          maybeAddUsageForConfiguration(configuration, compileReplacement, psiElement)
        }
    }
    return usages.toTypedArray()
  }

  override val readMoreUrlRedirect = ReadMoreUrlRedirect("compile-runtime-configuration")

  override fun getShortDescription(): String? = let {
    val mandatory = necessity().let { it == MANDATORY_CODEPENDENT || it == MANDATORY_INDEPENDENT }
    if (mandatory)
      """
        Some dependencies have been added to configurations which have been
        removed.  Those configurations must be replaced with their updated
        equivalents.
      """.trimIndent()
    else
      """
        Some dependencies have been added to configurations which have been
        deprecated.  Those configurations should be replaced with their
        updated equivalents.
      """.trimIndent()
  }


  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(UpgradeAssistantComponentKind.COMPILE_RUNTIME_CONFIGURATION)

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> = PsiElement.EMPTY_ARRAY

      override fun getProcessedElementsHeader(): String = AndroidBundle.message("project.upgrade.compileRuntimeConfigurationRefactoringProcessor.usageView.header")
    }
  }

  override fun getRefactoringId(): String = "com.android.tools.agp.upgrade.CompileRuntimeConfiguration"

  override fun getCommandName(): String = AndroidBundle.message("project.upgrade.compileRuntimeConfigurationRefactoringProcessor.commandName")

  companion object {
    val IMPLEMENTATION_API_INTRODUCED = AgpVersion.parse("3.1.0")
    val COMPILE_REMOVED = AgpVersion.parse("7.0.0-alpha03")

    val RENAME_CONFIGURATION_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("project.upgrade.compileRuntimeConfigurationRefactoringProcessor.renameConfigurationUsageType"))
    val CHANGE_DEPENDENCY_CONFIGURATION_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("project.upgrade.compileRuntimeConfigurationRefactoringProcessor.changeDependencyConfigurationUsageType"))
  }
}

class ObsoleteConfigurationDependencyUsageInfo(
  element: WrappedPsiElement,
  private val dependency: DependencyModel,
  private val newConfigurationName: String
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    dependency.setConfigurationName(newConfigurationName)
  }

  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.obsoleteConfigurationDependencyUsageInfo.tooltipText", dependency.configurationName(), newConfigurationName)

  override fun getDiscriminatingValues(): List<Any> = listOf(dependency, newConfigurationName)
}

class ObsoleteConfigurationConfigurationUsageInfo(
  element: WrappedPsiElement,
  private val configuration: ConfigurationModel,
  private val newConfigurationName: String
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    configuration.rename(newConfigurationName)
  }

  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.obsoleteConfigurationConfigurationUsageInfo.tooltipText", configuration.name(), newConfigurationName)

  override fun getDiscriminatingValues(): List<Any> = listOf(configuration, newConfigurationName)
}
