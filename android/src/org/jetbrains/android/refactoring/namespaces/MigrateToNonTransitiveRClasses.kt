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
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
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
import org.jetbrains.android.refactoring.module
import org.jetbrains.android.refactoring.offerToCreateBackupAndRun

const val REFACTORING_NAME = "Migrate to non-transitive R classes"

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

  override fun isEnabledOnDataContext(dataContext: DataContext) = isEnabledOnModule(dataContext.module)
  override fun isEnabledOnElements(elements: Array<PsiElement>) = isEnabledOnModule(ModuleUtil.findModuleForPsiElement(elements.first()))
  private fun isEnabledOnModule(module: Module?): Boolean = module?.getModuleSystem()?.isRClassTransitive == true
}

/**
 * [RefactoringActionHandler] for [MigrateToNonTransitiveRClassesAction].
 *
 * Since there's no user input required to start the refactoring, it just runs a fresh [MigrateToResourceNamespacesProcessor].
 */
class MigrateToNonTransitiveRClassesHandler : RefactoringActionHandler {
  override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
    dataContext?.module?.let(this::invoke)
  }

  override fun invoke(project: Project, elements: Array<PsiElement>, dataContext: DataContext?) {
    dataContext?.module?.let(this::invoke)
  }

  private fun invoke(module: Module) {
    val processor = MigrateToNonTransitiveRClassesProcessor(AndroidFacet.getInstance(module)!!)
    processor.setPreviewUsages(true)

    offerToCreateBackupAndRun(module.project, REFACTORING_NAME) {
      processor.run()
    }
  }
}

/**
 * Implements the "migrate to resource namespaces" refactoring by finding all references to resources and rewriting them.
 */
class MigrateToNonTransitiveRClassesProcessor(
  private val facetToMigrate: AndroidFacet
) : BaseRefactoringProcessor(facetToMigrate.module.project) {

  override fun getCommandName() = REFACTORING_NAME

  override fun findUsages(): Array<UsageInfo> {
    val progressIndicator = ProgressManager.getInstance().progressIndicator
    progressIndicator.isIndeterminate = true
    progressIndicator.text = "Finding R class usages..."
    val usages = findUsagesOfRClassesFromModule(facetToMigrate)

    progressIndicator.text = "Inferring package names..."
    inferPackageNames(facetToMigrate, usages, progressIndicator)

    progressIndicator.text = null
    return usages.toTypedArray()
  }

  override fun performRefactoring(usages: Array<out UsageInfo>) {
    val progressIndicator = ProgressManager.getInstance().progressIndicator
    progressIndicator.isIndeterminate = false
    progressIndicator.fraction = 0.0
    progressIndicator.text = "Rewriting resource references..."
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

    // TODO(b/137180850): change gradle.properties and sync
    //syncBeforeFinishingRefactoring(myProject, GradleSyncStats.Trigger.TRIGGER_REFACTOR_MIGRATE_TO_RESOURCE_NAMESPACES)
  }

  override fun createUsageViewDescriptor(usages: Array<UsageInfo>): UsageViewDescriptor = object : UsageViewDescriptorAdapter() {
    override fun getElements(): Array<PsiElement> = PsiElement.EMPTY_ARRAY
    override fun getProcessedElementsHeader() = "Resource references to migrate"
  }
}
