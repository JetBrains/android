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
package org.jetbrains.android.refactoring

import com.android.annotations.concurrency.UiThread
import com.android.ide.common.repository.AgpVersion
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.requestProjectSync
import com.android.tools.idea.projectsystem.gradle.isHolderModule
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.facet.ProjectFacetManager
import com.intellij.lang.Language
import com.intellij.lang.properties.psi.impl.PropertiesFileImpl
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.refactoring.namespaces.PropertiesUsageInfo
import org.jetbrains.android.util.AndroidBundle
import java.util.UUID

const val DEFAULT_TARGET_SDK_TO_COMPILE_SDK_IF_UNSET_PROPERTY = "android.sdk.defaultTargetSdkToCompileSdkIfUnset"

private fun findFacetsToMigrate(project: Project): List<AndroidFacet> {
  return ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID).filter { facet ->
    val isNotHolderModule =  !facet.module.isHolderModule()
    val projectProperties = facet.module.project.getProjectProperties()
    val shouldMigrateDefaultTargetSdk = projectProperties
      ?.findPropertyByKey(DEFAULT_TARGET_SDK_TO_COMPILE_SDK_IF_UNSET_PROPERTY)
      ?.value == "false"

    isNotHolderModule && shouldMigrateDefaultTargetSdk
  }
}

/**
 * Action to perform the refactoring.
 *
 * Decides if the refactoring is available and constructs the right [MigrateToDefaultTargetSdkToCompileSdkIfUnsetHandler] object if it is.
 */
class MigrateToDefaultTargetSdkToCompileSdkIfUnsetAction: AndroidGradleBaseRefactoringAction() {
  override fun getHandler(dataContext: DataContext) = MigrateToDefaultTargetSdkToCompileSdkIfUnsetHandler()
  override fun isHidden() = false
  override fun isAvailableInEditorOnly() = false
  override fun isAvailableForLanguage(language: Language?) = true
  override fun isEnabledOnDataContext(dataContext: DataContext) = dataContext.project?.let(this::isEnabledOnProject) ?: false
  override fun isEnabledOnElements(elements: Array<PsiElement>) = elements.firstOrNull()?.let { isEnabledOnProject(it.project) } ?: false
  override fun isAvailableOnElementInEditorAndFile(element: PsiElement, editor: Editor, file: PsiFile, context: DataContext) = isEnabledOnProject(element.project)

  private fun isEnabledOnProject(project: Project): Boolean = findFacetsToMigrate(project).isNotEmpty()
}

class MigrateToDefaultTargetSdkToCompileSdkIfUnsetHandler : RefactoringActionHandler {

  @UiThread
  override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?)  = invoke(project)

  @UiThread
  override fun invoke(project: Project, elements: Array<PsiElement>, dataContext: DataContext?) = invoke(project)

  private fun invoke(project: Project) {
    val pluginInfo = AndroidPluginInfo.findFromModel(project)

    if (pluginInfo == null) {
      Messages.showErrorDialog(
        project,
        AndroidBundle.message("android.refactoring.migrateto.defaulttargetsdktocompilesdkifunset.error.message"),
        AndroidBundle.message("android.refactoring.migrateto.defaulttargetsdktocompilesdkifunset.error.title"),
      )
      return
    }

    // If for some reason Android Gradle Plugin version cannot be found, assume users have the correct version for the refactoring i.e. 8.11.0
    val pluginVersion = pluginInfo.pluginVersion ?: AgpVersion(8, 11, 0)

    val processor = MigrateToDefaultTargetSdkToCompileSdkIfUnsetProcessor.forEntireProject(project, pluginVersion)
    processor.setPreviewUsages(true)
    processor.run()
  }
}

