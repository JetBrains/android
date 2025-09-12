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

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel.RepositoryType.JCENTER_DEFAULT
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel.RepositoryType.MAVEN_CENTRAL
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usages.impl.rules.UsageType
import com.intellij.util.containers.toArray

/**
 * Starting from Gradle 9.0.0 (required by AGP 9.0.0), the `jcenter()` RepositoriesHandler method has been removed.  This processor
 * removes usages of `jcenter()`, and if any was removed, adds `mavenCentral()` if not already present.  The jcenter repository has been
 * redirecting to maven central since 2024 in any case.
 */
class JCenterToMavenCentralRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override val necessityInfo = RegionNecessity(SUPPORTED_FROM, GRADLE_SUPPORT_REMOVED)

  override fun findComponentUsages(): Array<out UsageInfo> {
    val usages = mutableListOf<UsageInfo>()
    fun process(repositoriesModel: RepositoriesModel) {
      if (!repositoriesModel.containsMethodCall("jcenter")) return
      var foundMavenCentral = false
      repositoriesModel.repositories().forEach { repositoryModel ->
        if (JCENTER_DEFAULT == repositoryModel.type) {
          val psiElement = repositoryModel.psiElement ?: return@forEach
          val wrappedPsiElement = WrappedPsiElement(psiElement, this, REMOVE_JCENTER)
          usages.add(RemoveRepositoryUsageInfo(wrappedPsiElement, repositoriesModel, repositoryModel))
        }
        if (MAVEN_CENTRAL == repositoryModel.type) {
          foundMavenCentral = true
        }
      }
      if (!foundMavenCentral) {
        val psiElement = repositoriesModel.psiElement ?: return
        val wrappedPsiElement = WrappedPsiElement(psiElement, this, INSERT_MAVEN_CENTRAL)
        usages.add(AddMavenCentralRepositoryUsageInfo(wrappedPsiElement, repositoriesModel))
      }
    }
    projectBuildModel.allIncludedBuildModels.forEach { model ->
      process(model.repositories())
    }
    projectBuildModel.projectBuildModel?.buildscript()?.repositories()?.let(::process)
    projectBuildModel.projectSettingsModel?.dependencyResolutionManagement()?.repositories()?.let(::process)
    projectBuildModel.projectSettingsModel?.pluginManagement()?.repositories()?.let(::process)
    return usages.toArray(UsageInfo.EMPTY_ARRAY)
  }

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.JCENTER_TO_MAVEN_CENTRAL)

  @Suppress("DialogTitleCapitalization")
  override fun getCommandName(): String = AgpUpgradeBundle.message("jcenterToMavenCentral.commandName")

  override fun getShortDescription() = """
    As of Gradle 9.0 and the Android Gradle Plugin version 9.0.0, the `jcenter()`
    repository declaration is no longer supported.  The jcenter repository itself
    has been in read-only mode since 2021.  This processor removes uses of `jcenter`
    and, if any of those are found, adds a mavenCentral repository declaration if
    one is not already present.
  """.trimIndent()

  override fun getRefactoringId() = "con.android.tools.agp.upgrade.jcenterToMavenCentral"

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo?>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<out PsiElement?> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader(): String = AgpUpgradeBundle.message("jcenterToMavenCentral.usageView.header")
    }
  }

  override val readMoreUrlRedirect = ReadMoreUrlRedirect("jcenter-to-maven-central")

  companion object {
    val SUPPORTED_FROM = AgpVersion.parse("3.2.0")
    val GRADLE_SUPPORT_REMOVED = AgpVersion.parse("9.0.0-alpha01")

    val REMOVE_JCENTER = UsageType(AgpUpgradeBundle.messagePointer("jcenterToMavenCentral.removeJCenter.usageType"))
    val INSERT_MAVEN_CENTRAL = UsageType(AgpUpgradeBundle.messagePointer("jcenterToMavenCentral.insertMavenCentral.usageType"))
  }
}

class RemoveRepositoryUsageInfo(wrappedPsiElement: WrappedPsiElement, private val repositoriesModel: RepositoriesModel, private val repositoryModel: RepositoryModel): GradleBuildModelUsageInfo(wrappedPsiElement) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    repositoriesModel.removeRepository(repositoryModel)
  }

  override fun getTooltipText(): String = AgpUpgradeBundle.message("jcenterToMavenCentral.removeJCenter.tooltipText")
}

class AddMavenCentralRepositoryUsageInfo(wrappedPsiElement: WrappedPsiElement, private val repositoriesModel: RepositoriesModel): GradleBuildModelUsageInfo(wrappedPsiElement) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    repositoriesModel.addRepositoryByMethodName("mavenCentral")
  }

  override fun getTooltipText(): String = AgpUpgradeBundle.message("jcenterToMavenCentral.insertMavenCentral.tooltipText")
}