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

import com.android.annotations.concurrency.UiThread
import com.android.ide.common.repository.GradleVersion
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.requestProjectSync
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.stats.withProjectId
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.google.wireless.android.sdk.stats.NonTransitiveRClassMigrationEvent
import com.google.wireless.android.sdk.stats.NonTransitiveRClassMigrationEvent.NonTransitiveRClassMigrationEventKind.EXECUTE
import com.google.wireless.android.sdk.stats.NonTransitiveRClassMigrationEvent.NonTransitiveRClassMigrationEventKind.FIND_USAGES
import com.google.wireless.android.sdk.stats.NonTransitiveRClassMigrationEvent.NonTransitiveRClassMigrationEventKind.PREVIEW_REFACTORING
import com.google.wireless.android.sdk.stats.NonTransitiveRClassMigrationEvent.NonTransitiveRClassMigrationEventKind.SYNC_FAILED
import com.google.wireless.android.sdk.stats.NonTransitiveRClassMigrationEvent.NonTransitiveRClassMigrationEventKind.SYNC_SKIPPED
import com.google.wireless.android.sdk.stats.NonTransitiveRClassMigrationEvent.NonTransitiveRClassMigrationEventKind.SYNC_SUCCEEDED
import com.intellij.facet.ProjectFacetManager
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.migration.PsiMigrationManager
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.actions.BaseRefactoringAction
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usages.UsageView
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.refactoring.getProjectProperties
import org.jetbrains.android.refactoring.project
import org.jetbrains.android.refactoring.syncBeforeFinishingRefactoring
import org.jetbrains.android.util.AndroidBundle
import java.util.UUID

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
  @UiThread
  override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) = invoke(project)

  @UiThread
  override fun invoke(project: Project, elements: Array<PsiElement>, dataContext: DataContext?) = invoke(project)

  private fun invoke(project: Project) {
    val pluginInfo = AndroidPluginInfo.find(project)

    // If for some reason Android Gradle Plugin version cannot be found, assume users have the correct version for the refactoring ie. 7.0.0
    val pluginVersion = pluginInfo?.pluginVersion ?: GradleVersion(7,0,0)

    // Android Gradle Plugin version less than 4.2.0 is not supported.
    if (!pluginVersion.isAtLeast(4, 2, 0,"alpha", 0, true)) {
      Messages.showErrorDialog(
        project,
        AndroidBundle.message("android.refactoring.migrateto.nontransitiverclass.error.old.agp.message"),
        AndroidBundle.message("android.refactoring.migrateto.nontransitiverclass.error.old.agp.title")
      )
      return
    }
    val processor = MigrateToNonTransitiveRClassesProcessor.forEntireProject(project, pluginVersion)
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
  private val updateTopLevelGradleProperties: Boolean,
  private val gradleVersion: GradleVersion
) : BaseRefactoringProcessor(project) {

  val uuid = UUID.randomUUID().toString()

  companion object {
    private val LOG = Logger.getInstance(BaseRefactoringProcessor::class.java)

    fun forSingleModule(facet: AndroidFacet, gradleVersion: GradleVersion): MigrateToNonTransitiveRClassesProcessor {
      return MigrateToNonTransitiveRClassesProcessor(
        facet.module.project,
        setOf(facet),
        updateTopLevelGradleProperties = false,
        gradleVersion
      )
    }

    fun forEntireProject(project: Project, gradleVersion: GradleVersion): MigrateToNonTransitiveRClassesProcessor {
      return MigrateToNonTransitiveRClassesProcessor(
        project,
        facetsToMigrate = findFacetsToMigrate(project),
        updateTopLevelGradleProperties = true,
        gradleVersion
      )
    }
  }

  override fun getCommandName(): String = AndroidBundle.message("android.refactoring.migrateto.nontransitiverclass.title")

  public override fun findUsages(): Array<UsageInfo> {
    val progressIndicator = ProgressManager.getInstance().progressIndicator
    progressIndicator?.isIndeterminate = true
    progressIndicator?.text = AndroidBundle.message("android.refactoring.migrateto.nontransitiverclass.progress.findusages")
    val usages = facetsToMigrate.flatMap(::findUsagesOfRClassesFromModule).toMutableList()

    // TODO(b/137180850): handle the case where usages is empty better. Display gradle.properties as the only "usage", so there's something
    //   in the UI?

    progressIndicator?.text = AndroidBundle.message("android.refactoring.migrateto.nontransitiverclass.progress.inferring")
    inferPackageNames(usages, progressIndicator)

    progressIndicator?.text = null

    trackProcessorUsage(FIND_USAGES, usages.size)
    if (updateTopLevelGradleProperties) {
      val propertiesFile = myProject.getProjectProperties(createIfNotExists = false)
      if (propertiesFile != null ) {
        return usages.toTypedArray<UsageInfo>() + PropertiesUsageInfo(NON_TRANSITIVE_R_CLASSES_PROPERTY, propertiesFile.containingFile)
      }
    }
    return usages.toTypedArray()
  }

  override fun customizeUsagesView(viewDescriptor: UsageViewDescriptor, usageView: UsageView) {
    val shouldRecommendPluginUpgrade = gradleVersion.isAtLeast(4, 2, 0,"alpha", 0, true) &&
                                       !gradleVersion.isAtLeast(7, 0, 0, "alpha", 0, true)
    val hasUncommittedChanges = doesProjectHaveUncommittedChanges()
    if (hasUncommittedChanges || shouldRecommendPluginUpgrade) {
      val panel = JBPanel<JBPanel<*>>(VerticalLayout(5))
      panel.border = JBUI.Borders.empty(5)
      if (shouldRecommendPluginUpgrade) {
        panel.add(createWarningLabel(AndroidBundle.message("android.refactoring.migrateto.nontransitiverclass.warning.recommend.upgrade")))
      }
      if (hasUncommittedChanges) {
        panel.add(createWarningLabel(AndroidBundle.message("android.refactoring.migrateto.nontransitiverclass.warning.uncommitted.changes")))
      }

      usageView.setAdditionalComponent(panel)
    }
    super.customizeUsagesView(viewDescriptor, usageView)
  }

  private fun createWarningLabel(message: String): JBLabel {
    return JBLabel().apply {
      icon = StudioIcons.Common.WARNING
      text = message
    }
  }

  private fun doesProjectHaveUncommittedChanges(): Boolean {
    val changeListManager = ChangeListManager.getInstance(myProject)
    return changeListManager.areChangeListsEnabled() && changeListManager.allChanges.isNotEmpty()
  }

  override fun performRefactoring(usages: Array<out UsageInfo>) {
    trackProcessorUsage(EXECUTE, usages.size)
    val progressIndicator = ProgressManager.getInstance().progressIndicator
    progressIndicator.isIndeterminate = false
    progressIndicator.fraction = 0.0
    progressIndicator.text = AndroidBundle.message("android.refactoring.migrateto.nontransitiverclass.progress.rewriting")
    val totalUsages = usages.size.toDouble()

    val psiMigration = PsiMigrationManager.getInstance(myProject).startMigration()
    try {
      usages.forEachIndexed { index, usageInfo ->
        when (usageInfo) {
          is CodeUsageInfo -> {
            usageInfo.updateClassReference(psiMigration)
          }
        }
        progressIndicator.fraction = (index + 1) / totalUsages
      }
    } finally {
      psiMigration.finish()
    }

    if (updateTopLevelGradleProperties) {
      val propertiesFile = myProject.getProjectProperties(createIfNotExists = true)
      if (propertiesFile != null) {
        when {
          gradleVersion.isAtLeast(7, 0, 0, "alpha", 0, true) -> {
            propertiesFile.findPropertyByKey(NON_TRANSITIVE_R_CLASSES_PROPERTY)?.setValue("true") ?: propertiesFile.addProperty(
              NON_TRANSITIVE_R_CLASSES_PROPERTY, "true")
          }
          gradleVersion.isAtLeast(4, 2, 0, "alpha", 0, true) -> {
            propertiesFile.findPropertyByKey(NON_TRANSITIVE_R_CLASSES_PROPERTY)?.setValue("true") ?: propertiesFile.addProperty(
              NON_TRANSITIVE_R_CLASSES_PROPERTY, "true")
            propertiesFile.findPropertyByKey(NON_TRANSITIVE_APP_R_CLASSES_PROPERTY)?.setValue("true") ?: propertiesFile.addProperty(
              NON_TRANSITIVE_APP_R_CLASSES_PROPERTY, "true")
          }
          else -> {
            LOG.error("Gradle version too low for MigrateToNonTransitiveRClasses $gradleVersion")
          }
        }
      }

      myProject.getProjectProperties(createIfNotExists = true)?.apply {
        findPropertyByKey(NON_TRANSITIVE_R_CLASSES_PROPERTY)?.setValue("true") ?: addProperty(NON_TRANSITIVE_R_CLASSES_PROPERTY, "true")
      }
      val listener = object : GradleSyncListener {
        override fun syncSkipped(project: Project) = trackProcessorUsage(SYNC_SKIPPED)
        override fun syncFailed(project: Project, errorMessage: String) = trackProcessorUsage(SYNC_FAILED)
        override fun syncSucceeded(project: Project) = trackProcessorUsage(SYNC_SUCCEEDED)
      }
      syncBeforeFinishingRefactoring(myProject, GradleSyncStats.Trigger.TRIGGER_REFACTOR_MIGRATE_TO_RESOURCE_NAMESPACES, listener)
      UndoManager.getInstance(myProject).undoableActionPerformed(object : BasicUndoableAction() {
        override fun undo() {
          GradleSyncInvoker.getInstance().requestProjectSync(myProject, GradleSyncStats.Trigger.TRIGGER_MODIFIER_ACTION_UNDONE, listener)
        }

        override fun redo() {
          GradleSyncInvoker.getInstance().requestProjectSync(myProject, GradleSyncStats.Trigger.TRIGGER_MODIFIER_ACTION_REDONE, listener)
        }
      })
    }
  }

  override fun previewRefactoring(usages: Array<out UsageInfo>) {
    trackProcessorUsage(PREVIEW_REFACTORING, usages.size)
    super.previewRefactoring(usages)
  }
  override fun createUsageViewDescriptor(usages: Array<UsageInfo>): UsageViewDescriptor = object : UsageViewDescriptorAdapter() {
    override fun getElements(): Array<PsiElement> = PsiElement.EMPTY_ARRAY
    override fun getProcessedElementsHeader() =
      AndroidBundle.message("android.refactoring.migrateto.resourceview.header")
  }

  private fun trackProcessorUsage(kind: NonTransitiveRClassMigrationEvent.NonTransitiveRClassMigrationEventKind, usages: Int? = null) {
    val processorEvent = NonTransitiveRClassMigrationEvent.newBuilder()
      .setMigrationUuid(uuid)
      .setKind(kind)
      .apply { usages?.let { setUsages(it) } }

    val studioEvent = AndroidStudioEvent
      .newBuilder()
      .setKind(AndroidStudioEvent.EventKind.MIGRATE_TO_NON_TRANSITIVE_R_CLASS).withProjectId(myProject)
      .setNonTransitiveRClassMigrationEvent(processorEvent.build())

    UsageTracker.log(studioEvent)
  }
}
