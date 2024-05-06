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
package com.android.build.attribution.ui.controllers

import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.view.details.JetifierWarningDetailsView
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpecImpl
import com.android.tools.idea.gradle.dsl.parser.dependencies.FakeArtifactElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.google.common.base.Stopwatch
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Factory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.usageView.UsageInfo
import com.intellij.usages.FindUsagesProcessPresentation
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfoSearcherAdapter
import com.intellij.usages.UsageSearcher
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageView
import com.intellij.usages.UsageViewManager
import com.intellij.usages.UsageViewPresentation
import com.intellij.util.PlatformIcons
import com.intellij.util.Processor
import java.util.function.Supplier
import javax.swing.Icon

class FindSelectedLibVersionDeclarationAction(
  private val selectionSupplier: Supplier<JetifierWarningDetailsView.DirectDependencyDescriptor?>,
  private val project: Project,
  private val analytics: BuildAttributionUiAnalytics,
) : AnAction(
  "Find Version Declarations") {
  override fun update(e: AnActionEvent) {
    if (selectionSupplier.get() == null) {
      e.presentation.isEnabledAndVisible = false
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val selectedDependency = selectionSupplier.get() ?: return

    val watch = Stopwatch.createStarted()

    val usageViewPresentation = UsageViewPresentation()
    usageViewPresentation.tabName = "Dependency Version Declaration"
    usageViewPresentation.tabText = "Dependency Version Declaration"
    usageViewPresentation.targetsNodeText = "Dependency"
    val fullNameWithoutVersion = selectedDependency.fullName.substringBeforeLast(":")
    usageViewPresentation.codeUsagesString = "Version declarations"
    val pluralizedProject = StringUtil.pluralize("project", selectedDependency.projects.size)
    usageViewPresentation.scopeText = selectedDependency.projects.joinToString(prefix = "$pluralizedProject ", limit = 5)
    usageViewPresentation.searchString = fullNameWithoutVersion
    usageViewPresentation.isOpenInNewTab = false
    val processPresentation = FindUsagesProcessPresentation(usageViewPresentation)
    processPresentation.isShowNotFoundMessage = true
    processPresentation.isShowPanelIfOnlyOneUsage = false
    val factory = Factory<UsageSearcher> {
      object : UsageInfoSearcherAdapter() {
        override fun generate(processor: Processor<in Usage>) {
          processUsages(processor, project)
        }

        override fun findUsages(): Array<UsageInfo> {
          return findVersionDeclarations(project, selectedDependency)
        }
      }
    }
    val listener = object : UsageViewManager.UsageViewStateListener {
      override fun usageViewCreated(usageView: UsageView) = Unit
      override fun findingUsagesFinished(usageView: UsageView?) = analytics.findLibraryVersionDeclarationActionUsed(watch.elapsed())
    }
    val target = object : UsageTarget {
      override fun getName(): String = fullNameWithoutVersion
      override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String = fullNameWithoutVersion
        override fun getIcon(unused: Boolean): Icon = PlatformIcons.LIBRARY_ICON
      }

      override fun isValid(): Boolean = true
      override fun findUsages() = Unit
    }
    UsageViewManager.getInstance(project)
      .searchAndShowUsages(arrayOf(target), factory, processPresentation, usageViewPresentation, listener)
  }

}

fun findVersionDeclarations(project: Project, selectedDependency: JetifierWarningDetailsView.DirectDependencyDescriptor): Array<UsageInfo> {
  val selectedParsed = ArtifactDependencySpecImpl.create(selectedDependency.fullName) ?: return emptyArray()
  val rootBuildModel = ProjectBuildModel.get(project).projectBuildModel ?: return emptyArray()
  val modelsForSearch = ProjectBuildModel.get(project).projectSettingsModel?.let {
    selectedDependency.projects.mapNotNull { gradleProjectPath -> it.moduleModel(gradleProjectPath) }
  } ?: listOf(rootBuildModel)
  return modelsForSearch.asSequence()
    .flatMap { model -> model.dependencies().artifacts() }
    .filter { dependency ->
      dependency.spec.let {
        it.group == selectedParsed.group && it.name == selectedParsed.name
      }
    }
    .mapNotNull { dependency ->
      val versionElement = dependency.version().resultModel.rawElement
      fun extractDependencyPsi() = when (val dependencyElement = dependency.completeModel().resultModel.rawElement) {
        is GradleDslLiteral -> dependencyElement.expression
        else -> dependencyElement?.psiElement
      }
      when (versionElement) {
        null -> // Version declaration is not found, return dependency declaration.
          extractDependencyPsi()
        is FakeArtifactElement -> // Version declared as part of dependency declaration, return dependency declaration.
          extractDependencyPsi()
        else -> // Version declared in a separate element, return it's psi.
          versionElement.psiElement
      }
    }
    .map { UsageInfo(it) }
    .distinct()
    .toList()
    .toTypedArray()
}
