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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.GradleVersion
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.LIST_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.OBJECT_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.REFERENCE_TO_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.api.util.DeletablePsiElementHolder
import com.android.tools.idea.gradle.dsl.parser.semantics.AndroidGradlePluginVersion
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.*
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.Companion.standardRegionNecessity
import com.android.tools.idea.stats.withProjectId
import com.android.tools.idea.util.toIoFile
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory.PROJECT_SYSTEM
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.UPGRADE_ASSISTANT_COMPONENT_EVENT
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.UPGRADE_ASSISTANT_PROCESSOR_EVENT
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_AGP_VERSION_UPDATED
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_MODIFIER_ACTION_REDONE
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_MODIFIER_ACTION_UNDONE
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentEvent
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo.UpgradeAssistantEventKind
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo.UpgradeAssistantEventKind.EXECUTE
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo.UpgradeAssistantEventKind.FIND_USAGES
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo.UpgradeAssistantEventKind.PREVIEW_REFACTORING
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo.UpgradeAssistantEventKind.SYNC_FAILED
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo.UpgradeAssistantEventKind.SYNC_SKIPPED
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo.UpgradeAssistantEventKind.SYNC_SUCCEEDED
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo.UpgradeAssistantEventKind.BLOCKED
import com.google.wireless.android.sdk.stats.UpgradeAssistantProcessorEvent
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.notification.NotificationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Factory
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtil.pluralize
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usageView.UsageViewUtil
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageInfoSearcherAdapter
import com.intellij.usages.UsageSearcher
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageView
import com.intellij.usages.UsageViewManager
import com.intellij.usages.UsageViewPresentation
import com.intellij.usages.impl.UsageViewImpl
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.util.Processor
import com.intellij.util.ThreeState
import com.intellij.util.containers.toArray
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet
import org.jetbrains.android.util.AndroidBundle
import java.awt.event.ActionEvent
import java.io.File
import java.util.UUID
import java.util.function.Supplier
import javax.swing.AbstractAction
import javax.swing.Action

internal const val LOG_CATEGORY = "Upgrade Assistant"
private val LOG = Logger.getInstance(LOG_CATEGORY)

abstract class GradleBuildModelRefactoringProcessor : BaseRefactoringProcessor {
  constructor(project: Project) : super(project) {
    this.project = project
    this.projectBuildModel = ProjectBuildModel.get(project)
  }
  constructor(processor: GradleBuildModelRefactoringProcessor): super(processor.project) {
    this.project = processor.project
    this.projectBuildModel = processor.projectBuildModel
  }

  val project: Project
  val projectBuildModel: ProjectBuildModel

  val otherAffectedFiles = mutableSetOf<PsiFile>()
  val psiSpoilingUsageInfos = mutableListOf<UsageInfo>()

  var foundUsages: Boolean = false

  override fun performRefactoring(usages: Array<out UsageInfo>) {
    val size = usages.size
    LOG.info("performing refactoring \"${this.commandName}\" with $size ${pluralize("usage", size)}")
    usages.forEach {
      if (it is GradleBuildModelUsageInfo) {
        it.performRefactoringFor(this)
        otherAffectedFiles.addAll(it.otherAffectedFiles)
      }
    }
  }

  override fun performPsiSpoilingRefactoring() {
    LOG.info("applying changes from \"${this.commandName}\" refactoring to build model")
    projectBuildModel.applyChanges()

    if (otherAffectedFiles.isNotEmpty()) {
      val documentManager = PsiDocumentManager.getInstance(project)
      otherAffectedFiles.forEach psiFile@{ psiFile ->
        val document = documentManager.getDocument(psiFile) ?: return@psiFile
        if (documentManager.isDocumentBlockedByPsi(document)) {
          documentManager.doPostponedOperationsAndUnblockDocument(document)
        }
        FileDocumentManager.getInstance().saveDocument(document)
        if (!documentManager.isCommitted(document)) {
          documentManager.commitDocument(document)
        }
      }
    }

    if (psiSpoilingUsageInfos.isNotEmpty()) {
      projectBuildModel.reparse()
    }

    psiSpoilingUsageInfos.forEach {
      if (it is SpoilingGradleBuildModelUsageInfo)
        it.performPsiSpoilingRefactoringFor(this)
    }

    super.performPsiSpoilingRefactoring()
  }
}

/**
 * Instances of [GradleBuildModelUsageInfo] should perform their refactor through the buildModel, and must not
 * invalidate either the BuildModel or the underlying Psi in their [performBuildModelRefactoring] method.  Any spoiling
 * should be done using [SpoilingGradleBuildModelUsageInfo] instances.
 */
abstract class GradleBuildModelUsageInfo(element: WrappedPsiElement) : UsageInfo(element) {
  fun performRefactoringFor(processor: GradleBuildModelRefactoringProcessor) {
    logBuildModelRefactoring()
    performBuildModelRefactoring(processor)
  }

  abstract fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor)

  private fun logBuildModelRefactoring() {
    val path = when (val basePath = project.basePath) {
      null -> this.virtualFile?.name
      else -> this.virtualFile?.toIoFile()?.toRelativeString(File(basePath)) ?: this.virtualFile?.name
    }
    LOG.info("performing \"${this.tooltipText}\" build model refactoring in '${path}'")
  }

  abstract override fun getTooltipText(): String

  /**
   * Fundamentally, implementations of [GradleBuildModelUsageInfo] are data classes, in that we expect never to mutate
   * them, and their contents and class identity encode their semantics.  Unfortunately, there's a slight mismatch; the
   * equality semantics of the UsageInfo PsiElement are not straightforward (they are considered equal if they point to
   * the same range, even if they're not the identical element), but since the [UsageInfo] superclass constructor requires
   * a PsiElement, we must have a PsiElement in the primary constructor, so the automatically-generated methods from a
   * data class will not do the right thing.
   *
   * Instead, we simulate the parts of a data class we need here; by having a function which subclasses must implement, and
   * final implementations of equals() and hashCode() which use that function.  The default implementation here encodes that
   * document range is sufficient to discriminate between instances, which in practice will be true for replacements and
   * deletions but will not be in general for additions.
   */
  open fun getDiscriminatingValues(): List<Any> = listOf()

  /**
   * Most actions taken by a [GradleBuildModelUsageInfo] in its [performBuildModelRefactoring] method affect project files through the
   * build model, which takes responsibility for making sure that documents are saved and psi is not blocking.  Some actions affect
   * files outside that model, however, and any such actions are responsible for adding those files to this set so that they can be
   * accumulated for handling alongside the build model files.
   */
  val otherAffectedFiles = mutableSetOf<PsiFile>()

  final override fun equals(other: Any?) = super.equals(other) && when(other) {
    is GradleBuildModelUsageInfo -> getDiscriminatingValues() == other.getDiscriminatingValues()
    else -> false
  }

  final override fun hashCode() = super.hashCode() xor getDiscriminatingValues().hashCode()
}

