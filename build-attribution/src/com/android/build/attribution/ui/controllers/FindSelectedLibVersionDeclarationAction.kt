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

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpecImpl
import com.android.tools.idea.gradle.dsl.parser.dependencies.FakeArtifactElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Factory
import com.intellij.usageView.UsageInfo
import com.intellij.usages.FindUsagesProcessPresentation
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfoSearcherAdapter
import com.intellij.usages.UsageSearcher
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageViewManager
import com.intellij.usages.UsageViewPresentation
import com.intellij.util.Processor
import java.util.function.Supplier

class FindSelectedLibVersionDeclarationAction(private val selectionSupplier: Supplier<String?>, private val project: Project) : AnAction(
  "Find Version Declarations") {
  override fun update(e: AnActionEvent) {
    if (selectionSupplier.get() == null) {
      e.presentation.isEnabledAndVisible = false
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val selectedDependency = selectionSupplier.get() ?: return

    val usageViewPresentation = UsageViewPresentation()
    usageViewPresentation.tabName = "Dependency Version Declaration"
    usageViewPresentation.tabText = "Dependency Version Declaration"
    usageViewPresentation.codeUsagesString = "Version declarations of $selectedDependency"
    usageViewPresentation.scopeText = "project build files"
    usageViewPresentation.searchString = selectedDependency
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
          return findVersionDeclarations(selectedDependency)
        }
      }
    }
    UsageViewManager.getInstance(project)
      .searchAndShowUsages(arrayOf<UsageTarget>(), factory, processPresentation, usageViewPresentation, null)
  }

  fun findVersionDeclarations(selectedDependency: String): Array<UsageInfo> {
    val selectedParsed = ArtifactDependencySpecImpl.create(selectedDependency) ?: return emptyArray()
    return ProjectBuildModel.get(project).allIncludedBuildModels.asSequence()
      .flatMap { model -> model.dependencies().artifacts() }
      .filter { dependency -> dependency.spec.group == selectedParsed.group && dependency.spec.name == selectedParsed.name }
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
}