class MigrateToDefaultTargetSdkToCompileSdkIfUnsetProcessor private constructor(
  project: Project,
  private val facetsToMigrate: Collection<AndroidFacet>,
  private val updateTopLevelGradleProperties: Boolean,
  private val agpVersion: AgpVersion
) : BaseRefactoringProcessor(project) {

  val uuid = UUID.randomUUID().toString()
  val projectBuildModel = ProjectBuildModel.get(myProject)

  companion object {
    private val DEFAULT_TARGET_SDK_TO_COMPILE_SDK_IF_UNSET_ENABLE_BY_DEFAULT = AgpVersion.parse("9.0.0")
    private val DEFAULT_TARGET_SDK_TO_COMPILE_SDK_IF_UNSET_AVAILABLE = AgpVersion.parse("8.11.0")

    private val LOG = Logger.getInstance(BaseRefactoringProcessor::class.java)

    fun forEntireProject(project: Project, agpVersion: AgpVersion): MigrateToDefaultTargetSdkToCompileSdkIfUnsetProcessor {
      return MigrateToDefaultTargetSdkToCompileSdkIfUnsetProcessor(
        project,
        facetsToMigrate = findFacetsToMigrate(project),
        updateTopLevelGradleProperties = true,
        agpVersion
      )
    }
  }

  override fun createUsageViewDescriptor(usages: Array<UsageInfo>): UsageViewDescriptor = object : UsageViewDescriptorAdapter() {
    override fun getElements(): Array<PsiElement> = PsiElement.EMPTY_ARRAY
    override fun getProcessedElementsHeader() =
      AndroidBundle.message("android.refactoring.migrateto.resourceview.header")
  }

  override fun findUsages(): Array<UsageInfo> {
    val progressIndicator = ProgressManager.getInstance().progressIndicator

    progressIndicator?.isIndeterminate = true
    progressIndicator?.text = AndroidBundle.message(
      "android.refactoring.migrateto.defaulttargetsdktocompilesdkifunset.progress.findusages")


    val usages: List<UsageInfo> = facetsToMigrate.mapNotNull { facet ->
      val gradleAndroidModel: GradleAndroidModel? = GradleAndroidModel.get(facet)

      if (gradleAndroidModel?.selectedVariant?.targetSdkVersion == null && gradleAndroidModel?.selectedVariant?.minSdkVersion != null) {

        projectBuildModel.getModuleBuildModel(facet.module)?.let {
          buildModel ->
          buildModel.psiFile?.let {
            MigrateUsageInfo(it, projectBuildModel, facet.module, gradleAndroidModel.minSdkVersion)
          }
        }
      }
      else {
        null
      }
    }

    progressIndicator?.text = null

    val allUsageInfos = usages.toMutableList()

    if (updateTopLevelGradleProperties) {
      val propertiesFile = myProject.getProjectProperties(createIfNotExists = false)
      if (propertiesFile != null) {
         allUsageInfos.add(
           PropertiesUsageInfo(
             DEFAULT_TARGET_SDK_TO_COMPILE_SDK_IF_UNSET_PROPERTY,
             propertiesFile.containingFile
           )
         )
      }
    }

    return allUsageInfos.toTypedArray()
  }

  override fun performRefactoring(usages: Array<out UsageInfo>) {

    CommandProcessor.getInstance().markCurrentCommandAsGlobal(this.myProject)
    usages.forEach { (it as? MigratePropertiesUsageInfo)?.doIt() }

    if (updateTopLevelGradleProperties) {
      val onByDefault = agpVersion >= DEFAULT_TARGET_SDK_TO_COMPILE_SDK_IF_UNSET_ENABLE_BY_DEFAULT
      val available = agpVersion >= DEFAULT_TARGET_SDK_TO_COMPILE_SDK_IF_UNSET_AVAILABLE
      val propertiesFile = myProject.getProjectProperties(createIfNotExists = !onByDefault)
      if (propertiesFile != null) {
        when {
          onByDefault -> {
            propertiesFile.findPropertyByKey(
              DEFAULT_TARGET_SDK_TO_COMPILE_SDK_IF_UNSET_PROPERTY)?.psiElement?.delete()
          }

          available -> {
            propertiesFile.findPropertyByKey(
              DEFAULT_TARGET_SDK_TO_COMPILE_SDK_IF_UNSET_PROPERTY)?.setValue("true")
          }

          else -> {
            LOG.error("AGP version too low for DefaultTargetSdkToCompileSdkIfUnset: $agpVersion")
          }
        }
      }
    }
  }

  override fun performPsiSpoilingRefactoring() {
    projectBuildModel.applyChanges()

    val documentManager = PsiDocumentManager.getInstance(this.myProject)

    val gradlePropertiesFile = myProject.getProjectProperties(false)?.virtualFile?.let {
      PsiManager.getInstance(myProject).findFile(it) as? PropertiesFileImpl
    }

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

    UndoManager.getInstance(myProject).undoableActionPerformed(object : BasicUndoableAction() {
      override fun undo() {
        GradleSyncInvoker.getInstance().requestProjectSync(myProject, GradleSyncStats.Trigger.TRIGGER_MODIFIER_ACTION_UNDONE)
      }

      override fun redo() {
        GradleSyncInvoker.getInstance().requestProjectSync(myProject, GradleSyncStats.Trigger.TRIGGER_MODIFIER_ACTION_REDONE)
      }
    })
  }

  override fun getCommandName(): String = AndroidBundle.message("android.refactoring.migrateto.defaulttargetsdktocompilesdkifunset.title")

  abstract class MigratePropertiesUsageInfo(element: PsiElement): UsageInfo(element) {
    abstract fun doIt()
  }

  class MigrateUsageInfo(
    element: PsiElement,
    val buildModel: ProjectBuildModel,
    val module: Module,
    val minSdk: AndroidVersion
  ) : MigratePropertiesUsageInfo(element) {
    override fun doIt() {
      buildModel.getModuleBuildModel(module)?.android()?.defaultConfig()?.targetSdkVersion()?.setValue(minSdk.apiStringWithExtension)
    }
  }
}