/**
 * Instances of [SpoilingGradleBuildModelUsageInfo] should perform any build model refactoring in their extension of
 * [performBuildModelRefactoring], which must call this class's method; they may then perform Psi-spoiling refactoring in their
 * [performPsiSpoilingBuildModelRefactoring] method in any way they desire, operating after changes to the buildModel have been applied
 * and reparsed.
 */
abstract class SpoilingGradleBuildModelUsageInfo(
  element: WrappedPsiElement
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    noteForPsiSpoilingBuildModelRefactoring(processor)
  }

  fun performPsiSpoilingRefactoringFor(processor: GradleBuildModelRefactoringProcessor) {
    LOG.info("performing \"${this.tooltipText}\" Psi-spoiling refactoring")
    performPsiSpoilingBuildModelRefactoring(processor)
  }

  abstract fun performPsiSpoilingBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor)

  private fun noteForPsiSpoilingBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    LOG.info("adding usage \"${this.tooltipText}\" to psiSpoilingUsageInfos")
    processor.psiSpoilingUsageInfos.add(this)
  }
}

class AgpUpgradeRefactoringProcessor(
  project: Project,
  val current: GradleVersion,
  val new: GradleVersion
) : GradleBuildModelRefactoringProcessor(project) {

  val uuid = UUID.randomUUID().toString()
  val agpVersionRefactoringProcessor by lazy { componentRefactoringProcessors.firstNotNullOf { it as? AgpVersionRefactoringProcessor } }
  val componentRefactoringProcessorsWithAgpVersionProcessorLast
    get() = componentRefactoringProcessors.sortedBy { if (it is AgpVersionRefactoringProcessor) 1 else 0 }
  val componentRefactoringProcessors = listOf(
    AgpVersionRefactoringProcessor(this),
    GMavenRepositoryRefactoringProcessor(this),
    GradleVersionRefactoringProcessor(this),
    GradlePluginsRefactoringProcessor(this),
    Java8DefaultRefactoringProcessor(this),
    CompileRuntimeConfigurationRefactoringProcessor(this),
    FabricCrashlyticsRefactoringProcessor(this),
    RedundantPropertiesRefactoringProcessor(this),
    AndroidManifestPackageToNamespaceRefactoringProcessor(this),
    R8FullModeDefaultRefactoringProcessor(this),
    RenderScriptDefaultRefactoringProcessor(this),
    NonTransitiveRClassDefaultRefactoringProcessor(this),
    REMOVE_SOURCE_SET_JNI_INFO.RefactoringProcessor(this),
    MIGRATE_AAPT_OPTIONS_TO_ANDROID_RESOURCES.RefactoringProcessor(this),
    REMOVE_BUILD_TYPE_USE_PROGUARD_INFO.RefactoringProcessor(this),
    RemoveImplementationPropertiesRefactoringProcessor(this),
    MIGRATE_ADB_OPTIONS_TO_INSTALLATION.RefactoringProcessor(this),
    MIGRATE_FAILURE_RETENTION_TO_EMULATOR_SNAPSHOTS.RefactoringProcessor(this),
    MIGRATE_JACOCO_TO_TEST_COVERAGE.RefactoringProcessor(this),
    MigratePackagingOptionsToJniLibsAndResourcesRefactoringProcessor(this),
    MIGRATE_LINT_OPTIONS_TO_LINT.RefactoringProcessor(this),
    REWRITE_DEPRECATED_OPERATORS.RefactoringProcessor(this),
  )

  val targets = mutableListOf<PsiElement>()
  var usages: Array<UsageInfo> = listOf<UsageInfo>().toTypedArray()
  var executedUsages: Array<out UsageInfo> = listOf<UsageInfo>().toTypedArray()

  final fun blockProcessorExecution() =
    componentRefactoringProcessors.any { it.isEnabled && it.isBlocked }

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return targets.toArray(PsiElement.EMPTY_ARRAY)
      }

      override fun getProcessedElementsHeader() = AndroidBundle.message("project.upgrade.agpUpgradeRefactoringProcessor.usageView.header")

      /** see [ComponentGroupingRuleProvider] for an explanation of this override */
      override fun getCodeReferencesText(usagesCount: Int, filesCount: Int) = AndroidBundle.message("project.upgrade.references.text")
    }
  }

  override fun findUsages(): Array<UsageInfo> {
    val usages = doFindUsages()
    trackProcessorUsage(FIND_USAGES, usages.size, projectBuildModel.context.allRequestedFiles.size)
    return usages
  }

  @VisibleForTesting
  fun doFindUsages(): Array<UsageInfo> {
    projectBuildModel.reparse()
    val usages = ArrayList<UsageInfo>()

    componentRefactoringProcessors.forEach { processor ->
      usages.addAll(processor.findUsages())
    }
    targets.clear()
    projectBuildModel.projectBuildModel?.let {
      targets.add(object : FakePsiElement() {
        override fun getParent() = it.psiElement
        override fun canNavigate() = false
        override fun getContainingFile() = it.psiFile
        override fun getName() = "Upgrading Project Build Configuration"
      })
    }

    foundUsages = usages.size > 0
    this.usages = usages.toTypedArray()
    return this.usages
  }

  override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
    val filtered = refUsages.get().filter {
      when (it) {
        is KotlinLanguageLevelUsageInfo, is JavaLanguageLevelUsageInfo ->
          (it.element as? WrappedPsiElement)?.usageType == Java8DefaultRefactoringProcessor.INSERT_OLD_USAGE_TYPE
        is R8FullModeUsageInfo ->
          (it.element as? WrappedPsiElement)?.usageType == R8FullModeDefaultRefactoringProcessor.INSERT_OLD_USAGE_TYPE
        else -> true
      }
    }
    refUsages.set(filtered.toTypedArray())
    prepareSuccessful()
    return true
  }

  private fun ensureElementsWritable(usages: Array<out UsageInfo>, viewDescriptor: UsageViewDescriptor): Boolean {
    fun ensureFilesWritable(project: Project, elements: Collection<PsiElement>): Boolean {
      val psiElements = PsiUtilCore.toPsiElementArray(elements)
      return CommonRefactoringUtil.checkReadOnlyStatus(project, *psiElements)
    }

    val elements: MutableSet<PsiElement> = ReferenceOpenHashSet()  // protect against poorly implemented equality

    for (usage in usages) {
      @Suppress("SENSELESS_COMPARISON") // preserve correspondence with BaseRefactoringProcessor
      assert(usage != null) { "Found null element in usages array" }
      if (skipNonCodeUsages() && usage.isNonCodeUsage()) continue
      val element = usage.element
      if (element != null) elements.add(element)
    }
    elements.addAll(getElementsToWrite(viewDescriptor))
    return ensureFilesWritable(project, elements)
  }

  var usageView: UsageView? = null

  private fun createPresentation(descriptor: UsageViewDescriptor, usages: Array<Usage>): UsageViewPresentation {
    val presentation = UsageViewPresentation()
    presentation.tabText = AndroidBundle.message("project.upgrade.usageView.tabText")
    presentation.targetsNodeText = descriptor.processedElementsHeader
    presentation.isShowReadOnlyStatusAsRed = true
    presentation.isShowCancelButton = true
    presentation.searchString = RefactoringBundle.message("usageView.usagesText")
    var codeUsageCount = 0
    var nonCodeUsageCount = 0
    var dynamicUsagesCount = 0
    val codeFiles: MutableSet<PsiFile?> = HashSet()
    val nonCodeFiles: MutableSet<PsiFile?> = HashSet()
    val dynamicUsagesCodeFiles: MutableSet<PsiFile?> = HashSet()

    for (usage in usages) {
      if (usage is PsiElementUsage) {
        @Suppress("UnnecessaryVariable") val elementUsage = usage // preserve correspondence with BaseRefactoringProcessor
        val element = elementUsage.element ?: continue
        val containingFile = element.containingFile
        if (usage is UsageInfo2UsageAdapter && usage.usageInfo.isDynamicUsage) {
          dynamicUsagesCount++
          dynamicUsagesCodeFiles.add(containingFile)
        }
        else if (elementUsage.isNonCodeUsage) {
          nonCodeUsageCount++
          nonCodeFiles.add(containingFile)
        }
        else {
          codeUsageCount++
          codeFiles.add(containingFile)
        }
      }
    }
    codeFiles.remove(null)
    nonCodeFiles.remove(null)
    dynamicUsagesCodeFiles.remove(null)

    val codeReferencesText: String = descriptor.getCodeReferencesText(codeUsageCount, codeFiles.size)
    presentation.codeUsagesString = codeReferencesText
    val commentReferencesText: String? = descriptor.getCommentReferencesText(nonCodeUsageCount, nonCodeFiles.size)
    if (commentReferencesText != null) {
      presentation.nonCodeUsagesString = commentReferencesText
    }
    presentation.setDynamicUsagesString("Dynamic " + StringUtil.decapitalize(
      descriptor.getCodeReferencesText(dynamicUsagesCount, dynamicUsagesCodeFiles.size)))
    return presentation

  }

  private fun showUsageView(viewDescriptor: UsageViewDescriptor, factory: Factory<UsageSearcher>, usageInfos: Array<out UsageInfo>) {
    val viewManager = UsageViewManager.getInstance(myProject)

    val initialElements = viewDescriptor.elements
    val targets: Array<out UsageTarget> = PsiElement2UsageTargetAdapter.convert(initialElements)
      .map {
        when (val action = backFromPreviewAction) {
          null -> WrappedUsageTarget(it)
          else -> WrappedConfigurableUsageTarget(it, action)
        }
      }
      .toArray(UsageTarget.EMPTY_ARRAY)
    val convertUsagesRef = Ref<Array<Usage>>()
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
        {
          ApplicationManager.getApplication().runReadAction {
            @Suppress("UNCHECKED_CAST")
            val usages: Array<Usage> = UsageInfo2UsageAdapter.convert(usageInfos) as Array<Usage>
            convertUsagesRef.set(usages)
          }
        },
        RefactoringBundle.message("refactoring.preprocess.usages.progress"), true, myProject)) {
      return
    }

    if (convertUsagesRef.isNull) {
      return
    }

    val usages = convertUsagesRef.get()

    val presentation = createPresentation(viewDescriptor, usages)
    if (usageView == null) {
      usageView = viewManager.showUsages(targets, usages, presentation, factory).apply {
        Disposer.register(this, { usageView = null })
        customizeUsagesView(viewDescriptor, this)
      }
    }
    else {
      (usageView as? UsageViewImpl)?.run {
        removeUsagesBulk(this.usages)
        appendUsagesInBulk(listOf(*usages))
        // TODO(xof): switch to Find tab with our existing usageView in it (but it's more complicated than that
        //  because the user might have detached the usageView and it might be visible, floating in a window
        //  somewhere, or arbitrarily minimized).
      }
    }
    // TODO(xof): investigate whether UnloadedModules are a thing we support / understand
    //val unloadedModules = computeUnloadedModulesFromUseScope(viewDescriptor)
    //if (!unloadedModules.isEmpty()) {
    //  usageView?.appendUsage(UnknownUsagesInUnloadedModules(unloadedModules))
    //}
  }

  override fun previewRefactoring(usages: Array<out UsageInfo>) {
    trackProcessorUsage(PREVIEW_REFACTORING, usages.size, projectBuildModel.context.allRequestedFiles.size)
    // this would be `super.previewRefactoring(usages) except that there's no way to override the tab window title
    if (ApplicationManager.getApplication().isUnitTestMode) {
      ensureElementsWritable(usages, createUsageViewDescriptor(usages))
      execute(usages)
      return
    }
    doPreviewRefactoring(usages)
  }

  @VisibleForTesting
  fun doPreviewRefactoring(usages: Array<out UsageInfo>) {
    val viewDescriptor = createUsageViewDescriptor(usages)
    val elements = viewDescriptor.elements
    val targets = PsiElement2UsageTargetAdapter.convert(elements)
    val factory = Factory<UsageSearcher> {
      object : UsageInfoSearcherAdapter() {
        override fun generate(
          processor: Processor<in Usage?>) {
          ApplicationManager.getApplication().runReadAction {
            var i = 0
            while (i < elements.size) {
              elements[i] = targets[i].element
              i++
            }
            refreshElements(
              elements)
          }
          processUsages(
            processor,
            myProject)
        }

        override fun findUsages(): Array<UsageInfo> {
          return this@AgpUpgradeRefactoringProcessor.findUsages()
        }
      }
    }

    showUsageView(viewDescriptor, factory, usages)
  }

  var backFromPreviewAction : Action? = null
    set(value) {
      backFromPreviewAction?.let { additionalPreviewActions.remove(it) }
      field = value
      value?.let { additionalPreviewActions.add(it) }
    }
  private var additionalPreviewActions : MutableList<Action> = mutableListOf()

  // Note: this override does almost the same as the base method as of 2020-07-29, except for adding and renaming
  // some buttons.  Because of the limited support for extension, we have to reimplement most of the base method
  // in-place, which is fine until the base method changes, at which point this processor will not reflect those
  // changes.
  //
  // TODO(xof): given that in order to change the tool window tab name we have to override previewRefactoring() as well,
  //  it's possible that rather than overriding customizeUsagesView (which is only called from previewRefactoring()) we
  //  could just inline its effect.
  override fun customizeUsagesView(viewDescriptor: UsageViewDescriptor, usageView: UsageView) {
    if (!blockProcessorExecution()) {
      // if the processor is blocked, don't offer a button that executes the processor.
      val refactoringRunnable = Runnable {
        val notExcludedUsages = UsageViewUtil.getNotExcludedUsageInfos(usageView)
        val usagesToRefactor = this.usages.filter { notExcludedUsages.contains(it) } // preserve found order
        val infos = usagesToRefactor.toArray(UsageInfo.EMPTY_ARRAY)
        if (ensureElementsWritable(infos, viewDescriptor)) {
          execute(infos)
          previewExecutedCallback?.invoke()
        }
      }
      val canNotMakeString = AndroidBundle.message("project.upgrade.usageView.need.reRun")
      val label = AndroidBundle.message("project.upgrade.usageView.doAction")
      usageView.addPerformOperationAction(refactoringRunnable, commandName, canNotMakeString, label, false)
    }
    usageView.setRerunAction(object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) = doRun()
    })

    this.usageView = usageView

    additionalPreviewActions.forEach {
      usageView.addButtonToLowerPane(it)
    }
  }

  override fun execute(usages: Array<out UsageInfo>) {
    if (blockProcessorExecution()) {
      LOG.warn("${this.commandName} has blocked components")
      trackProcessorUsage(BLOCKED, usages.size, projectBuildModel.context.allRequestedFiles.size)
      return
    }
    projectBuildModel.context.agpVersion = AndroidGradlePluginVersion.tryParse(new.toString())
    trackProcessorUsage(EXECUTE, usages.size, projectBuildModel.context.allRequestedFiles.size)
    executedUsages = usages
    super.execute(usages)
  }

  var showBuildOutputOnSyncFailure = true

  var syncRequestCallback: (() -> Unit)? = null
  var previewExecutedCallback: (() -> Unit)? = null

  override fun performPsiSpoilingRefactoring() {
    super.performPsiSpoilingRefactoring()
    val executedUsagesSize = executedUsages.size
    val requestedFilesSize = projectBuildModel.context.allRequestedFiles.size
    val listener = object : GradleSyncListener {
      override fun syncSkipped(project: Project) = trackProcessorUsage(SYNC_SKIPPED, executedUsagesSize, requestedFilesSize)
      override fun syncFailed(project: Project, errorMessage: String) =
        trackProcessorUsage(SYNC_FAILED, executedUsagesSize, requestedFilesSize)
      override fun syncSucceeded(project: Project) = trackProcessorUsage(SYNC_SUCCEEDED, executedUsagesSize, requestedFilesSize)
    }
    val request = GradleSyncInvoker.Request(TRIGGER_AGP_VERSION_UPDATED, dontFocusSyncFailureOutput = !showBuildOutputOnSyncFailure)
    syncRequestCallback?.invoke()
    GradleSyncInvoker.getInstance().requestProjectSync(project, request, listener)
    UndoManager.getInstance(project).undoableActionPerformed(object : BasicUndoableAction() {
      override fun undo(): Unit =
        GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncInvoker.Request(TRIGGER_MODIFIER_ACTION_UNDONE))
      override fun redo(): Unit =
        GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncInvoker.Request(TRIGGER_MODIFIER_ACTION_REDONE))
    })
  }

  var myCommandName: String = AndroidBundle.message("project.upgrade.agpUpgradeRefactoringProcessor.commandName", current, new)

  override fun getCommandName() = myCommandName

  fun setCommandName(value: String) { myCommandName = value }

  override fun getRefactoringId(): String = "com.android.tools.agp.upgrade"

  /**
   * Parsing models is potentially expensive, so client code can call this method on a background thread before changing the modality
   * state or performing other user interface actions, which (if parsing were to happen in their scope) might block the whole UI.
   */
  fun ensureParsedModels() {
    // TODO(b/169667833): add methods that explicitly compute and cache the list or retrieve it from cache (computeAllIncluded... /
    //  retrieveAllIncluded..., maybe?) and use that here.  Deprecate the old getAllIncluded... methods).
    val progressManager = ProgressManager.getInstance()
    // Running synchronously here brings up a modal progress dialog.  On the one hand this isn't ideal because it prevents other work from
    // being done; on the other hand it is cancellable, shows numeric progress and takes around 30 seconds for a project with 1k modules.
    //
    // Moving to an asynchronous process would involve modifying callers to do the subsequent work after parsing in callbacks.
    progressManager.runProcessWithProgressSynchronously(
      {
        val indicator = progressManager.progressIndicator
        projectBuildModel.getAllIncludedBuildModels { seen, total ->
          indicator?.let {
            indicator.checkCanceled()
            // both "Parsing file ..." and "Parsing module ..." here are in general slightly wrong (given included and settings files).
            indicator.text = "Parsing file $seen${if (total != null) " of $total" else ""}"
            indicator.isIndeterminate = total == null
            total?.let { indicator.fraction = seen.toDouble() / total.toDouble() }
          }
        }
        // Ensure that we have the information about no-ops, which might also involve inspecting Psi directly (and thus should not be
        // done on the EDT).
        componentRefactoringProcessors.forEach { it.initializeComponentCaches() }
      },
      commandName, true, project)
  }
}

