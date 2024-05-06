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
package org.jetbrains.android.refactoring

import com.android.annotations.concurrency.UiThread
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.BOOLEAN_TYPE
import com.android.tools.idea.gradle.dsl.utils.FN_GRADLE_PROPERTIES
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.project.getPackageName
import com.android.tools.idea.projectsystem.isMainModule
import com.android.tools.idea.util.androidFacet
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.lang.Language
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.psi.impl.PropertiesFileImpl
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.findFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import java.io.File
import java.util.Locale

private const val BUILDCONFIG_PROPERTY = "android.defaults.buildfeatures.buildconfig"
private val CHANGED_DEFAULT_VERSION = AgpVersion.parse("8.0.0-beta01")

private val LOG = Logger.getInstance("MigrateBuildConfigFromGradleProperties")

private fun shouldEnable(project: Project): Boolean {
  val propertiesFile = project.baseDir?.findFile(FN_GRADLE_PROPERTIES) ?: return false
  if (!propertiesFile.isValid) return false
  val version = GradleProjectSystemUtil.getAndroidGradleModelVersionInUse(project) ?: return false
  val propertiesPsi = PsiManager.getInstance(project).findFile(propertiesFile) as? PropertiesFile ?: return false
  val property = propertiesPsi.findPropertyByKey(BUILDCONFIG_PROPERTY)

  return when {
    // if the default for this AGP is true, we need to do work unless there is an explicit setting of false in gradle.properties.
    version < CHANGED_DEFAULT_VERSION -> property?.value?.lowercase(Locale.US) != "false"
    // if the default for this AGP is false, we need to do work unless the setting is not present in gradle.properties.
    version >= CHANGED_DEFAULT_VERSION -> property != null
    // Kotlin does not know that the above three clauses are exhaustive.
    else -> error("unreachable")
  }
}

class MigrateBuildConfigFromGradlePropertiesAction : AndroidGradleBaseRefactoringAction() {
  override fun isHidden() = StudioFlags.MIGRATE_BUILDCONFIG_FROM_GRADLE_PROPERTIES_REFACTORING_ENABLED.get().not()

  override fun getHandler(dataContext: DataContext) = MigrateBuildConfigFromGradlePropertiesHandler()
  override fun isAvailableInEditorOnly() = false
  override fun isAvailableForLanguage(language: Language?) = true
  override fun isEnabledOnDataContext(dataContext: DataContext) = dataContext.project?.let { shouldEnable(it) } ?: false
  override fun isEnabledOnElements(elements: Array<PsiElement>) = shouldEnable(elements.first().project)
  override fun isAvailableOnElementInEditorAndFile(element: PsiElement, editor: Editor, file: PsiFile, context: DataContext) =
    shouldEnable(element.project)
}

class MigrateBuildConfigFromGradlePropertiesHandler : RefactoringActionHandler {
  @UiThread
  override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) = invoke(project)

  @UiThread
  override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) = invoke(project)

  @UiThread
  private fun invoke(project: Project) {
    val progressManager = ProgressManager.getInstance()
    var processor: MigrateBuildConfigFromGradlePropertiesRefactoringProcessor? = null
    progressManager.runProcessWithProgressSynchronously(
      {
        val indicator = progressManager.progressIndicator
        val files = mutableListOf<File>()
        indicator?.let {
          indicator.checkCanceled()
          indicator.isIndeterminate = true
          indicator.text = "Generating sources"
        }
        GradleBuildInvoker.getInstance(project).generateSources(project.modules).get()
        project.modules.forEach module@{ module ->
          GradleAndroidModel.get(module)?.mainArtifact?.generatedSourceFolders?.let { files.addAll(it) }
        }
        indicator?.let {
          indicator.checkCanceled()
          indicator.isIndeterminate = true
          indicator.text = "Refreshing files"
        }
        VfsUtil.markDirtyAndRefresh(false, true, true, *files.toTypedArray())
        indicator?.let {
          indicator.checkCanceled()
          indicator.isIndeterminate = true
          indicator.text = "Getting Android Gradle Plugin version"
        }
        val agpVersion = GradleProjectSystemUtil.getAndroidGradleModelVersionInUse(project) ?: return@runProcessWithProgressSynchronously
        processor = MigrateBuildConfigFromGradlePropertiesRefactoringProcessor(project, agpVersion, indicator)
      }, "Migrate Build Config from Gradle Properties", true, project)
    processor?.apply {
      PsiManager.getInstance(project).dropPsiCaches()
      DumbService.getInstance(project).completeJustSubmittedTasks()
      setPreviewUsages(true)
      run()
    }
  }
}

