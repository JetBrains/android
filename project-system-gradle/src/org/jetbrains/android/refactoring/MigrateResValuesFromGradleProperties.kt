/*
 * Copyright (C) 2024 The Android Open Source Project
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

package org.jetbrains.android.refactoring

import com.android.annotations.concurrency.UiThread
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.BOOLEAN_TYPE
import com.android.tools.idea.gradle.dsl.utils.FN_GRADLE_PROPERTIES
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.projectsystem.getSyncManager
import com.android.tools.idea.projectsystem.gradle.isMainModule
import com.android.tools.idea.projectsystem.toReason
import com.android.tools.idea.util.androidFacet
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.lang.Language
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.psi.impl.PropertiesFileImpl
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.vfs.findFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import java.util.Locale

private const val RES_VALUES_PROPERTY = "android.defaults.buildfeatures.resvalues"
private val CHANGED_DEFAULT_VERSION = AgpVersion.parse("9.0.0")

private fun shouldEnable(project: Project): Boolean {
  val propertiesFile = project.baseDir?.findFile(FN_GRADLE_PROPERTIES) ?: return false
  if (!propertiesFile.isValid) return false
  val version = GradleProjectSystemUtil.getAndroidGradleModelVersionInUse(project) ?: return false
  val propertiesPsi = PsiManager.getInstance(project).findFile(propertiesFile) as? PropertiesFile ?: return false
  val property = propertiesPsi.findPropertyByKey(RES_VALUES_PROPERTY)

  return when {
    // if the default for this AGP is true, we need to do work unless there is an explicit setting of false in gradle.properties.
    version < CHANGED_DEFAULT_VERSION -> property?.value?.lowercase(Locale.US) != "false"
    // if the default for this AGP is false, we need to do work unless the setting is not present in gradle.properties.
    version >= CHANGED_DEFAULT_VERSION -> property != null
    // Kotlin does not know that the above three clauses are exhaustive.
    else -> error("unreachable")
  }
}

class MigrateResValuesFromGradlePropertiesAction : AndroidGradleBaseRefactoringAction() {
  override fun getHandler(dataContext: DataContext) = MigrateResValuesFromGradlePropertiesHandler()
  override fun isAvailableInEditorOnly() = false
  override fun isAvailableForLanguage(language: Language?) = true
  override fun isEnabledOnDataContext(dataContext: DataContext) = dataContext.project?.let { shouldEnable(it) } ?: false
  override fun isEnabledOnElements(elements: Array<PsiElement>) = shouldEnable(elements.first().project)
  override fun isAvailableOnElementInEditorAndFile(element: PsiElement, editor: Editor, file: PsiFile, context: DataContext) =
    shouldEnable(element.project)
}

class MigrateResValuesFromGradlePropertiesHandler : RefactoringActionHandler {
  @UiThread
  override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) = invoke(project)

  @UiThread
  override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) = invoke(project)

  @UiThread
  private fun invoke(project: Project) {
    val progressManager = ProgressManager.getInstance()
    var processor: MigrateResValuesFromGradlePropertiesRefactoringProcessor? = null
    progressManager.runProcessWithProgressSynchronously(
      {
        val indicator = progressManager.progressIndicator
        indicator?.let {
          indicator.checkCanceled()
          indicator.isIndeterminate = true
          indicator.text = "Getting Android Gradle Plugin version"
        }
        val agpVersion = GradleProjectSystemUtil.getAndroidGradleModelVersionInUse(project) ?: return@runProcessWithProgressSynchronously
        processor = MigrateResValuesFromGradlePropertiesRefactoringProcessor(project, agpVersion, indicator)
      }, "Migrate Res Values from Gradle Properties", true, project)
    processor?.apply {
      PsiManager.getInstance(project).dropPsiCaches()
      DumbService.getInstance(project).completeJustSubmittedTasks()
      setPreviewUsages(true)
      run()
    }
  }
}

class MigrateResValuesFromGradlePropertiesRefactoringProcessor(
  project: Project,
  private val agpVersion: AgpVersion,
  indicator: ProgressIndicator?
) : BaseRefactoringProcessor(project) {
  private val projectBuildModel: ProjectBuildModel
  private val targets = ArrayList<PsiElement>()
  private var gradlePropertiesFile: PropertiesFileImpl? = null

  init {
    projectBuildModel = ProjectBuildModel.get(project)
    projectBuildModel.getAllIncludedBuildModels { seen, total ->
      indicator?.let {
        indicator.checkCanceled()
        indicator.text = "Parsing file $seen${if (total != null) " of $total" else ""}"
        indicator.isIndeterminate = total == null
        total?.let { indicator.fraction = seen.toDouble() / total.toDouble() }
      }
    }
  }

  // TODO(xof): AndroidBundleize
  override fun getCommandName() = "Migrate resValues setting from gradle.properties to build Dsl"

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>) = object : UsageViewDescriptorAdapter() {
    override fun getElements() = targets.toTypedArray()
    override fun getProcessedElementsHeader() = "Migrate resValues setting to build Dsl"
  }

  override fun findUsages(): Array<out UsageInfo> {
    val usages = mutableListOf<UsageInfo>()
    val baseDir = myProject.baseDir ?: return usages.toTypedArray()
    if (!baseDir.exists()) return usages.toTypedArray()
    baseDir.findChild(FN_GRADLE_PROPERTIES)?.let { gradlePropertiesVirtualFile ->
      gradlePropertiesFile = PsiManager.getInstance(myProject).findFile(gradlePropertiesVirtualFile) as? PropertiesFileImpl
    }
    val newSetting = if (agpVersion > CHANGED_DEFAULT_VERSION) null else false
    when (val propertiesFile = gradlePropertiesFile) {
      null -> {
        val directory = PsiManager.getInstance(myProject).findDirectory(baseDir) ?: return usages.toTypedArray()
        if (newSetting != null) {
          usages.add(NewGradlePropertiesUsageInfo(directory, newSetting))
        }
        targets.add(directory)
      }
      else -> {
        usages.add(ExistingGradlePropertiesUsageInfo(propertiesFile, newSetting))
        propertiesFile.findPropertyByKey(RES_VALUES_PROPERTY)?.psiElement?.let { targets.add(it) }
      }
    }

    myProject.modules.forEach module@{ module ->
      val facet = module.androidFacet ?: return@module
      if (!module.isMainModule()) return@module
      val generatedResourceFolders = GradleAndroidModel.get(facet)?.mainArtifact?.generatedResourceFolders ?: return@module
      if (!generatedResourceFolders.any { it.systemIndependentPath.contains("generated/res/resValues") }) {
        // If none of our generated resource folders are for resValues, then the user must have turned it off here already.
        return@module
      }

      projectBuildModel.getModuleBuildModel(module)?.let { buildModel ->
        val resValuesModel = buildModel.android().buildFeatures().resValues()
        val resValuesEnabled = resValuesModel.getValue(BOOLEAN_TYPE)
        // If we can find an explicit resValues directive for this module, true or false, then we don't need to do anything.
        if (resValuesEnabled != null) return@module

        if (buildModel.android().defaultConfig().resValues().isNotEmpty() ||
            buildModel.android().buildTypes().any { it.resValues().isNotEmpty() } ||
            buildModel.android().productFlavors().any { it.resValues().isNotEmpty() }) {
          // We have resValues Dsl in this module. Make sure the feature is turned on explicitly in this module.
          buildModel.psiFile?.let {
            usages.add(ModuleWithResValuesUsageInfo(it, projectBuildModel, module))
          }
        }
      }
    }

    return usages.toTypedArray()
  }

  override fun performRefactoring(usages: Array<out UsageInfo>) {
    CommandProcessor.getInstance().markCurrentCommandAsGlobal(this.myProject)
    usages.forEach { (it as? MigrateBuildPropertiesUsageInfo)?.doIt() }
  }

  override fun performPsiSpoilingRefactoring() {
    projectBuildModel.applyChanges()

    val documentManager = PsiDocumentManager.getInstance(this.myProject)
    gradlePropertiesFile?.let {
      val document = documentManager.getDocument(it) ?: return@let
      if (documentManager.isDocumentBlockedByPsi(document)) {
        documentManager.doPostponedOperationsAndUnblockDocument(document)
      }
      FileDocumentManager.getInstance().saveDocument(document)
      if (!documentManager.isCommitted(document)) {
        documentManager.commitDocument(document)
      }
    }

    myProject.getSyncManager().requestSyncProject(GradleSyncStats.Trigger.TRIGGER_REFACTOR_MIGRATE_RES_VALUES_FROM_GRADLE_PROPERTIES.toReason())
    UndoManager.getInstance(myProject).undoableActionPerformed(object : BasicUndoableAction() {
      override fun undo() {
        myProject.getSyncManager().requestSyncProject(GradleSyncStats.Trigger.TRIGGER_MODIFIER_ACTION_UNDONE.toReason())
      }
      override fun redo() {
        myProject.getSyncManager().requestSyncProject(GradleSyncStats.Trigger.TRIGGER_MODIFIER_ACTION_REDONE.toReason())
      }
    })
  }

  abstract class MigrateBuildPropertiesUsageInfo(element: PsiElement): UsageInfo(element) {
    abstract fun doIt()
  }

  class ExistingGradlePropertiesUsageInfo(
    val element: PropertiesFileImpl,
    val newSetting: Boolean?
  ): MigrateBuildPropertiesUsageInfo(element) {
    override fun doIt() {
      when (newSetting) {
        null -> element.findPropertyByKey(RES_VALUES_PROPERTY)?.psiElement?.delete()
        else -> element.findPropertyByKey(RES_VALUES_PROPERTY)?.setValue("$newSetting")
      }
    }
  }

  class NewGradlePropertiesUsageInfo(val element: PsiDirectory, val newSetting: Boolean): MigrateBuildPropertiesUsageInfo(element) {
    override fun doIt() {
      val propertiesFile = element.createFile(FN_GRADLE_PROPERTIES) as? PropertiesFileImpl
      propertiesFile?.addProperty(RES_VALUES_PROPERTY, "$newSetting")
    }
  }

  class ModuleWithResValuesUsageInfo(
    element: PsiElement,
    val buildModel: ProjectBuildModel,
    val module: Module
  ): MigrateBuildPropertiesUsageInfo(element) {
    override fun doIt() {
      buildModel.getModuleBuildModel(module)?.android()?.buildFeatures()?.resValues()?.setValue(true)
    }
  }
}