internal fun notifyCancelledUpgrade(project: Project, processor: AgpUpgradeRefactoringProcessor) {
  val current = processor.current
  val new = processor.new
  val listener = NotificationListener { notification, _ ->
    notification.expire()
    ApplicationManager.getApplication().executeOnPooledThread {
      showAndInvokeAgpUpgradeRefactoringProcessor(project, current, new)
    }
  }
  val notification = ProjectUpgradeNotification(
    AndroidBundle.message("project.upgrade.notifyCancelledUpgrade.title"),
    AndroidBundle.message("project.upgrade.notifyCancelledUpgrade.body"),
    listener)
  notification.notify(project)
}

/**
 * This function is a default entry point to the AGP Upgrade Assistant, responsible for showing suitable UI for gathering user input
 * to the process, and then running the processor under that user input's direction.
 */
fun showAndInvokeAgpUpgradeRefactoringProcessor(project: Project, current: GradleVersion, new: GradleVersion) {
  DumbService.getInstance(project).smartInvokeLater {
    val contentManager = project.getService(ContentManager::class.java)
    contentManager.showContent(new)
  }
}

/**
One common way to characterise a compatibility change is that some old feature f_o is deprecated in favour of some new feature f_n from
version v_n (when the new feature f_n is available); the old feature f_o is finally removed in version v_o.  That is, feature f_n is
available in the versions [v_n, +∞), and f_o is available in versions (-∞, v_o) -- note the exclusion of v_o, which is the first version
in which f_o is *not* available.  For the rest of this analysis to hold, we also assume that v_n <= v_o -- that is, there is a set
of versions where the features overlap, or else a feature is replaced wholesale in a single version, but that there is no period where
neither of the features is present.

If we can characterise the upgrade from a (cur, new > cur) pair of AGP versions, a compatibility change (implemented by a single
component refactoring) can be put into one of six categories:

| 1 | 2 | 3 | 4 | Necessity
|---|---|---|---|----------
|v_n|v_o|cur|new| [IRRELEVANT_PAST]
|cur|new|v_n|v_o| [IRRELEVANT_FUTURE]
|cur|v_n|v_o|new| [MANDATORY_CODEPENDENT] (must do the refactoring in the same action as the AGP version upgrade)
|v_n|cur|v_o|new| [MANDATORY_INDEPENDENT] (must do the refactoring, but can do it before the AGP version upgrade)
|cur|v_n|new|v_o| [OPTIONAL_CODEPENDENT] (need not do the refactoring, but if done must be with or after the AGP version upgrade)
|v_n|cur|new|v_o| [OPTIONAL_INDEPENDENT] (need not do the refactoring, but if done can be at any point in the process)

with the conventions for v_n and v_o as described above, equality in version numbers (e.g. if we are upgrading to the first version
where a feature appears or disappears) is handled by v_n/v_o sorting before cur/new -- so that when comparing a feature version against
an version associated with an AGP dependency, we must use the < or >= operators depending on whether the feature version is on the left
or right of the operator respectively.

For the possibly-simpler case where we have a discontinuity in behaviour, v_o = v_n = vvv, and the three possible cases are:

| 1 | 2 | 3 | Necessity
+---+---+---+----------
|vvv|cur|new| [IRRELEVANT_PAST]
|cur|vvv|new| [MANDATORY_CODEPENDENT]
|cur|new|vvv| [IRRELEVANT_FUTURE]

(again in case of equality, vvv sorts before cur and new)

If other refactorings come along which are more complicated than can be supported by this model of a single feature replaced by another,
we might need more necessity values.
*/
enum class AgpUpgradeComponentNecessity {
  IRRELEVANT_PAST,
  IRRELEVANT_FUTURE,
  MANDATORY_CODEPENDENT,
  MANDATORY_INDEPENDENT,
  OPTIONAL_CODEPENDENT,
  OPTIONAL_INDEPENDENT,

