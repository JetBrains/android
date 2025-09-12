/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.ide.common.gradle.Version
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.dependencies.PluginsHelper
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.BOOLEAN_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.project.upgrade.GradlePluginsRefactoringProcessor.Companion.`kotlin-gradle-plugin-compatibility-info`
import com.android.tools.idea.gradle.util.CompatibleGradleVersion.Companion.getCompatibleGradleVersion
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiElement
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usages.impl.rules.UsageType
import org.jetbrains.annotations.NonNls

/**
 * Some of the logic embodied by this class is convoluted, partly because the chain of decisions
 * leading to the necessity of this refactoring is long, and partly because of design decisions
 * made some time in the past.  Although this is the AGP Upgrade Assistant, arbitrary third-party
 * Gradle plugins (such as the Kotlin plugin) are upgraded not directly based on the AGP version
 * change, but based on the change to the minimum compatible Gradle version for the target AGP
 * version; at the time this implementation decision was made (around 2020), the major
 * incompatibility in third-party plugins seemed to be from Gradle itself, but as of 2025 this is
 * very much not the case.
 *
 * In this specific instance: AGP versions in 8.x and below require various 1.x Kotlin versions,
 * while AGP 9.0 requires Kotlin 2.0.  However, Kotlin 2.0 changes the way that Compose projects
 * must be declared, relative to Kotlin 1.x; we must apply a special Kotlin compiler Gradle plugin
 * (and the old composeOptions.kotlinCompilerExtensionsVersion declaration must be removed).  We
 * must do this if:
 * - the project has buildFeatures.compose = true; and
 * - the project does not yet have the kotlin compiler plugin enabled; and
 * - we are actually upgrading a project over the 8.x to 9.0 boundary.
 *
 * Since a compose-using project would not sync if it were already using a 2.x Kotlin without the
 * Kotlin compiler gradle plugin applied, we can unconditionally perform this refactoring to the
 * version of Kotlin suggested by the [GradlePluginsRefactoringProcessor], as this is guaranteed
 * to be the version of Kotlin used in the target project.
 */
class Kotlin20RefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new) {
    this.kotlinVersion = `kotlin-gradle-plugin-compatibility-info`(getCompatibleGradleVersion(new))
  }
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor) {
    this.kotlinVersion = `kotlin-gradle-plugin-compatibility-info`(getCompatibleGradleVersion(processor.new))
  }

  private val kotlinVersion: Version

  override val necessityInfo: AgpUpgradeComponentNecessityInfo = PointNecessity(AgpVersion.parse("9.0.0-alpha01"))

  override fun getShortDescription(): String = AgpUpgradeBundle.message("kotlin20.shortDescription")

  override fun getRefactoringId(): @NonNls String = "com.android.tools.upgrade.agp.kotlin20"

  override fun findComponentUsages(): Array<out UsageInfo> {
    val usages = mutableListOf<UsageInfo>()
    projectBuildModel.allIncludedBuildModels.forEach { model ->
      if (model.android().buildFeatures().compose().getValue(BOOLEAN_TYPE) != true) return@forEach
      if (model.appliedPlugins().any { it.name().toString() == "org.jetbrains.kotlin.plugin.compose" }) return@forEach
      val pluginsPsiElement = model.pluginsPsiElement ?: model.psiFile ?: return@forEach
      pluginsPsiElement.let { psiElement ->
        val wrappedPsiElement = WrappedPsiElement(psiElement, this, COMPILER_PLUGIN_USAGE_TYPE)
        usages.add(ApplyComposeCompilerPluginUsageInfo(wrappedPsiElement, projectBuildModel, model, kotlinVersion))
      }
      val kotlinCompilerExtensionVersionModel = model.android().composeOptions().kotlinCompilerExtensionVersion()
      if (kotlinCompilerExtensionVersionModel.getValue(STRING_TYPE) != null) {
        kotlinCompilerExtensionVersionModel.psiElement?.let { psiElement ->
          val wrappedPsiElement = WrappedPsiElement(psiElement, this, COMPOSE_OPTION_USAGE_TYPE)
          usages.add(RemoveComposeOptionUsageInfo(wrappedPsiElement, kotlinCompilerExtensionVersionModel))
        }
      }
      // TODO: should we also remove KotlinCompilerVersion?
    }
    return usages.toTypedArray()
  }

  override fun getCommandName(): String = AgpUpgradeBundle.message("kotlin20.commandName")

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder) =
    builder.setKind(UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.KOTLIN20_COMPOSE)

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo?>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<out PsiElement?> = PsiElement.EMPTY_ARRAY

      override fun getProcessedElementsHeader(): @NlsContexts.ListItem String? =
        AgpUpgradeBundle.message("kotlin20.usageView.header")
    }
  }

  companion object {
    val COMPILER_PLUGIN_USAGE_TYPE = UsageType(AgpUpgradeBundle.messagePointer("kotlin20.compilerPlugin.usageType"))
    val COMPOSE_OPTION_USAGE_TYPE = UsageType(AgpUpgradeBundle.messagePointer("kotlin20.composeOption.usageType"))
  }
}

class ApplyComposeCompilerPluginUsageInfo(
  element: WrappedPsiElement,
  val projectModel: ProjectBuildModel,
  val model: GradleBuildModel,
  val kotlinVersion: Version
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    PluginsHelper.withModel(projectModel).addPluginOrClasspath(PLUGIN_ID, MODULE, kotlinVersion.toString(), listOf(model))
  }

  override fun getTooltipText(): String = AgpUpgradeBundle.message("kotlin20.compilerPlugin.tooltipText")
  companion object {
    const val PLUGIN_ID = "org.jetbrains.kotlin.plugin.compose"
    const val MODULE = "org.jetbrains.kotlin:compose-compiler-gradle-plugin"
  }
}

class RemoveComposeOptionUsageInfo(element: WrappedPsiElement, val model: ResolvedPropertyModel) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    model.delete()
  }
  override fun getTooltipText(): String = AgpUpgradeBundle.message("kotlin20.composeOption.tooltipText")
}