class MigrateBuildConfigFromGradlePropertiesRefactoringProcessor(
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
  override fun getCommandName() = "Migrate buildConfig setting from gradle.properties to build Dsl"

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>) = object : UsageViewDescriptorAdapter() {
    override fun getElements() = targets.toTypedArray()
    override fun getProcessedElementsHeader() = "Migrate buildConfig setting to build Dsl"
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
        propertiesFile.findPropertyByKey(BUILDCONFIG_PROPERTY)?.psiElement?.let { targets.add(it) }
      }
    }

    myProject.modules.forEach module@{ module ->
      val facet = module.androidFacet ?: return@module
      if (!module.isMainModule()) return@module
      val generatedSourceFolders = GradleAndroidModel.get(facet)?.mainArtifact?.generatedSourceFolders ?: return@module
      if (!generatedSourceFolders.any { it.systemIndependentPath.contains("generated/source/buildConfig") }) {
        // If none of our generated source folders are for buildConfig, then the user must have turned it off here already.
        return@module
      }

      projectBuildModel.getModuleBuildModel(module)?.let { buildModel ->
        val buildConfigModel = buildModel.android().buildFeatures().buildConfig()
        val buildConfigEnabled = buildConfigModel.getValue(BOOLEAN_TYPE)
        // If we can find an explicit buildConfig directive for this module, true or false, then we don't need to do anything.
        if (buildConfigEnabled != null) return@module
        val namespace = GradleAndroidModel.get(facet)?.androidProject?.namespace ?: getPackageName(module)
        val className = "$namespace.BuildConfig"

        val psiFacade = JavaPsiFacade.getInstance(myProject)
        val buildConfigClass = psiFacade.findClass(className, module.moduleContentScope)
        if (buildConfigClass == null) {
          // We have generated sources, so if we can't find a BuildConfig class at this point something weird is going on.
          LOG.warn("BuildConfig class not found: \"$className\"")
          return@module
        }

        // Note that this only searches in the synced variant, but in principle we could have no BuildConfig usages in that variant but
        // usages elsewhere.  Oh well.  (This is consistent with how other refactoring tools work.)
        val references = ReferencesSearch.search(buildConfigClass, GlobalSearchScope.projectScope(myProject))
        if (references.findAll().isEmpty()) {
          if (buildModel.android().defaultConfig().buildConfigFields().isNotEmpty() ||
              buildModel.android().buildTypes().any { it.buildConfigFields().isNotEmpty() } ||
              buildModel.android().productFlavors().any { it.buildConfigFields().isNotEmpty() }) {
            // We have buildConfig Dsl in this module, but no use of it.  Since after the change buildConfig will be off in this module,
            // delete the buildConfigFields.
            buildModel.psiFile?.let {
              usages.add(ModuleWithoutBuildConfigButWithBuildConfigDslUsageInfo(it, projectBuildModel, module))
            }
          }
        }
        else {
          buildModel.psiFile?.let {
            // We have a use of BuildConfig in this module.  Make sure the feature is turned on explicitly in this module.
            usages.add(ModuleWithBuildConfigUsageInfo(it, projectBuildModel, module))
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

    val request = GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_REFACTOR_MIGRATE_BUILD_CONFIG_FROM_GRADLE_PROPERTIES)
    GradleSyncInvoker.getInstance().requestProjectSync(myProject, request)
    UndoManager.getInstance(myProject).undoableActionPerformed(object : BasicUndoableAction() {
      override fun undo() {
        GradleSyncInvoker.getInstance().requestProjectSync(myProject, GradleSyncInvoker.Request(
          GradleSyncStats.Trigger.TRIGGER_MODIFIER_ACTION_UNDONE))
      }
      override fun redo() {
        GradleSyncInvoker.getInstance().requestProjectSync(myProject, GradleSyncInvoker.Request(
          GradleSyncStats.Trigger.TRIGGER_MODIFIER_ACTION_REDONE))
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
        null -> element.findPropertyByKey(BUILDCONFIG_PROPERTY)?.psiElement?.delete()
        else -> element.findPropertyByKey(BUILDCONFIG_PROPERTY)?.setValue("$newSetting")
      }
    }
  }

  class NewGradlePropertiesUsageInfo(val element: PsiDirectory, val newSetting: Boolean): MigrateBuildPropertiesUsageInfo(element) {
    override fun doIt() {
      val propertiesFile = element.createFile(FN_GRADLE_PROPERTIES) as? PropertiesFileImpl
      propertiesFile?.addProperty(BUILDCONFIG_PROPERTY, "$newSetting")
    }
  }

  class ModuleWithBuildConfigUsageInfo(
    element: PsiElement,
    val buildModel: ProjectBuildModel,
    val module: Module
  ): MigrateBuildPropertiesUsageInfo(element) {
    override fun doIt() {
      buildModel.getModuleBuildModel(module)?.android()?.buildFeatures()?.buildConfig()?.setValue(true)
    }
  }

  class ModuleWithoutBuildConfigButWithBuildConfigDslUsageInfo(
    element: PsiElement,
    val buildModel: ProjectBuildModel,
    val module: Module
  ): MigrateBuildPropertiesUsageInfo(element) {
    override fun doIt() {
      buildModel.getModuleBuildModel(module)?.android()?.run {
        productFlavors().forEach { it.removeAllBuildConfigFields() }
        buildTypes().forEach { it.removeAllBuildConfigFields() }
        defaultConfig().removeAllBuildConfigFields()
      }
    }
  }
}