  ;

  companion object {
    fun standardPointNecessity(current: GradleVersion, new: GradleVersion, change: GradleVersion) = when {
      current > new -> throw IllegalArgumentException("inconsistency: current ($current) > new ($new)")
      current >= change && new >= change -> IRRELEVANT_PAST
      current < change && new >= change -> MANDATORY_CODEPENDENT
      current < change && new < change -> IRRELEVANT_FUTURE
      else -> throw RuntimeException("cannot happen")
    }

    /** [replacementAvailable] must be less than [originalRemoved]. */
    fun standardRegionNecessity(
      current: GradleVersion,
      new: GradleVersion,
      replacementAvailable: GradleVersion,
      originalRemoved: GradleVersion
    ): AgpUpgradeComponentNecessity {
      return when {
        current > new -> throw IllegalArgumentException("inconsistency: current ($current) > new ($new)")
        replacementAvailable > originalRemoved ->
          throw IllegalArgumentException("internal error: replacementAvailable ($replacementAvailable) > originalRemoved ($originalRemoved")
        current >= originalRemoved && new >= originalRemoved -> IRRELEVANT_PAST
        current < replacementAvailable && new < replacementAvailable -> IRRELEVANT_FUTURE
        current < replacementAvailable && new >= originalRemoved -> MANDATORY_CODEPENDENT
        current < originalRemoved && new >= originalRemoved -> MANDATORY_INDEPENDENT
        current < replacementAvailable && new >= replacementAvailable -> OPTIONAL_CODEPENDENT
        current >= replacementAvailable && new < originalRemoved -> OPTIONAL_INDEPENDENT
        else -> throw RuntimeException("cannot happen")
      }
    }
  }
}

