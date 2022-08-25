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
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.util.DeletablePsiElementHolder
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.IRRELEVANT_PAST
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.OPTIONAL_INDEPENDENT
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usages.impl.rules.UsageType
import org.jetbrains.android.util.AndroidBundle

class RemoveImplementationPropertiesRefactoringProcessor: AgpUpgradeComponentRefactoringProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  // This cannot be expressed as a point or region necessity, because although the implementation classes backing these properties
  // is scheduled to be altered only for AGP 8.0.0, and so for Groovy build files removing the properties is optional until then,
  // the interface definitions have already been removed, so it is necessary for KotlinScript build files in the 7.0.0-alpha series.
  // We cannot make it required by 7.0.0-alpha, though, because users might mistakenly include those properties in Groovy files without
  // receiving errors or even deprecation notices during the 7.x series.
  override val necessityInfo = object : AgpUpgradeComponentNecessityInfo() {
    override fun computeNecessity(current: AgpVersion, new: AgpVersion) = when {
      new < AgpVersion.parse("7.0.0-alpha13") -> OPTIONAL_INDEPENDENT
      current > AgpVersion.parse("8.0.0") -> IRRELEVANT_PAST
      else -> MANDATORY_INDEPENDENT
    }
  }

  override fun findComponentUsages(): Array<UsageInfo> {
    val usages = ArrayList<UsageInfo>()
    projectBuildModel.allIncludedBuildModels.forEach model@{ model ->
      val modelPsiElement = model.psiElement ?: return@model
      val moduleKind = model.moduleKind ?: return@model
      model.(moduleKind.implementationProperties)().forEach { propertyModel ->
        val psiElement = propertyModel.representativeContainedPsiElement ?: return@forEach
        val wrappedPsiElement = WrappedPsiElement(psiElement, this, REMOVE_IMPLEMENTATION_PROPERTY_USAGE_TYPE)
        val usageInfo = RemoveImplementationPropertyUsageInfo(wrappedPsiElement, propertyModel)
        usages.add(usageInfo)
      }
    }
    return usages.toTypedArray()
  }

  override fun getCommandName(): String = AndroidBundle.message("project.upgrade.removeImplementationPropertiesRefactoringProcessor.commandName")

  override fun getShortDescription(): String? =
    """
      The build configuration used to allow setting of properties for some
      modules, even where those properties do not have any effect for that kind
      of module.  In some circumstances from now, attempts to set those
      properties may cause errors rather than being silently ignored.
    """.trimIndent()

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.REMOVE_IMPLEMENTATION_PROPERTIES)

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = AndroidBundle.message("project.upgrade.removeImplementationPropertiesRefactoringProcessor.usageView.header")
    }
  }

  companion object {
    val REMOVE_IMPLEMENTATION_PROPERTY_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("project.upgrade.removeImplementationProperty.usageType"))
  }
}

class RemoveImplementationPropertyUsageInfo(
  element: WrappedPsiElement,
  val model: DeletablePsiElementHolder
): GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    model.delete()
  }

  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.removeImplementationProperty.tooltipText")
}

val GradleBuildModel.moduleKind: ModuleKind?
  get() {
    fun Set<String>.intersects(other: Set<String>) = this.any { other.contains(it) }
    val pluginSet = appliedPlugins().map { it.name().forceString() }.toSet()
    val appSet = setOf("android", "com.android.application")
    val librarySet = setOf("com.android.library")
    val testSet = setOf("com.android.test")
    val dynamicFeatureSet = setOf("com.android.dynamic-feature")
    return when {
      appSet.intersects(pluginSet) -> ModuleKind.APP
      librarySet.intersects(pluginSet) -> ModuleKind.LIBRARY
      dynamicFeatureSet.intersects(pluginSet) -> ModuleKind.DYNAMIC_FEATURE
      testSet.intersects(pluginSet) -> ModuleKind.TEST
      else -> null
    }
  }

enum class ModuleKind(val implementationProperties: GradleBuildModel.() -> List<GradlePropertyModel>) {
  APP(
    {
      listOf(
        android().aidlPackagedList(),
        android().targetProjectPath(),
        android().defaultConfig().consumerProguardFiles(),
        android().defaultConfig().dimension(),
      ) +
      android().buildTypes().map { it.consumerProguardFiles() } +
      android().productFlavors().map { it.consumerProguardFiles() }
    }),
  LIBRARY(
    {
      listOf(
        android().assetPacks(),
        android().dynamicFeatures(),
        android().targetProjectPath(),
        android().defaultConfig().applicationId(),
        android().defaultConfig().applicationIdSuffix(),
        android().defaultConfig().dimension(),
        android().defaultConfig().maxSdkVersion(),
        android().defaultConfig().versionCode(),
        android().defaultConfig().versionName(),
        android().defaultConfig().versionNameSuffix()
      ) +
      android().buildTypes().flatMap {
        listOf(it.crunchPngs(), it.debuggable(), it.embedMicroApp(), it.applicationIdSuffix(), it.versionNameSuffix())
      } +
      android().productFlavors().flatMap {
        listOf(it.applicationId(), it.applicationIdSuffix(), it.maxSdkVersion(), it.versionCode(), it.versionName(), it.versionNameSuffix())
      }
    }),
  DYNAMIC_FEATURE(
    {
      listOf(
        android().aidlPackagedList(),
        android().assetPacks(),
        android().dynamicFeatures(),
        android().targetProjectPath(),
        android().defaultConfig().applicationId(),
        android().defaultConfig().applicationIdSuffix(),
        android().defaultConfig().consumerProguardFiles(),
        android().defaultConfig().dimension(),
        android().defaultConfig().maxSdkVersion(),
        android().defaultConfig().multiDexEnabled(),
        android().defaultConfig().targetSdkVersion(),
        android().defaultConfig().versionCode(),
        android().defaultConfig().versionName(),
        android().defaultConfig().versionNameSuffix()
      ) +
      android().buildTypes().flatMap {
        listOf(
          it.debuggable(),
          it.isDefault(),
          it.embedMicroApp(),
          it.consumerProguardFiles(),
          it.applicationIdSuffix(),
          it.versionNameSuffix()
        )
      } +
      android().productFlavors().flatMap {
        listOf(
          it.isDefault(),
          it.consumerProguardFiles(),
          it.applicationId(),
          it.applicationIdSuffix(),
          it.multiDexEnabled(),
          it.versionCode(),
          it.versionName(),
          it.versionNameSuffix()
        )
      }
    }),
  TEST(
    {
      listOf(
        android().aidlPackagedList(),
        android().assetPacks(),
        android().dynamicFeatures(),
        android().defaultConfig().applicationId(),
        android().defaultConfig().applicationIdSuffix(),
        android().defaultConfig().consumerProguardFiles(),
        android().defaultConfig().dimension(),
        android().defaultConfig().versionCode(),
        android().defaultConfig().versionName(),
        android().defaultConfig().versionNameSuffix(),
      ) +
      android().buildTypes().flatMap {
        listOf(it.isDefault(), it.consumerProguardFiles(), it.embedMicroApp(), it.applicationIdSuffix(), it.versionNameSuffix())
      } +
      android().productFlavors().flatMap {
        listOf(
          it.isDefault(),
          it.consumerProguardFiles(),
          it.applicationId(),
          it.applicationIdSuffix(),
          it.versionCode(),
          it.versionName(),
          it.versionNameSuffix()
        )
      }
    }),
}

