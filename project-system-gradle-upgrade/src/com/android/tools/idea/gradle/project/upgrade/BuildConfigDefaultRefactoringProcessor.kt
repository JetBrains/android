/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.BOOLEAN_TYPE
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.Companion.standardPointNecessity
import com.android.tools.idea.project.getPackageName
import com.android.tools.idea.projectsystem.isMainModule
import com.android.tools.idea.util.androidFacet
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usages.impl.rules.UsageType
import org.jetbrains.android.util.AndroidBundle

class BuildConfigDefaultRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override fun necessity() = standardPointNecessity(current, new, AgpVersion.parse("8.0.0-beta01"))

  class SourcesNotGenerated(moduleNames: List<String>) : BlockReason(
    shortDescription = "Generated BuildConfig sources are missing",
    description = """
      The project is configured to generate BuildConfig sources, but those
      generated sources are missing from the project, in the following modules:
      ${moduleNames.descriptionText}.
      To proceed, execute the "Run Generate Sources Gradle Tasks" action and refresh
      the AGP Upgrade Assistant.
    """.trimIndent(),
    readMoreUrl = ReadMoreUrlRedirect("build-config-sources-not-generated"),
  )

  override fun blockProcessorReasons(): List<BlockReason> {
    val explicitProperty =
      projectBuildModel.projectBuildModel?.propertiesModel?.declaredProperties
        ?.any { it.name == "android.defaults.buildfeatures.buildconfig" }
      ?: false
    // If the user has already explicitly turned BuildConfig on or off, we should not block the upgrade.
    if (explicitProperty) return listOf()

    val moduleNames = mutableListOf<String>()
    val modules = ModuleManager.getInstance(project).modules.filter { it.isMainModule() }

    modules.forEach module@{ module ->
      val facet = module.androidFacet ?: return@module
      val generatedSourceFolders = GradleAndroidModel.get(facet)?.mainArtifact?.generatedSourceFolders ?: return@module
      if (!generatedSourceFolders.any { it.systemIndependentPath.contains("generated/source/buildConfig")}) {
        // If none of our generated source folders are for buildConfig, then the user must have turned it off.
        return@module
      }

      projectBuildModel.getModuleBuildModel(module)?.let { buildModel ->
        val buildConfigModel = buildModel.android().buildFeatures().buildConfig()
        val buildConfigEnabled = buildConfigModel.getValue(BOOLEAN_TYPE)
        // If we can find an explicit buildConfig directive for this module, true or false, then we should not block this processor.
        if (buildConfigEnabled != null) return@module
      }

      val namespace = GradleAndroidModel.get(facet)?.androidProject?.namespace ?: getPackageName(module)
      val className = "$namespace.BuildConfig"
      val buildConfigClass = JavaPsiFacade.getInstance(project).findClass(className, module.moduleContentScope)
      if (buildConfigClass == null) {
        // If we cannot resolve the BuildConfig class, but there is a generated folder for it, then (most likely) the sources have
        // not been generated; we should block this upgrade until the user generates sources.
        moduleNames.add(module.name)
      }
    }
    return when {
      moduleNames.isEmpty() -> listOf()
      else -> listOf(SourcesNotGenerated(moduleNames))
    }
  }

  override fun findComponentUsages(): Array<out UsageInfo> {
    val usages = mutableListOf<UsageInfo>()
    val explicitProperty =
      projectBuildModel.projectBuildModel?.propertiesModel?.declaredProperties
        ?.any { it.name == "android.defaults.buildfeatures.buildconfig" }
      ?: false
    if (explicitProperty) return UsageInfo.EMPTY_ARRAY
    val modules = ModuleManager.getInstance(project).modules.filter { it.isMainModule() }

    modules.forEach module@{ module ->
      val buildModel = projectBuildModel.getModuleBuildModel(module) ?: return@module
      val buildConfigModel = buildModel.android().buildFeatures().buildConfig()
      val buildConfigEnabled = buildConfigModel.getValue(BOOLEAN_TYPE)
      if (buildConfigEnabled != null) return@module

      val facet = module.androidFacet ?: return@module
      val namespace = GradleAndroidModel.get(facet)?.androidProject?.namespace ?: getPackageName(module)
      val className = "$namespace.BuildConfig"
      // This should succeed (given the check in blockProcessorReasons() above), but be defensive Just In Case.
      val buildConfigClass = JavaPsiFacade.getInstance(project).findClass(className, module.moduleContentScope) ?: return@module
      val references = ReferencesSearch.search(buildConfigClass, module.moduleContentScope)
      if (references.findAll().isEmpty()) return@module

      val buildFeaturesOrHigherPsiElement =
        listOf(buildModel.android().buildFeatures(), buildModel.android(), buildModel)
          .firstNotNullOfOrNull { it.psiElement }
        ?: return@module
      val psiElement = WrappedPsiElement(buildFeaturesOrHigherPsiElement, this, INSERT_BUILD_CONFIG_DIRECTIVE)
      usages.add(BuildConfigEnableUsageInfo(psiElement, buildConfigModel))
    }
    return usages.toTypedArray()
  }

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.BUILD_CONFIG_DEFAULT)

  override fun getCommandName() = AndroidBundle.message("project.upgrade.buildConfigDefaultRefactoringProcessor.commandName")

  override fun getShortDescription() = """
    The default value for buildFeatures.buildConfig has changed, and some
    modules in this project appear to be using BuildConfig.  Build files
    will be modified to explicitly turn BuildConfig on in modules referring
    to BuildConfig.
  """.trimIndent()

  override fun getRefactoringId() = "com.android.tools.agp.upgrade.buildConfigDefault"

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = AndroidBundle.message("project.upgrade.buildConfigDefaultRefactoringProcessor.usageView.header")
    }
  }

  // TODO(xof): find target for redirect
  override val readMoreUrlRedirect = ReadMoreUrlRedirect("build-config-default")

  companion object {
    val INSERT_BUILD_CONFIG_DIRECTIVE = UsageType(AndroidBundle.messagePointer("project.upgrade.buildConfigDefaultRefactoringProcessor.enable.usageType"))
  }
}

class BuildConfigEnableUsageInfo(
  element: WrappedPsiElement,
  private val resultModel: GradlePropertyModel,
): GradleBuildModelUsageInfo(element) {
  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.buildConfigBuildFeature.enable.tooltipText")

  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    resultModel.setValue(true)
  }
}