// Each individual refactoring involved in an AGP Upgrade is implemented as its own refactoring processor.  For a "batch" upgrade, most
// of the functionality of a refactoring processor is handled by an outer (master) RefactoringProcessor, which delegates to sub-processors
// for findUsages (and implicitly for performing the refactoring, implemented as methods on the UsageInfos).  However, there may be
// a need for chained upgrades in the future, where each individual refactoring processor would run independently.
abstract class AgpUpgradeComponentRefactoringProcessor: GradleBuildModelRefactoringProcessor {
  val current: GradleVersion
  val new: GradleVersion
  val uuid: String
  val hasParentProcessor: Boolean
  private var _isEnabled: Boolean? = null
  var isEnabled: Boolean
    set(value) {
      LOG.info("setting isEnabled for \"${this.commandName}\" refactoring to $value")
      _isEnabled = value
    }
    get() {
      if (_isEnabled == null) {
        LOG.info("initializing isEnabled for \"${this.commandName}\" refactoring from ${necessity()}")
        _isEnabled = when (necessity()) {
          IRRELEVANT_FUTURE, IRRELEVANT_PAST -> false
          MANDATORY_CODEPENDENT, MANDATORY_INDEPENDENT, OPTIONAL_CODEPENDENT, OPTIONAL_INDEPENDENT -> true
        }
      }
      return _isEnabled!!
    }

