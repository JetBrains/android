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
package org.jetbrains.android.refactoring.namespaces

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.getModuleSystem
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.facet.ProjectFacetManager
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.migration.PsiMigrationManager
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.actions.BaseRefactoringAction
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.refactoring.getProjectProperties
import org.jetbrains.android.refactoring.project
import org.jetbrains.android.refactoring.syncBeforeFinishingRefactoring
import org.jetbrains.android.util.AndroidBundle

private fun findFacetsToMigrate(project: Project): List<AndroidFacet> {
  return ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID).filter { facet ->
    facet.getModuleSystem().isRClassTransitive
  }
}

/**
 * Action to perform the refactoring.
 *
 * Decides if the refactoring is available and constructs the right [MigrateToNonTransitiveRClassesHandler] object if it is.
 */
class MigrateToNonTransitiveRClassesAction : BaseRefactoringAction() {
  override fun getHandler(dataContext: DataContext) = MigrateToNonTransitiveRClassesHandler()
  override fun isHidden() = StudioFlags.MIGRATE_TO_NON_TRANSITIVE_R_CLASSES_REFACTORING_ENABLED.get().not()
  override fun isAvailableInEditorOnly() = false
  override fun isAvailableForLanguage(language: Language?) = true

  override fun isEnabledOnDataContext(dataContext: DataContext) = dataContext.project?.let(this::isEnabledOnProject) ?: false
  override fun isEnabledOnElements(elements: Array<PsiElement>) = isEnabledOnProject(elements.first().project)

  override fun isAvailableOnElementInEditorAndFile(element: PsiElement, editor: Editor, file: PsiFile, context: DataContext): Boolean {
    return isEnabledOnProject(element.project)
  }

  private fun isEnabledOnProject(project: Project): Boolean = findFacetsToMigrate(project).isNotEmpty()
}

/**
 * [RefactoringActionHandler] for [MigrateToNonTransitiveRClassesAction].
 *
 * Since there's no user input required to start the refactoring, it just runs a fresh [MigrateToResourceNamespacesProcessor].
 */
class MigrateToNonTransitiveRClassesHandler : RefactoringActionHandler {
  override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) = invoke(project)
  override fun invoke(project: Project, elements: Array<PsiElement>, dataContext: DataContext?) = invoke(project)

  private fun invoke(project: Project) {
    val processor = MigrateToNonTransitiveRClassesProcessor.forEntireProject(project)
    processor.setPreviewUsages(true)
    processor.run()
  }
}

/**
 * Implements the "migrate to resource namespaces" refactoring by finding all references to resources and rewriting them.
 */
class MigrateToNonTransitiveRClassesProcessor private constructor(
  project: Project,
  private val facetsToMigrate: Collection<AndroidFacet>,
  private val updateTopLevelGradleProperties: Boolean
) : BaseRefactoringProcessor(project) {

  companion object {
    fun forSingleModule(facet: AndroidFacet): MigrateToNonTransitiveRClassesProcessor {
      return MigrateToNonTransitiveRClassesProcessor(facet.module.project, setOf(facet), updateTopLevelGradleProperties = false)
    }

    fun forEntireProject(project: Project): MigrateToNonTransitiveRClassesProcessor {
      return MigrateToNonTransitiveRClassesProcessor(
        project,
        facetsToMigrate = findFacetsToMigrate(project),
        updateTopLevelGradleProperties = true
      )
    }
  }

  override fun getCommandName() = AndroidBundle.message("android.refactoring.migrateto.nontransitiverclass.title")

  override fun findUsages(): Array<UsageInfo> {
    val progressIndicator = ProgressManager.getInstance().progressIndicator
    progressIndicator.isIndeterminate = true
    progressIndicator.text = AndroidBundle.message("android.refactoring.migrateto.nontransitiverclass.progress.findusages")
    val usages = facetsToMigrate.flatMap(::findUsagesOfRClassesFromModule)

    // TODO(b/137180850): handle the case where usages is empty better. Display gradle.properties as the only "usage", so there's something
    //   in the UI?

    progressIndicator.text = AndroidBundle.message("android.refactoring.migrateto.nontransitiverclass.progress.inferring")
    inferPackageNames(usages, progressIndicator)

    progressIndicator.text = null
    return usages.toTypedArray()
  }

  override fun performRefactoring(usages: Array<out UsageInfo>) {
    val progressIndicator = ProgressManager.getInstance().progressIndicator
    progressIndicator.isIndeterminate = false
    progressIndicator.fraction = 0.0
    progressIndicator.text = AndroidBundle.message("android.refactoring.migrateto.nontransitiverclass.progress.rewriting")
    val totalUsages = usages.size.toDouble()

    val psiMigration = PsiMigrationManager.getInstance(myProject).startMigration()
    try {
      usages.forEachIndexed { index, usageInfo ->
        if (usageInfo !is CodeUsageInfo) error("unexpected $usageInfo")
        usageInfo.updateClassReference(psiMigration)
        progressIndicator.fraction = (index + 1) / totalUsages
      }
    } finally {
      psiMigration.finish()
    }

    if (updateTopLevelGradleProperties) {
      myProject.getProjectProperties(createIfNotExists = true)?.apply {
        findPropertyByKey(NON_TRANSITIVE_R_CLASSES_PROPERTY)?.setValue("true") ?: addProperty(NON_TRANSITIVE_R_CLASSES_PROPERTY, "true")
        findPropertyByKey(NON_TRANSITIVE_APP_R_CLASSES_PROPERTY)?.setValue("true") ?: addProperty(NON_TRANSITIVE_APP_R_CLASSES_PROPERTY, "true")
        syncBeforeFinishingRefactoring(myProject, GradleSyncStats.Trigger.TRIGGER_REFACTOR_MIGRATE_TO_RESOURCE_NAMESPACES)
      }
    }

  }

  override fun createUsageViewDescriptor(usages: Array<UsageInfo>): UsageViewDescriptor = object : UsageViewDescriptorAdapter() {
    override fun getElements(): Array<PsiElement> = PsiElement.EMPTY_ARRAY
    override fun getProcessedElementsHeader() =
      AndroidBundle.message("android.refactoring.migrateto.resourceview.header")
  }
}