  private var _isAlwaysNoOpForProject: Boolean? = null
  var isAlwaysNoOpForProject: Boolean
    @VisibleForTesting // only exists for testing
    set(value) {
      _isAlwaysNoOpForProject = value
    }
    get() {
      if (_isAlwaysNoOpForProject == null) {
        _isAlwaysNoOpForProject = runReadAction { computeIsAlwaysNoOpForProject() }
      }
      return _isAlwaysNoOpForProject!!
    }

  internal fun initializeComponentCaches() {
    runReadAction {
      _isAlwaysNoOpForProject = computeIsAlwaysNoOpForProject()
      _cachedUsages = findComponentUsages().toList()
      initializeComponentExtraCaches()
    }
  }

  /**
   * This function should be overridden by individual component processors in order to compute (within a read action) any non-volatile
   * state relating to their operation.
   */
  open fun initializeComponentExtraCaches() = Unit

  sealed class BlockReason(
    val shortDescription: String,
    val description: String? = null,
    val helpLinkUrl: String? = null
  )

  open fun blockProcessorReasons(): List<BlockReason> = listOf()

  val isBlocked: Boolean
    get() = blockProcessorReasons().isNotEmpty()

  constructor(project: Project, current: GradleVersion, new: GradleVersion): super(project) {
    this.current = current
    this.new = new
    this.uuid = UUID.randomUUID().toString()
    this.hasParentProcessor = false
  }

  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor) {
    this.current = processor.current
    this.new = processor.new
    this.uuid = processor.uuid
    this.hasParentProcessor = true
  }

  abstract fun necessity(): AgpUpgradeComponentNecessity

  private var _cachedUsages = listOf<UsageInfo>()
  internal val cachedUsages
    get() = _cachedUsages

  public final override fun findUsages(): Array<out UsageInfo> {
    if (!hasParentProcessor) {
      projectBuildModel.reparse()
    }
    if (!isEnabled) {
      trackComponentUsage(FIND_USAGES, 0, projectBuildModel.context.allRequestedFiles.size)
      LOG.info("\"${this.commandName}\" refactoring is disabled")
      return UsageInfo.EMPTY_ARRAY
    }
    val usages = findComponentUsages()
    _cachedUsages = usages.toList()
    val size = usages.size
    trackComponentUsage(FIND_USAGES, size, projectBuildModel.context.allRequestedFiles.size)
    LOG.info("found $size ${pluralize("usage", size)} for \"${this.commandName}\" refactoring")
    foundUsages = usages.isNotEmpty()
    return usages
  }

  protected abstract fun findComponentUsages(): Array<out UsageInfo>

  override fun previewRefactoring(usages: Array<out UsageInfo>) {
    // TODO(b/231690925): what should we do if the component is blocked?
    trackComponentUsage(PREVIEW_REFACTORING, usages.size, projectBuildModel.context.allRequestedFiles.size)
    super.previewRefactoring(usages)
  }

  override fun execute(usages: Array<out UsageInfo>) {
    projectBuildModel.context.agpVersion = AndroidGradlePluginVersion.tryParse(new.toString())
    if (isBlocked) {
      LOG.warn("${this.commandName} refactoring is blocked")
      trackComponentUsage(BLOCKED, usages.size, projectBuildModel.context.allRequestedFiles.size)
      return
    }
    trackComponentUsage(EXECUTE, usages.size, projectBuildModel.context.allRequestedFiles.size)
    super.execute(usages)
  }

  public abstract override fun getCommandName(): String

  open val groupingName
    get() = commandName

  open fun getReadMoreUrl(): String? = null

  open fun getShortDescription(): String? = null

  /**
   * Return whether this refactoring processor is known to perform no changes to the project, no matter what the settings
   * of the processor are; a return value of false may nevertheless lead to no changes, but true must never be returned
   * if the processor does in fact make changes.  The default method checks whether the processor finds any usages, returning
   * true if not and false otherwise; component processors may override or extend.
   */
  protected open fun computeIsAlwaysNoOpForProject(): Boolean = findComponentUsages().isEmpty()

  fun getComponentInfo(): UpgradeAssistantComponentInfo.Builder =
    completeComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setIsEnabled(isEnabled))

  abstract fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder
}

interface PropertiesOperationInfo {
  fun findBuildModelUsages(processor: AgpUpgradeComponentRefactoringProcessor, buildModel: GradleBuildModel): ArrayList<UsageInfo>
}

data class PropertiesOperationsRefactoringInfo(
  val optionalFromVersion: GradleVersion,
  val requiredFromVersion: GradleVersion,
  val commandNameSupplier: Supplier<String>,
  val shortDescriptionSupplier: Supplier<String>,
  val processedElementsHeaderSupplier: Supplier<String>,
  val componentKind: UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind,
  val propertiesOperationInfos: List<PropertiesOperationInfo>
) {

  inner class RefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {
    constructor(project: Project, current: GradleVersion, new: GradleVersion) : super(project, current, new)
    constructor(processor: AgpUpgradeRefactoringProcessor) : super(processor)

    override fun necessity() = standardRegionNecessity(current, new, optionalFromVersion, requiredFromVersion)

    override fun getCommandName(): String = commandNameSupplier.get()

    override fun getShortDescription(): String? = shortDescriptionSupplier.get()

    override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
      builder.setKind(componentKind)

    override fun findComponentUsages(): Array<out UsageInfo> {
      val usages = ArrayList<UsageInfo>()
      projectBuildModel.allIncludedBuildModels.forEach buildModel@{ buildModel ->
        propertiesOperationInfos.forEach propertyInfo@{ propertyInfo ->
          usages.addAll(propertyInfo.findBuildModelUsages(this, buildModel))
        }
      }
      return usages.toArray(UsageInfo.EMPTY_ARRAY)
    }

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
      return object : UsageViewDescriptorAdapter() {
        override fun getElements(): Array<PsiElement> {
          return PsiElement.EMPTY_ARRAY
        }

        override fun getProcessedElementsHeader(): String = processedElementsHeaderSupplier.get()
      }
    }

    val info = this@PropertiesOperationsRefactoringInfo
  }
}

data class MovePropertiesInfo(
  val sourceToDestinationPropertyModelGetters: List<
    Pair<GradleBuildModel.() -> ResolvedPropertyModel, GradleBuildModel.() -> ResolvedPropertyModel>>,
  val tooltipTextSupplier: Supplier<String>,
  val usageType: UsageType,
): PropertiesOperationInfo {

  override fun findBuildModelUsages(
    processor: AgpUpgradeComponentRefactoringProcessor,
    buildModel: GradleBuildModel
  ): ArrayList<UsageInfo> {
    val usages = ArrayList<UsageInfo>()
    sourceToDestinationPropertyModelGetters.forEach { (sourceGetter, destinationGetter) ->
      val sourceModel = buildModel.(sourceGetter)()
      if (sourceModel.getValue(OBJECT_TYPE) != null) {
        val psiElement = sourceModel.psiElement ?: return@forEach
        val wrappedPsiElement = WrappedPsiElement(psiElement, processor, usageType)
        val usageInfo = this.MovePropertyUsageInfo(wrappedPsiElement, sourceModel, buildModel, destinationGetter)
        usages.add(usageInfo)
      }
    }
    return usages
  }

  inner class MovePropertyUsageInfo(
    element: WrappedPsiElement,
    val sourcePropertyModel: ResolvedPropertyModel,
    val buildModel: GradleBuildModel,
    val destinationPropertyModelGetter: GradleBuildModel.() -> ResolvedPropertyModel,
  ) : GradleBuildModelUsageInfo(element) {
    override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
      val valueModel = sourcePropertyModel.unresolvedModel

      val value: Any = when (valueModel.valueType) {
        GradlePropertyModel.ValueType.LIST -> valueModel.getValue(LIST_TYPE) ?: return
        GradlePropertyModel.ValueType.REFERENCE -> valueModel.getValue(REFERENCE_TO_TYPE) ?: return
        else -> valueModel.getValue(OBJECT_TYPE) ?: return
      }

      buildModel.(destinationPropertyModelGetter)().setValue(value)
      sourcePropertyModel.delete()
    }

    override fun getTooltipText(): String = tooltipTextSupplier.get()

    override fun getDiscriminatingValues(): List<Any> = listOf(this@MovePropertiesInfo)
  }
}

data class RemovePropertiesInfo(
  val propertyModelListGetter: GradleBuildModel.() -> List<DeletablePsiElementHolder>,
  val tooltipTextSupplier: Supplier<String>,
  val usageType: UsageType
): PropertiesOperationInfo {

  override fun findBuildModelUsages(
    processor: AgpUpgradeComponentRefactoringProcessor,
    buildModel: GradleBuildModel
  ): ArrayList<UsageInfo> {
    val usages = ArrayList<UsageInfo>()
    buildModel.(propertyModelListGetter)().forEach { model ->
      val psiElement = model.representativeContainedPsiElement ?: return@forEach
      val wrappedPsiElement = WrappedPsiElement(psiElement, processor, usageType)
      val usageInfo = this.RemovePropertyUsageInfo(wrappedPsiElement, model)
      usages.add(usageInfo)
    }
    return usages
  }

  inner class RemovePropertyUsageInfo(
    element: WrappedPsiElement,
    val model: DeletablePsiElementHolder
  ) : GradleBuildModelUsageInfo(element) {

    override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
      model.delete()
    }

    override fun getTooltipText(): String = tooltipTextSupplier.get()

    override fun getDiscriminatingValues(): List<Any> = listOf(this@RemovePropertiesInfo)
  }
}

data class RewriteObsoletePropertiesInfo(
  val propertyModelListGetter: GradleBuildModel.() -> List<ResolvedPropertyModel>,
  val tooltipTextSupplier: Supplier<String>,
  val usageType: UsageType,
): PropertiesOperationInfo {

  override fun findBuildModelUsages(
    processor: AgpUpgradeComponentRefactoringProcessor,
    buildModel: GradleBuildModel
  ): ArrayList<UsageInfo> {
    val usages = ArrayList<UsageInfo>()
    buildModel.(propertyModelListGetter)().forEach property@{ property ->
      val ok = property.rawElement?.modelEffect?.versionConstraint?.isOkWith(null) ?: return@property
      if (!ok) {
        val psiElement = property.psiElement ?: return@property
        val wrappedPsiElement = WrappedPsiElement(psiElement, processor, usageType)
        val usageInfo = RewritePropertyUsageInfo(wrappedPsiElement, property)
        usages.add(usageInfo)
      }
    }
    return usages
  }

  inner class RewritePropertyUsageInfo(
    element: WrappedPsiElement,
    val model: ResolvedPropertyModel,
  ): GradleBuildModelUsageInfo(element) {

    override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
      model.rewrite()
    }

    override fun getTooltipText(): String = tooltipTextSupplier.get()

    override fun getDiscriminatingValues(): List<Any> = listOf(this@RewriteObsoletePropertiesInfo)
  }
}
/**
 * Usage Types for usages coming from [AgpUpgradeComponentRefactoringProcessor]s.
 *
 * This usage type provider will only provide a usage type if the element in question is a [WrappedPsiElement], which is not
 * intended for use outside this package; it will return null in all other cases.  The [UsageType] it returns should give
 * a high-level description of the effect the refactoring will have on this usage.
 */
class AgpComponentUsageTypeProvider : UsageTypeProvider {
  override fun getUsageType(element: PsiElement): UsageType? = (element as? WrappedPsiElement)?.usageType
}

internal fun isUpdatablePluginDependency(toVersion: GradleVersion, model: ArtifactDependencyModel): ThreeState {
  val artifactId = model.name().forceString()
  val groupId = model.group().toString()
  if (!AndroidPluginInfo.isAndroidPlugin(artifactId, groupId)) {
    return ThreeState.UNSURE
  }
  val versionValue = model.version().toString()
  return if (StringUtil.isEmpty(versionValue) || toVersion.compareTo(versionValue) != 0) ThreeState.YES else ThreeState.NO
}

internal fun isUpdatablePluginRelatedDependency(toVersion: GradleVersion, model: ArtifactDependencyModel): ThreeState {
  val artifactId = model.name().forceString()
  val groupId = model.group().toString()
  if (!AndroidPluginInfo.isAndroidPluginOrApi(artifactId, groupId)) {
    return ThreeState.UNSURE
  }

  val versionValue = model.version().toString()
  return if (StringUtil.isEmpty(versionValue) || toVersion.compareTo(versionValue) != 0) ThreeState.YES else ThreeState.NO
}

/**
 * Helper functions for metrics, placed out of the way of the main logic, which are responsible for building and logging
 * AndroidStudioEvent messages at various stages:
 * - of the operation of the overall processor: [AgpUpgradeRefactoringProcessor.trackProcessorUsage]
 * - of an individual component: [AgpUpgradeComponentRefactoringProcessor.trackComponentUsage].
 *
 * Currently, the difference between these messages is simply that the Processor reports on the state of all its
 * Components, while each Component reports only on itself.
 */
internal fun AgpUpgradeRefactoringProcessor.trackProcessorUsage(
  kind: UpgradeAssistantEventKind,
  usages: Int? = null,
  files: Int? = null,
) {
  val processorEvent = UpgradeAssistantProcessorEvent.newBuilder()
    .setUpgradeUuid(uuid)
    .setCurrentAgpVersion(current.toString()).setNewAgpVersion(new.toString())
    .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(kind)
                    .apply { usages?.let { setUsages(it) } }
                    .apply { files?.let { setFiles(it) } }
                    .build())
  componentRefactoringProcessors.forEach {
    processorEvent.addComponentInfo(it.getComponentInfo())
  }

  val studioEvent = AndroidStudioEvent.newBuilder()
    .setCategory(PROJECT_SYSTEM).setKind(UPGRADE_ASSISTANT_PROCESSOR_EVENT).withProjectId(project)
    .setUpgradeAssistantProcessorEvent(processorEvent.build())

  UsageTracker.log(studioEvent)
}

private fun AgpUpgradeComponentRefactoringProcessor.trackComponentUsage(
  kind: UpgradeAssistantEventKind,
  usages: Int,
  files: Int,
) {
  val componentEvent = UpgradeAssistantComponentEvent.newBuilder()
    .setUpgradeUuid(uuid)
    .setCurrentAgpVersion(current.toString()).setNewAgpVersion(new.toString())
    .setComponentInfo(getComponentInfo().build())
    .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(kind).setUsages(usages).setFiles(files).build())
    .build()
  val studioEvent = AndroidStudioEvent.newBuilder()
    .setCategory(PROJECT_SYSTEM).setKind(UPGRADE_ASSISTANT_COMPONENT_EVENT).withProjectId(project)
    .setUpgradeAssistantComponentEvent(componentEvent)

  UsageTracker.log(studioEvent)
}
