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

import com.android.SdkConstants.GRADLE_DISTRIBUTION_URL_PROPERTY
import com.android.SdkConstants.GRADLE_MINIMUM_VERSION
import com.android.ide.common.repository.GradleVersion
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.Projects.getBaseDirPath
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.PluginModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.android.BuildTypeModel
import com.android.tools.idea.gradle.dsl.api.configurations.ConfigurationModel
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.CLASSPATH
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.BOOLEAN_TYPE
import com.android.tools.idea.gradle.dsl.api.java.LanguageLevelPropertyModel
import com.android.tools.idea.gradle.dsl.api.repositories.MavenRepositoryModel
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel
import com.android.tools.idea.gradle.dsl.parser.dependencies.FakeArtifactElement
import com.android.tools.idea.gradle.project.upgrade.AndroidPluginVersionUpdater.isUpdatablePluginDependency
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.upgrade.AgpGradleVersionRefactoringProcessor.Companion.CompatibleGradleVersion.*
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.*
import com.android.tools.idea.gradle.project.upgrade.AndroidPluginVersionUpdater.isUpdatablePluginRelatedDependency
import com.android.tools.idea.gradle.project.upgrade.Java8DefaultRefactoringProcessor.Companion.INSERT_OLD_USAGE_TYPE
import com.android.tools.idea.gradle.project.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction
import com.android.tools.idea.gradle.project.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction.ACCEPT_NEW_DEFAULT
import com.android.tools.idea.gradle.project.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction.INSERT_OLD_DEFAULT
import com.android.tools.idea.gradle.util.BuildFileProcessor
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.gradle.util.GradleWrapper
import com.android.tools.idea.stats.withProjectId
import com.android.tools.idea.util.toIoFile
import com.android.tools.idea.util.toVirtualFile
import com.android.utils.FileUtils
import com.android.utils.appendCapitalized
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory.PROJECT_SYSTEM
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.UPGRADE_ASSISTANT_COMPONENT_EVENT
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.UPGRADE_ASSISTANT_PROCESSOR_EVENT
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_AGP_VERSION_UPDATED
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentEvent
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.Java8DefaultProcessorSettings
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.AGP_CLASSPATH_DEPENDENCY
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.COMPILE_RUNTIME_CONFIGURATION
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.FABRIC_CRASHLYTICS
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.GMAVEN_REPOSITORY
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.GRADLE_VERSION
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.JAVA8_DEFAULT
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo.UpgradeAssistantEventKind
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo.UpgradeAssistantEventKind.EXECUTE
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo.UpgradeAssistantEventKind.FIND_USAGES
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo.UpgradeAssistantEventKind.PREVIEW_REFACTORING
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo.UpgradeAssistantEventKind.SYNC_FAILED
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo.UpgradeAssistantEventKind.SYNC_SKIPPED
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo.UpgradeAssistantEventKind.SYNC_SUCCEEDED
import com.google.wireless.android.sdk.stats.UpgradeAssistantProcessorEvent
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.psi.Property
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Factory
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtil.pluralize
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
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
import com.intellij.util.ThreeState.NO
import com.intellij.util.ThreeState.YES
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.toArray
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult
import org.jetbrains.kotlin.utils.ifEmpty
import java.awt.event.ActionEvent
import java.io.File
import java.util.Arrays
import java.util.HashSet
import java.util.UUID
import javax.swing.AbstractAction
import javax.swing.Action

private val LOG = Logger.getInstance("Upgrade Assistant")

abstract class GradleBuildModelRefactoringProcessor : BaseRefactoringProcessor {
  constructor(project: Project) : super(project) {
    this.project = project
    this.buildModel = ProjectBuildModel.get(project)
  }
  constructor(processor: GradleBuildModelRefactoringProcessor): super(processor.project) {
    this.project = processor.project
    this.buildModel = processor.buildModel
  }

  val project: Project
  val buildModel: ProjectBuildModel

  val psiSpoilingUsageInfos = mutableListOf<UsageInfo>()

  var foundUsages: Boolean = false

  override fun performRefactoring(usages: Array<out UsageInfo>) {
    val size = usages.size
    LOG.info("performing refactoring \"${this.commandName}\" with $size ${pluralize("usage", size)}")
    usages.forEach {
      if (it is GradleBuildModelUsageInfo) {
        it.performRefactoringFor(this)
      }
    }
  }

  override fun performPsiSpoilingRefactoring() {
    LOG.info("applying changes from \"${this.commandName}\" refactoring to build model")
    buildModel.applyChanges()

    if (psiSpoilingUsageInfos.isNotEmpty()) {
      buildModel.reparse()
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
  val classpathRefactoringProcessor = AgpClasspathDependencyRefactoringProcessor(this)
  val componentRefactoringProcessors = listOf(
    GMavenRepositoryRefactoringProcessor(this),
    AgpGradleVersionRefactoringProcessor(this),
    Java8DefaultRefactoringProcessor(this),
    CompileRuntimeConfigurationRefactoringProcessor(this),
    FabricCrashlyticsRefactoringProcessor(this)
  )

  val targets = mutableListOf<PsiElement>()

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return targets.toArray(PsiElement.EMPTY_ARRAY)
      }

      override fun getProcessedElementsHeader() = "AGP Upgrade Assistant"

      /** see [ComponentGroupingRuleProvider] for an explanation of this override */
      override fun getCodeReferencesText(usagesCount: Int, filesCount: Int): String = "References considered"
    }
  }

  override fun findUsages(): Array<UsageInfo> {
    // TODO(xof): *something* needs to ensure that the buildModel has a fresh view of the Dsl files before
    //  looking for things (particularly since findUsages can be re-run by user action) but it's not clear that
    //  this is the right thing: it is a bit expensive, and sub-processors will have to also reparse() in case
    //  they are run in isolation.  We could be correct regarding the sub-processor issue by either keeping track
    //  of which constructor was used (e.g. "do I have a parent processor?  If so, don't reparse") or by reparsing
    //  in findUsages() but calling findComponentUsages() from here.
    buildModel.reparse()
    val usages = ArrayList<UsageInfo>()

    usages.addAll(classpathRefactoringProcessor.findUsages())
    targets.ifEmpty { targets.apply { usages.firstNotNullResult { it.element }?.let { add(it) } } }

    componentRefactoringProcessors.forEach { processor ->
      usages.addAll(processor.findUsages())
    }

    foundUsages = usages.size > 0
    trackProcessorUsage(FIND_USAGES, usages.size)
    return usages.toTypedArray()
  }

  override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
    val filtered = refUsages.get().filter {
      when (it) {
        is KotlinLanguageLevelUsageInfo, is JavaLanguageLevelUsageInfo ->
          (it.element as? WrappedPsiElement)?.usageType == INSERT_OLD_USAGE_TYPE
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

    val elements: MutableSet<PsiElement> = ContainerUtil.newIdentityTroveSet() // protect against poorly implemented equality

    for (usage in usages) {
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
    // RefactoringBundle.message("usageView.tabText")
    presentation.tabText = "Upgrade Preview"
    presentation.targetsNodeText = descriptor.processedElementsHeader
    presentation.isShowReadOnlyStatusAsRed = true
    presentation.isShowCancelButton = true
    presentation.usagesString = RefactoringBundle.message("usageView.usagesText")
    var codeUsageCount = 0
    var nonCodeUsageCount = 0
    var dynamicUsagesCount = 0
    val codeFiles: MutableSet<PsiFile?> = HashSet()
    val nonCodeFiles: MutableSet<PsiFile?> = HashSet()
    val dynamicUsagesCodeFiles: MutableSet<PsiFile?> = HashSet()

    for (usage in usages) {
      if (usage is PsiElementUsage) {
        val elementUsage = usage
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
    val generatedCodeString: String
    generatedCodeString = if (codeReferencesText.contains("in code")) {
      StringUtil.replace(codeReferencesText, "in code", "in generated code")
    }
    else {
      "$codeReferencesText in generated code"
    }
    presentation.usagesInGeneratedCodeString = generatedCodeString
    return presentation

  }

  private fun showUsageView(viewDescriptor: UsageViewDescriptor, factory: Factory<UsageSearcher>, usageInfos: Array<out UsageInfo>) {
    val viewManager = UsageViewManager.getInstance(myProject)

    val initialElements = viewDescriptor.elements
    val targets: Array<out UsageTarget> = PsiElement2UsageTargetAdapter.convert(initialElements)
      .map {
        when (val action = backFromPreviewAction) {
          null -> WrappedUsageTarget(it) as UsageTarget
          else -> WrappedConfigurableUsageTarget(it, action) as UsageTarget
        }
      }
      .toArray(UsageTarget.EMPTY_ARRAY)
    val convertUsagesRef = Ref<Array<Usage>>()
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
        {
          ApplicationManager.getApplication().runReadAction {
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
      usageView = viewManager.showUsages(targets, usages, presentation, factory)
      customizeUsagesView(viewDescriptor, usageView!!)
    }
    else {
      usageView?.run { removeUsagesBulk(this.usages) }
      (usageView as UsageViewImpl).appendUsagesInBulk(Arrays.asList(*usages))
    }
    // TODO(xof): investigate whether UnloadedModules are a thing we support / understand
    //val unloadedModules = computeUnloadedModulesFromUseScope(viewDescriptor)
    //if (!unloadedModules.isEmpty()) {
    //  usageView?.appendUsage(UnknownUsagesInUnloadedModules(unloadedModules))
    //}
  }

  override fun previewRefactoring(usages: Array<out UsageInfo>) {
    trackProcessorUsage(PREVIEW_REFACTORING, usages.size)
    // this would be `super.previewRefactoring(usages) except that there's no way to override the tab window title
    if (ApplicationManager.getApplication().isUnitTestMode) {
      ensureElementsWritable(usages, createUsageViewDescriptor(usages))
      execute(usages)
      return
    }
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
    val refactoringRunnable = Runnable {
      val usagesToRefactor = UsageViewUtil.getNotExcludedUsageInfos(usageView)
      val infos = usagesToRefactor.toArray(UsageInfo.EMPTY_ARRAY)
      if (ensureElementsWritable(infos, viewDescriptor)) {
        execute(infos)
      }
    }
    // modified from RefactoringBundle.message("usageView.need.reRun")
    val canNotMakeString = """Cannot perform upgrade.
      There were changes in code after usages have been found.
      Please perform usage search again.""".trimIndent()
    val label = "Complete &Upgrade" // RefactoringBundle.message("usageView.doAction")
    usageView.addPerformOperationAction(refactoringRunnable, commandName, canNotMakeString, label, false)

    usageView.setRerunAction(object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) = doRun()
    })

    this.usageView = usageView

    additionalPreviewActions.forEach {
      usageView.addButtonToLowerPane(it)
    }
  }

  override fun execute(usages: Array<out UsageInfo>) {
    trackProcessorUsage(EXECUTE, usages.size)
    super.execute(usages)
  }

  override fun performPsiSpoilingRefactoring() {
    super.performPsiSpoilingRefactoring()
    val listener = object : GradleSyncListener {
      override fun syncSkipped(project: Project) = trackProcessorUsage(SYNC_SKIPPED)
      override fun syncFailed(project: Project, errorMessage: String) = trackProcessorUsage(SYNC_FAILED)
      override fun syncSucceeded(project: Project) = trackProcessorUsage(SYNC_SUCCEEDED)
    }
    // in AndroidRefactoringUtil this happens between performRefactoring() and performPsiSpoilingRefactoring().  Not
    // sure why.
    //
    // FIXME(b/169838158): having this here works (in that a sync is triggered at the end of the refactor) but no sync is triggered
    //  if the refactoring action is undone.
    GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncInvoker.Request(TRIGGER_AGP_VERSION_UPDATED), listener)
  }

  var myCommandName = "Upgrade AGP version from ${current} to ${new}"

  override fun getCommandName() = myCommandName

  fun setCommandName(value: String) { myCommandName = value }

  override fun getRefactoringId(): String = "com.android.tools.agp.upgrade"

  /**
   * Parsing models is potentially expensive, so client code can call this method on a background thread before changing the modality
   * state or performing other user interface actions, which (if parsing were to happen in their scope) might block the whole UI.
   */
  fun ensureParsedModels() {
    // this may look like a property access but it is in fact a call to retrieve (from cache or, if empty, by parsing) the complete
    // project Build Dsl model.
    // TODO(b/169667833): add methods that explicitly compute and cache the list or retrieve it from cache (computeAllIncluded... /
    //  retrieveAllIncluded..., maybe?) and use that here.  Deprecate the old getAllIncluded... method).
    buildModel.allIncludedBuildModels
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
}

// Each individual refactoring involved in an AGP Upgrade is implemented as its own refactoring processor.  For a "batch" upgrade, most
// of the functionality of a refactoring processor is handled by an outer (master) RefactoringProcessor, which delegates to sub-processors
// for findUsages (and implicitly for performing the refactoring, implemented as methods on the UsageInfos).  However, there may be
// a need for chained upgrades in the future, where each individual refactoring processor would run independently.
sealed class AgpUpgradeComponentRefactoringProcessor: GradleBuildModelRefactoringProcessor {
  val current: GradleVersion
  val new: GradleVersion
  val uuid: String
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
        _isAlwaysNoOpForProject = computeIsAlwaysNoOpForProject()
      }
      return _isAlwaysNoOpForProject!!
    }

  constructor(project: Project, current: GradleVersion, new: GradleVersion): super(project) {
    this.current = current
    this.new = new
    this.uuid = UUID.randomUUID().toString()
  }

  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor) {
    this.current = processor.current
    this.new = processor.new
    this.uuid = processor.uuid
  }

  abstract fun necessity(): AgpUpgradeComponentNecessity

  public final override fun findUsages(): Array<out UsageInfo> {
    if (!isEnabled) {
      trackComponentUsage(FIND_USAGES, 0)
      LOG.info("\"${this.commandName}\" refactoring is disabled")
      return UsageInfo.EMPTY_ARRAY
    }
    val usages = findComponentUsages()
    val size = usages.size
    trackComponentUsage(FIND_USAGES, size)
    LOG.info("found $size ${pluralize("usage", size)} for \"${this.commandName}\" refactoring")
    foundUsages = usages.isNotEmpty()
    return usages
  }

  protected abstract fun findComponentUsages(): Array<out UsageInfo>

  override fun previewRefactoring(usages: Array<out UsageInfo>) {
    trackComponentUsage(PREVIEW_REFACTORING, usages.size)
    super.previewRefactoring(usages)
  }

  override fun execute(usages: Array<out UsageInfo>) {
    trackComponentUsage(EXECUTE, usages.size)
    super.execute(usages)
  }

  public abstract override fun getCommandName(): String

  open val groupingName
    get() = commandName

  open fun getReadMoreUrl(): String? = null

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

class AgpClasspathDependencyRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {

  constructor(project: Project, current: GradleVersion, new: GradleVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override fun necessity() = MANDATORY_CODEPENDENT

  override fun findComponentUsages(): Array<UsageInfo> {
    val usages = ArrayList<UsageInfo>()
    val buildSrcDir = File(getBaseDirPath(project), "buildSrc").toVirtualFile()
    // Using the buildModel, look for classpath dependencies on AGP, and if we find one, record it as a usage.
    buildModel.allIncludedBuildModels.forEach model@{ model ->
      model.buildscript().dependencies().artifacts(CLASSPATH).forEach dep@{ dep ->
        when (isUpdatablePluginDependency(new, dep)) {
          YES -> {
            val resultModel = dep.version().resultModel
            val element = resultModel.rawElement
            val psiElement = when (element) {
              null -> return@dep
              // TODO(xof): most likely we need a range in PsiElement, if the dependency is expressed in compactNotation
              is FakeArtifactElement -> element.realExpression.psiElement
              else -> element.psiElement
            }
            // This text gets used in the `target` display of the preview, and so needs to conform with our user interface
            // (having this be more of a verb than a noun).
            val presentableText = "Upgrading AGP configuration"
            psiElement?.let {
              usages.add(AgpVersionUsageInfo(WrappedPsiElement(it, this, USAGE_TYPE, presentableText), current, new, resultModel))
            }
          }
          else -> Unit
        }
      }
      if (model.moduleRootDirectory.toVirtualFile() == buildSrcDir) {
        model.dependencies().artifacts().forEach dep@{ dep ->
          when (isUpdatablePluginRelatedDependency(new, dep)) {
            YES -> {
              val resultModel = dep.version().resultModel
              val element = resultModel.rawElement
              val psiElement = when (element) {
                null -> return@dep
                // TODO(xof): most likely we need a range in PsiElement, if the dependency is expressed in compactNotation
                is FakeArtifactElement -> element.realExpression.psiElement
                else -> element.psiElement
              }
              val presentableText = when (element) {
                is FakeArtifactElement -> "AGP buildSrc dependency"
                else -> "AGP version specification"
              }
              psiElement?.let {
                usages.add(AgpVersionUsageInfo(WrappedPsiElement(it, this, USAGE_TYPE, presentableText), current, new, resultModel))
              }
            }
            else -> Unit
          }
        }
      }
    }
    return usages.toTypedArray()
  }

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(AGP_CLASSPATH_DEPENDENCY)

  override fun getCommandName(): String = "Upgrade AGP dependency from $current to $new"

  override fun getRefactoringId(): String = "com.android.tools.agp.upgrade.classpathDependency"

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = "Upgrade AGP classpath dependency version from $current to $new"
    }
  }

  companion object {
    val USAGE_TYPE = UsageType("Update version string")
  }
}

class AgpVersionUsageInfo(
  element: WrappedPsiElement,
  val current: GradleVersion,
  val new: GradleVersion,
  private val resultModel: GradlePropertyModel
) : GradleBuildModelUsageInfo(element) {
  override fun getTooltipText(): String {
    return "Upgrade AGP version from ${current} to ${new}"
  }

  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    resultModel.setValue(new.toString())
  }
}

class GMavenRepositoryRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {
  constructor(project: Project, current: GradleVersion, new: GradleVersion): super(project, current, new) {
    this.gradleVersion = AgpGradleVersionRefactoringProcessor.getCompatibleGradleVersion(new).version
  }
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor) {
    this.gradleVersion = AgpGradleVersionRefactoringProcessor.getCompatibleGradleVersion(processor.new).version
  }

  var gradleVersion: GradleVersion
    @VisibleForTesting set

  override fun necessity() = when {
    current < GradleVersion(3, 0, 0) && new >= GradleVersion(3, 0, 0) -> MANDATORY_CODEPENDENT
    new < GradleVersion(3, 0, 0) -> IRRELEVANT_FUTURE
    else -> IRRELEVANT_PAST
  }

  override fun findComponentUsages(): Array<UsageInfo> {
    val usages = ArrayList<UsageInfo>()
    // using the buildModel, look for classpath dependencies on AGP, and if we find one,
    // check the buildscript/repositories block for a google() gmaven entry, recording an additional usage if we don't find one
    buildModel.allIncludedBuildModels.forEach model@{ model ->
      model.buildscript().dependencies().artifacts(CLASSPATH).forEach dep@{ dep ->
        when (isUpdatablePluginDependency(new, dep)) {
          // consider returning a usage even if the dependency has the current version (in a chained upgrade, the dependency
          // might have been updated before this RefactoringProcessor gets a chance to run).  The applicability of the processor
          // will prevent this from being a problem.
          YES, NO -> {
            val repositories = model.buildscript().repositories()
            if (!repositories.hasGoogleMavenRepository()) {
              // TODO(xof) if we don't have a psiElement, we should add a suitable parent (and explain what
              //  we're going to do in terms of that parent.  (But a buildscript block without a repositories block is unusual)
              repositories.psiElement?.let { element ->
                val wrappedElement = WrappedPsiElement(element, this, USAGE_TYPE)
                usages.add(RepositoriesNoGMavenUsageInfo(wrappedElement, repositories, gradleVersion))
              }
            }
          }
          else -> Unit
        }
      }
    }
    return usages.toTypedArray()
  }

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(GMAVEN_REPOSITORY)

  override fun getCommandName(): String = "Add google() GMaven to buildscript repositories"

  override fun getRefactoringId(): String = "com.android.tools.agp.upgrade.gmaven"

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = "Add google() GMaven to buildscript repositories"
    }
  }

  companion object {
    val USAGE_TYPE = UsageType("Add GMaven declaration")
  }
}

class RepositoriesNoGMavenUsageInfo(
  element: WrappedPsiElement,
  private val repositoriesModel: RepositoriesModel,
  private val gradleVersion: GradleVersion
) : GradleBuildModelUsageInfo(element) {
  override fun getTooltipText(): String {
    return "Add google() to buildscript repositories"
  }

  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    repositoriesModel.addGoogleMavenRepository(gradleVersion)
  }
}

class AgpGradleVersionRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {

  constructor(project: Project, current: GradleVersion, new: GradleVersion): super(project, current, new) {
    this.compatibleGradleVersion = getCompatibleGradleVersion(new)
  }
  constructor(processor: AgpUpgradeRefactoringProcessor) : super(processor) {
    compatibleGradleVersion = getCompatibleGradleVersion(processor.new)
  }

  val compatibleGradleVersion: CompatibleGradleVersion

  override fun necessity() = MANDATORY_CODEPENDENT

  override fun findComponentUsages(): Array<out UsageInfo> {
    val usages = mutableListOf<UsageInfo>()
    // check the project's wrapper(s) for references to no-longer-supported Gradle versions
    project.basePath?.let {
      val projectRootFolders = listOf(File(FileUtils.toSystemDependentPath(it))) + BuildFileProcessor.getCompositeBuildFolderPaths(project)
      projectRootFolders.filterNotNull().forEach { ioRoot ->
        val ioFile = GradleWrapper.getDefaultPropertiesFilePath(ioRoot)
        val gradleWrapper = GradleWrapper.get(ioFile, project)
        val currentGradleVersion = gradleWrapper.gradleVersion ?: return@forEach
        val parsedCurrentGradleVersion = GradleVersion.tryParse(currentGradleVersion) ?: return@forEach
        if (!GradleUtil.isSupportedGradleVersion(parsedCurrentGradleVersion)) {
          val updatedUrl = gradleWrapper.getUpdatedDistributionUrl(compatibleGradleVersion.version.toString(), true);
          val virtualFile = VfsUtil.findFileByIoFile(ioFile, true) ?: return@forEach
          val propertiesFile = PsiManager.getInstance(project).findFile(virtualFile) as? PropertiesFile ?: return@forEach
          val property = propertiesFile.findPropertyByKey(GRADLE_DISTRIBUTION_URL_PROPERTY) ?: return@forEach
          val wrappedPsiElement = WrappedPsiElement(property.psiElement, this, USAGE_TYPE)
          usages.add(GradleVersionUsageInfo(wrappedPsiElement, compatibleGradleVersion.version, updatedUrl))
        }
      }
    }
    return usages.toTypedArray()
  }

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(GRADLE_VERSION)

  override fun getCommandName(): String = "Upgrade Gradle version to ${compatibleGradleVersion.version}"

  override fun getRefactoringId(): String = "com.android.tools.agp.upgrade.gradleVersion"

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = "Upgrade Gradle version to ${compatibleGradleVersion.version}"
    }
  }

  companion object {
    val USAGE_TYPE = UsageType("Update Gradle distribution URL")

    enum class CompatibleGradleVersion(val version: GradleVersion) {
      // versions earlier than 4.4 (corresponding to AGP 3.0.0 and below) are not needed because
      // we no longer support running such early versions of Gradle given our required JDKs, so upgrading to
      // them using this functionality is a non-starter.
      VERSION_4_4(GradleVersion.parse("4.4")),
      VERSION_4_6(GradleVersion.parse("4.6")),
      VERSION_4_10_1(GradleVersion.parse("4.10.1")),
      VERSION_5_1_1(GradleVersion.parse("5.1.1")),
      VERSION_5_4_1(GradleVersion.parse("5.4.1")),
      VERSION_5_6_4(GradleVersion.parse("5.6.4")),
      VERSION_6_1_1(GradleVersion.parse("6.1.1")),
      VERSION_6_5(GradleVersion.parse("6.5")),
      VERSION_FOR_DEV(GradleVersion.parse(GRADLE_MINIMUM_VERSION))
    }

    fun getCompatibleGradleVersion(agpVersion: GradleVersion): CompatibleGradleVersion {
      val agpVersionMajorMinor = GradleVersion(agpVersion.major, agpVersion.minor)
      return when {
        GradleVersion.parse("3.1") >= agpVersionMajorMinor -> VERSION_4_4
        GradleVersion.parse("3.2") >= agpVersionMajorMinor -> VERSION_4_6
        GradleVersion.parse("3.3") >= agpVersionMajorMinor -> VERSION_4_10_1
        GradleVersion.parse("3.4") >= agpVersionMajorMinor -> VERSION_5_1_1
        GradleVersion.parse("3.5") >= agpVersionMajorMinor -> VERSION_5_4_1
        GradleVersion.parse("3.6") >= agpVersionMajorMinor -> VERSION_5_6_4
        GradleVersion.parse("4.0") >= agpVersionMajorMinor -> VERSION_6_1_1
        GradleVersion.parse("4.1") >= agpVersionMajorMinor -> VERSION_6_5
        else -> VERSION_FOR_DEV
      }
    }
  }
}

class GradleVersionUsageInfo(
  element: WrappedPsiElement,
  private val gradleVersion: GradleVersion,
  private val updatedUrl: String
) : GradleBuildModelUsageInfo(element) {
  override fun getTooltipText(): String {
    return "Upgrade Gradle version to $gradleVersion"
  }

  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    ((element as? WrappedPsiElement)?.realElement as? Property)?.setValue(updatedUrl)
    // TODO(xof): if we brought properties files into the build model, this would not be necessary here, but the buildModel applyChanges()
    //  does all that is necessary to save files, so we do that here to mimic that.  Should we do that in
    //  performPsiSpoilingBuildModelRefactoring instead, to mimic the time applyChanges() would do that more precisely?
    val documentManager = PsiDocumentManager.getInstance(project)
    val document = documentManager.getDocument(element!!.containingFile) ?: return
    if (documentManager.isDocumentBlockedByPsi(document)) {
      documentManager.doPostponedOperationsAndUnblockDocument(document)
    }
    FileDocumentManager.getInstance().saveDocument(document)
    if (!documentManager.isCommitted(document)) {
      documentManager.commitDocument(document)
    }
  }
}

class Java8DefaultRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {

  var noLanguageLevelAction = INSERT_OLD_DEFAULT
    set(value) {
      LOG.info("setting noLanguageLevelAction to ${value.name}")
      field = value
    }

  constructor(project: Project, current: GradleVersion, new: GradleVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override fun necessity() = when {
    current < ACTIVATED_VERSION && new >= ACTIVATED_VERSION -> MANDATORY_CODEPENDENT
    new < ACTIVATED_VERSION -> IRRELEVANT_FUTURE
    else -> IRRELEVANT_PAST
  }

  override fun findComponentUsages(): Array<out UsageInfo> {
    fun usageType(model: LanguageLevelPropertyModel): UsageType? = when {
      model.psiElement != null -> EXISTING_DIRECTIVE_USAGE_TYPE
      noLanguageLevelAction == INSERT_OLD_DEFAULT -> INSERT_OLD_USAGE_TYPE
      noLanguageLevelAction == ACCEPT_NEW_DEFAULT -> ACCEPT_NEW_USAGE_TYPE
      else -> null
    }

    val usages = mutableListOf<UsageInfo>()
    buildModel.allIncludedBuildModels.forEach model@{ model ->
      // TODO(xof): we should consolidate the various ways of guessing what a module is from its plugins (see also
      //  PsModuleType.kt)
      val pluginNames = model.plugins().map { it.name().forceString() }
      pluginNames
        .firstOrNull { it.startsWith("java") || it.startsWith("org.gradle.java") || it == "application" || it == "org.gradle.application" }
        ?.let { _ ->
        model.java().sourceCompatibility().let {
          val psiElement = listOf(it, model.java(), model).firstNotNullResult { model -> model.psiElement }!!
          val wrappedElement = WrappedPsiElement(psiElement, this, usageType(it))
          val existing = it.psiElement != null
          usages.add(JavaLanguageLevelUsageInfo(wrappedElement, it, existing, noLanguageLevelAction, "sourceCompatibility"))
        }
        model.java().targetCompatibility().let {
          val psiElement = listOf(it, model.java(), model).firstNotNullResult { model -> model.psiElement }!!
          val wrappedElement = WrappedPsiElement(psiElement, this, usageType(it))
          val existing = it.psiElement != null
          usages.add(JavaLanguageLevelUsageInfo(wrappedElement, it, existing, noLanguageLevelAction, "targetCompatibility"))
        }
      }

      pluginNames.firstOrNull { it.startsWith("com.android") }?.let { _ ->
        model.android().compileOptions().sourceCompatibility().let {
          val psiElement = listOf(it, model.android().compileOptions(), model.android(), model)
            .firstNotNullResult { model -> model.psiElement }!!
          val wrappedElement = WrappedPsiElement(psiElement, this, usageType(it))
          val existing = it.psiElement != null
          usages.add(JavaLanguageLevelUsageInfo(wrappedElement, it, existing, noLanguageLevelAction, "sourceCompatibility"))
        }
        model.android().compileOptions().targetCompatibility().let {
          val psiElement = listOf(it, model.android().compileOptions(), model.android(), model)
            .firstNotNullResult { model -> model.psiElement }!!
          val wrappedElement = WrappedPsiElement(psiElement, this, usageType(it))
          val existing = it.psiElement != null
          usages.add(JavaLanguageLevelUsageInfo(wrappedElement, it, existing, noLanguageLevelAction, "targetCompatibility"))
        }
        pluginNames.firstOrNull { it.startsWith("org.jetbrains.kotlin") || it.startsWith("kotlin") }?.let { _ ->
          model.android().kotlinOptions().jvmTarget().let {
            val psiElement = listOf(it, model.android().kotlinOptions(), model.android(), model)
              .firstNotNullResult { model -> model.psiElement }!!
            val wrappedElement = WrappedPsiElement(psiElement, this, usageType(it))
            val existing = it.psiElement != null
            usages.add(KotlinLanguageLevelUsageInfo(wrappedElement, it, existing, noLanguageLevelAction, "jvmTarget"))
          }
        }
      }
    }
    return usages.toTypedArray()
  }

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder {
    val protoNoLanguageLevelAction = when (noLanguageLevelAction) {
      INSERT_OLD_DEFAULT -> Java8DefaultProcessorSettings.NoLanguageLevelAction.INSERT_OLD_DEFAULT
      ACCEPT_NEW_DEFAULT -> Java8DefaultProcessorSettings.NoLanguageLevelAction.ACCEPT_NEW_DEFAULT
    }
    val java8Settings = Java8DefaultProcessorSettings.newBuilder().setNoLanguageLevelAction(protoNoLanguageLevelAction).build()
    return builder.setKind(JAVA8_DEFAULT).setJava8DefaultSettings(java8Settings)
  }

  override fun getCommandName(): String = "Update default Java language level"

  override val groupingName = "Add directives to keep using Java 7"

  override fun getRefactoringId(): String = "com.android.tools.agp.upgrade.Java8Default"

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = "Update default Java language level"
    }
  }

  override fun computeIsAlwaysNoOpForProject(): Boolean =
    findComponentUsages().all {
      when (it) {
        is JavaLanguageLevelUsageInfo -> it.existing
        is KotlinLanguageLevelUsageInfo -> it.existing
        else -> false // shouldn't happen; if it does, return conservative value
      }
    }

  override fun getReadMoreUrl(): String? = "https://developer.android.com/r/tools/upgrade-assistant/java8-default"

  companion object {
    val ACTIVATED_VERSION = GradleVersion.parse("4.2.0-alpha05")

    val EXISTING_DIRECTIVE_USAGE_TYPE = UsageType("Existing language level directive (leave unchanged)")
    val ACCEPT_NEW_USAGE_TYPE = UsageType("Accept new default (leave unchanged)")
    val INSERT_OLD_USAGE_TYPE = UsageType("Continue using Java 7 (insert language level directives)")
  }

  enum class NoLanguageLevelAction(val text: String) {
    ACCEPT_NEW_DEFAULT("Accept the new default of Java 8"),
    INSERT_OLD_DEFAULT("Insert directives to continue using Java 7"),
    ;

    override fun toString() = text
  }
}

class JavaLanguageLevelUsageInfo(
  element: WrappedPsiElement,
  private val model: LanguageLevelPropertyModel,
  internal val existing: Boolean,
  private val noLanguageLevelAction: NoLanguageLevelAction,
  private val propertyName: String
): GradleBuildModelUsageInfo(element) {
  override fun getTooltipText(): String {
    return when (existing) {
      false -> when (noLanguageLevelAction) {
        INSERT_OLD_DEFAULT -> "insert explicit $propertyName to preserve previous behaviour"
        ACCEPT_NEW_DEFAULT -> "accept new default"
      }
      true -> "preserve existing explicit $propertyName"
    }
  }

  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    when {
      !existing && noLanguageLevelAction == INSERT_OLD_DEFAULT -> model.setLanguageLevel(LanguageLevel.JDK_1_7)
    }
  }

  override fun getDiscriminatingValues(): List<Any> = listOf(propertyName)
}

class KotlinLanguageLevelUsageInfo(
  element: WrappedPsiElement,
  private val model: LanguageLevelPropertyModel,
  internal val existing: Boolean,
  private val noLanguageLevelAction: NoLanguageLevelAction,
  private val propertyName: String
  ): SpoilingGradleBuildModelUsageInfo(element) {
  override fun getTooltipText(): String {
    return when (existing) {
      false -> when (noLanguageLevelAction) {
        INSERT_OLD_DEFAULT -> "insert explicit $propertyName to preserve previous behaviour"
        ACCEPT_NEW_DEFAULT -> "accept new default"

      }
      true -> "preserve existing explicit $propertyName"
    }
  }

  val gradleFile = model.gradleFile

  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    when {
      !existing && noLanguageLevelAction == INSERT_OLD_DEFAULT -> {
        model.setLanguageLevel(LanguageLevel.JDK_1_6)
        super.performBuildModelRefactoring(processor)
      }
    }
  }

  override fun performPsiSpoilingBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    val element = processor.buildModel.getModuleBuildModel(gradleFile).android().kotlinOptions().jvmTarget().psiElement ?: return
    val documentManager = PsiDocumentManager.getInstance(project)
    val document = documentManager.getDocument(element.containingFile) ?: return
    document.insertString(element.textRange.endOffset, " // Java 7 not supported by kotlinOptions jvmTarget")
    FileDocumentManager.getInstance().saveDocument(document)
    documentManager.commitDocument(document)
  }

  override fun getDiscriminatingValues(): List<Any> = listOf(propertyName)
}

class CompileRuntimeConfigurationRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {
  constructor(project: Project, current: GradleVersion, new: GradleVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override fun necessity() = when {
    current < IMPLEMENTATION_API_INTRODUCED && new >= COMPILE_REMOVED -> MANDATORY_CODEPENDENT
    current < COMPILE_REMOVED && new >= COMPILE_REMOVED -> MANDATORY_INDEPENDENT
    new < IMPLEMENTATION_API_INTRODUCED -> IRRELEVANT_FUTURE
    current >= COMPILE_REMOVED -> IRRELEVANT_PAST
    current < IMPLEMENTATION_API_INTRODUCED -> OPTIONAL_CODEPENDENT
    else -> OPTIONAL_INDEPENDENT
  }

  override fun findComponentUsages(): Array<out UsageInfo> {
    val usages = mutableListOf<UsageInfo>()

    fun computeReplacementName(name: String, compileReplacement: String): String? {
      return when {
        name == "compile" -> compileReplacement
        name.endsWith("Compile") && (name.startsWith("test") || name.startsWith("androidTest")) ->
          name.removeSuffix("Compile").appendCapitalized("implementation")
        name.endsWith("Compile") -> name.removeSuffix("Compile").appendCapitalized(compileReplacement)
        name == "runtime" -> "runtimeOnly"
        name.endsWith("Runtime") -> "${name}Only"
        else -> null
      }
    }

    fun maybeAddUsageForDependency(dependency: DependencyModel, compileReplacement: String, psiElement: PsiElement) {
      val configuration = dependency.configurationName()
      computeReplacementName(configuration, compileReplacement)?.let {
        val wrappedElement = WrappedPsiElement(psiElement, this, CHANGE_DEPENDENCY_CONFIGURATION_USAGE_TYPE)
        usages.add(ObsoleteConfigurationDependencyUsageInfo(wrappedElement, dependency, it))
      }
    }

    fun maybeAddUsageForConfiguration(configuration: ConfigurationModel, compileReplacement: String, psiElement: PsiElement) {
      val name = configuration.name()
      computeReplacementName(name, compileReplacement)?.let {
        val wrappedElement = WrappedPsiElement(psiElement, this, RENAME_CONFIGURATION_USAGE_TYPE)
        usages.add(ObsoleteConfigurationConfigurationUsageInfo(wrappedElement, configuration, it))
      }
    }

    buildModel.allIncludedBuildModels.forEach model@{ model ->
      // if we don't have a PsiElement for the model, we don't have a file at all, and attempting to perform a refactoring is not
      // going to work
      val modelPsiElement = model.psiElement ?: return@model
      val pluginSet = model.plugins().map { it.name().forceString() }.toSet()
      // TODO(xof): as with the Java8Default refactoring above, we should define and use some kind of API
      //  to determine what kind of a module we have.
      val applicationSet = setOf(
        "application", "org.gradle.application", // see Gradle documentation for PluginDependenciesSpec for `org.gradle.` prefix
        "com.android.application", "com.android.test", "com.android.instant-app")
      val librarySet = setOf(
        "java", "java-library", "org.gradle.java", "org.gradle.java-library",
        "com.android.library", "com.android.dynamic-feature", "com.android.feature")
      val compileReplacement = when {
        !model.android().dynamicFeatures().toList().isNullOrEmpty() -> "api"
        pluginSet.intersect(applicationSet).isNotEmpty() -> "implementation"
        pluginSet.intersect(librarySet).isNotEmpty() -> "api"
        else -> return@model
      }

      model.dependencies().all()
        .forEach { dependency ->
          val psiElement = dependency.psiElement ?: model.dependencies().psiElement ?: modelPsiElement
          maybeAddUsageForDependency(dependency, compileReplacement, psiElement)
        }
      // Although there might be a buildscript with dependencies, those dependencies cannot be added to a compile/runtime configuration
      // out of the box -- and if somehow a user manages to configure things to have compile/runtime configurations there, there's no
      // guarantee that they mean the same as the deprecated gradle ones.
      model.configurations().all()
        .forEach { configuration ->
          // this PsiElement is used for displaying in the refactoring preview window, rather than for performing the refactoring; it is
          // therefore appropriate and safe to pass a notional parent Psi if the element is not (yet) on file.
          // TODO(b/159597456): here and elsewhere, encode this hierarchical logic more declaratively.
          val psiElement = configuration.psiElement ?: model.configurations().psiElement ?: modelPsiElement
          maybeAddUsageForConfiguration(configuration, compileReplacement, psiElement)
        }
    }
    return usages.toTypedArray()
  }

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(COMPILE_RUNTIME_CONFIGURATION)

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> = PsiElement.EMPTY_ARRAY

      override fun getProcessedElementsHeader(): String = "Replace deprecated configurations"
    }
  }

  override fun getRefactoringId(): String = "com.android.tools.agp.upgrade.CompileRuntimeConfiguration"

  override fun getCommandName(): String = "Replace deprecated configurations"

  companion object {
    val IMPLEMENTATION_API_INTRODUCED = GradleVersion(3, 5, 0)
    val COMPILE_REMOVED = GradleVersion(5, 0, 0)

    val RENAME_CONFIGURATION_USAGE_TYPE = UsageType("Rename configuration")
    val CHANGE_DEPENDENCY_CONFIGURATION_USAGE_TYPE = UsageType("Change dependency configuration")
  }
}

class ObsoleteConfigurationDependencyUsageInfo(
  element: WrappedPsiElement,
  private val dependency: DependencyModel,
  private val newConfigurationName: String
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    dependency.setConfigurationName(newConfigurationName)
  }

  override fun getTooltipText() = "Update configuration from ${dependency.configurationName()} to $newConfigurationName"

  override fun getDiscriminatingValues(): List<Any> = listOf(dependency, newConfigurationName)
}

class ObsoleteConfigurationConfigurationUsageInfo(
  element: WrappedPsiElement,
  private val configuration: ConfigurationModel,
  private val newConfigurationName: String
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    configuration.rename(newConfigurationName)
  }

  override fun getTooltipText() = "Rename configuration from ${configuration.name()} to $newConfigurationName"

  override fun getDiscriminatingValues(): List<Any> = listOf(configuration, newConfigurationName)
}

class FabricCrashlyticsRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {
  constructor(project: Project, current: GradleVersion, new: GradleVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override fun necessity() = when {
    current < INCOMPATIBLE_VERSION && new >= INCOMPATIBLE_VERSION -> MANDATORY_CODEPENDENT
    new < INCOMPATIBLE_VERSION -> IRRELEVANT_FUTURE
    else -> IRRELEVANT_PAST
  }

  override fun findComponentUsages(): Array<out UsageInfo> {
    val usages = ArrayList<UsageInfo>()
    buildModel.allIncludedBuildModels.forEach model@{ model ->
      val modelPsiElement = model.psiElement ?: return@model

      // ref. https://firebase.google.com/docs/crashlytics/upgrade-sdk?platform=android Step 2.1:
      // - Replace Fabric's Maven repository with Google's Maven repository.
      // - Replace the Fabric Gradle plugin with the Firebase Crashlytics Gradle plugin.
      run {
        var hasGoogleServices = false
        var hasFirebaseCrashlytics = false
        var seenFabricCrashlytics = false
        val dependencies = model.buildscript().dependencies()
        val dependenciesOrHigherPsiElement = dependencies.psiElement ?: model.buildscript().psiElement ?: modelPsiElement
        dependencies.artifacts(CLASSPATH).forEach dep@{ dep ->
          when {
            dep.spec.group == "com.google.gms" && dep.spec.name == "google-services" -> hasGoogleServices = true
            dep.spec.group == "com.google.firebase" && dep.spec.name == "firebase-crashlytics-gradle" -> hasFirebaseCrashlytics = true
            dep.spec.group == "io.fabric.tools" && dep.spec.name == "gradle" -> {
              // remove the dependency on the Fabric Gradle plugin
              val psiElement = dep.psiElement ?: dependenciesOrHigherPsiElement
              val wrappedPsiElement = WrappedPsiElement(psiElement, this, REMOVE_FABRIC_CLASSPATH_USAGE_TYPE)
              val usageInfo = RemoveFabricClasspathDependencyUsageInfo(wrappedPsiElement, dependencies, dep)
              usages.add(usageInfo)
              seenFabricCrashlytics = true
            }
          }
        }
        if (seenFabricCrashlytics) {
          // if we are a project that currently declares a dependency on io.fabric.tools:gradle (the Fabric Gradle plugin) ...
          if (!hasGoogleServices) {
            // ... if we don't have Google Services already, add it
            val wrappedPsiElement = WrappedPsiElement(dependenciesOrHigherPsiElement, this, ADD_GOOGLE_SERVICES_CLASSPATH_USAGE_TYPE)
            val usageInfo = AddGoogleServicesClasspathDependencyUsageInfo(wrappedPsiElement, dependencies)
            usages.add(usageInfo)
          }
          if (!hasFirebaseCrashlytics) {
            // ... if we don't have Firebase Crashlytics already
            val wrappedPsiElement = WrappedPsiElement(dependenciesOrHigherPsiElement, this, ADD_FIREBASE_CRASHLYTICS_CLASSPATH_USAGE_TYPE)
            val usageInfo = AddFirebaseCrashlyticsClasspathDependencyUsageInfo(wrappedPsiElement, dependencies)
            usages.add(usageInfo)
          }
        }

        var seenFabricMavenRepository = false
        val repositories = model.buildscript().repositories()
        val repositoriesOrHigherPsiElement = repositories.psiElement ?: model.buildscript().psiElement ?: modelPsiElement
        repositories.repositories().filterIsInstance(MavenRepositoryModel::class.java).forEach repo@{ repo ->
          if (repo.url().forceString().startsWith("https://maven.fabric.io/public")) {
            val psiElement = repo.psiElement ?: repositoriesOrHigherPsiElement
            val wrappedPsiElement = WrappedPsiElement(psiElement, this, REMOVE_FABRIC_REPOSITORY_USAGE_TYPE)
            val usageInfo = RemoveFabricMavenRepositoryUsageInfo(wrappedPsiElement, repositories, repo)
            usages.add(usageInfo)
            seenFabricMavenRepository = true
          }
        }
        if (seenFabricMavenRepository && !repositories.hasGoogleMavenRepository()) {
          // TODO(xof): in theory this could collide with the refactoring to add google() to pre-3.0.0 projects.  In practice there's
          //  probably little overlap in fabric upgrades with such old projects.
          val compatibleGradleVersion = AgpGradleVersionRefactoringProcessor.getCompatibleGradleVersion(new)
          val wrappedPsiElement = WrappedPsiElement(repositoriesOrHigherPsiElement, this, ADD_GMAVEN_REPOSITORY_USAGE_TYPE)
          val usageInfo = AddGoogleMavenRepositoryUsageInfo(wrappedPsiElement, repositories, compatibleGradleVersion.version)
          usages.add(usageInfo)
        }
      }

      // ref. https://firebase.google.com/docs/crashlytics/upgrade-sdk?platform=android Step 2.2
      // - In your app-level build.gradle, replace the Fabric plugin with the Firebase Crashlytics plugin.
      run {
        val pluginsOrHigherPsiElement = model.pluginsPsiElement ?: modelPsiElement
        var seenFabricPlugin = false
        var seenGoogleServicesPlugin = false
        model.plugins().forEach { plugin ->
          when (plugin.name().forceString()) {
            "com.google.gms.google-services" -> seenGoogleServicesPlugin = true
            "io.fabric" -> {
              val psiElement = plugin.psiElement ?: pluginsOrHigherPsiElement
              val wrappedPsiElement = WrappedPsiElement(psiElement, this, REPLACE_FABRIC_PLUGIN_USAGE_TYPE)
              val usageInfo = ReplaceFabricPluginUsageInfo(wrappedPsiElement, plugin)
              usages.add(usageInfo)
              seenFabricPlugin = true
            }
          }
        }
        if (seenFabricPlugin && !seenGoogleServicesPlugin) {
          val wrappedPsiElement = WrappedPsiElement(pluginsOrHigherPsiElement, this, APPLY_GOOGLE_SERVICES_PLUGIN_USAGE_TYPE)
          val usageInfo = ApplyGoogleServicesPluginUsageInfo(wrappedPsiElement, model)
          usages.add(usageInfo)
        }
      }

      run {
        val dependencies = model.dependencies()
        val dependenciesOrHigherPsiElement = dependencies.psiElement ?: modelPsiElement

        // ref. https://firebase.google.com/docs/crashlytics/upgrade-sdk?platform=android Step 2.3
        // - In your app-level build.gradle, replace the legacy Fabric Crashlytics SDK with the new Firebase Crashlytics SDK.
        //   Make sure you add version 17.0.0 or later (beginning November 15, 2020, this is required for your crash reports to appear
        //   in the Firebase console).
        run {
          var seenFabricSdk = false
          var seenFirebaseSdk = false
          var seenGoogleAnalyticsSdk = false
          dependencies.artifacts().forEach dep@{ dep ->
            when {
              dep.spec.group == "com.crashlytics.sdk.android" && dep.spec.name == "crashlytics" -> {
                val psiElement = dep.psiElement ?: dependenciesOrHigherPsiElement
                val wrappedPsiElement = WrappedPsiElement(psiElement, this, REMOVE_FABRIC_CRASHLYTICS_SDK_USAGE_TYPE)
                val usageInfo = RemoveFabricCrashlyticsSdkUsageInfo(wrappedPsiElement, dependencies, dep)
                usages.add(usageInfo)
                seenFabricSdk = true
              }
              dep.spec.group == "com.google.firebase" && dep.spec.name == "firebase-crashlytics" -> seenFirebaseSdk = true
              dep.spec.group == "com.google.firebase" && dep.spec.name == "google-analytics" -> seenGoogleAnalyticsSdk = true
            }
          }
          // if we currently depend on the Fabric SDK ...
          if (seenFabricSdk) {
            // ... insert a dependency on the Firebase Crashlytics SDK, if not already present ...
            if (!seenFirebaseSdk) {
              val wrappedPsiElement = WrappedPsiElement(dependenciesOrHigherPsiElement, this, ADD_FIREBASE_CRASHLYTICS_SDK_USAGE_TYPE)
              val usageInfo = AddFirebaseCrashlyticsSdkUsageInfo(wrappedPsiElement, dependencies)
              usages.add(usageInfo)
            }
            // ... and insert a dependency on the Google Analytics SDK, as recommended.
            if (!seenGoogleAnalyticsSdk) {
              val wrappedPsiElement = WrappedPsiElement(dependenciesOrHigherPsiElement, this, ADD_GOOGLE_ANALYTICS_SDK_USAGE_TYPE)
              val usageInfo = AddGoogleAnalyticsSdkUsageInfo(wrappedPsiElement, dependencies)
              usages.add(usageInfo)
            }
          }
        }

        // ref. https://firebase.google.com/docs/crashlytics/upgrade-sdk?platform=android Optional Step: Set up Ndk crash reporting
        // - only done if crashlytics.enableNdk is present and enabled in the current project.
        // - In your app-level build.gradle, replace the Fabric NDK dependency with the Firebase Crashlytics NDK dependency. Then,
        //   add the firebaseCrashlytics extension and make sure to enable the nativeSymbolUploadEnabled flag.
        run {
          if (model.crashlytics().enableNdk().getValue(BOOLEAN_TYPE) == true) {
            // if enableNdk is true (not false or null/non-existent), remove it ...
            run {
              val psiElement = model.crashlytics().enableNdk().psiElement ?: model.crashlytics().psiElement ?: modelPsiElement
              val wrappedPsiElement = WrappedPsiElement(psiElement, this, REMOVE_CRASHLYTICS_ENABLE_NDK_USAGE_INFO)
              val usageInfo = RemoveCrashlyticsEnableNdkUsageInfo(wrappedPsiElement, model)
              usages.add(usageInfo)
            }
            // ... turn on native symbol upload for the `release` buildType ...
            run {
              val releaseBuildType = model.android().buildTypes().first { it.name() == "release" }
              val psiElement = releaseBuildType.psiElement ?: model.android().psiElement ?: modelPsiElement
              val wrappedPsiElement = WrappedPsiElement(psiElement, this, ADD_FIREBASE_CRASHLYTICS_NATIVE_SYMBOL_UPLOAD)
              val usageInfo = AddBuildTypeFirebaseCrashlyticsUsageInfo(wrappedPsiElement, releaseBuildType)
              usages.add(usageInfo)
            }
          }

          // replace the Fabric NDK dependency with the Firebase Crashlytics NDK dependency
          var seenFabricNdk = false
          var seenFirebaseCrashlyticsNdk = false
          dependencies.artifacts().forEach dep@{ dep ->
            when {
              dep.spec.group == "com.crashlytics.sdk.android" && dep.spec.name == "crashlytics-ndk" -> {
                val psiElement = dep.psiElement ?: dependenciesOrHigherPsiElement
                val wrappedPsiElement = WrappedPsiElement(psiElement, this, REMOVE_FABRIC_NDK_USAGE_TYPE)
                val usageInfo = RemoveFabricNdkUsageInfo(wrappedPsiElement, dependencies, dep)
                usages.add(usageInfo)
                seenFabricNdk = true
              }
              dep.spec.group == "com.google.firebase" && dep.spec.name == "firebase-crashlytics-ndk" -> seenFirebaseCrashlyticsNdk = true
            }
          }
          if (seenFabricNdk && !seenFirebaseCrashlyticsNdk) {
            val wrappedPsiElement = WrappedPsiElement(dependenciesOrHigherPsiElement, this, ADD_FIREBASE_CRASHLYTICS_NDK_USAGE_TYPE)
            val usageInfo = AddFirebaseCrashlyticsNdkUsageInfo(wrappedPsiElement, dependencies)
            usages.add(usageInfo)
          }
        }
      }
    }
    return usages.toTypedArray()
  }

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = "Migrate crashlytics from fabric to firebase"
    }
  }

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(FABRIC_CRASHLYTICS)

  override fun getCommandName() = "Migrate crashlytics from fabric to firebase"

  override fun getReadMoreUrl(): String? = "https://firebase.google.com/docs/crashlytics/upgrade-sdk?platform=android"

  companion object {
    val INCOMPATIBLE_VERSION = GradleVersion.parse("4.1.0-alpha05") // see b/154302886

    val REMOVE_FABRIC_REPOSITORY_USAGE_TYPE = UsageType("Remove the Fabric Maven repository")
    val ADD_GMAVEN_REPOSITORY_USAGE_TYPE = UsageType("Add the Google Maven repository")

    val REMOVE_FABRIC_CLASSPATH_USAGE_TYPE = UsageType("Remove the dependency on the Fabric Gradle plugin")
    val ADD_GOOGLE_SERVICES_CLASSPATH_USAGE_TYPE = UsageType("Add a dependency on the Google Services Gradle plugin")
    val ADD_FIREBASE_CRASHLYTICS_CLASSPATH_USAGE_TYPE = UsageType("Add a dependency on the Firebase Crashlytics Gradle plugin")

    val REPLACE_FABRIC_PLUGIN_USAGE_TYPE = UsageType("Replace the Fabric plugin with the Firebase Crashlytics plugin")
    val APPLY_GOOGLE_SERVICES_PLUGIN_USAGE_TYPE = UsageType("Apply the Google Services plugin")

    val REMOVE_FABRIC_CRASHLYTICS_SDK_USAGE_TYPE = UsageType("Remove the dependency on the Fabric SDK")
    val ADD_FIREBASE_CRASHLYTICS_SDK_USAGE_TYPE = UsageType("Add a dependency on the Firebase Crashlytics SDK")
    val ADD_GOOGLE_ANALYTICS_SDK_USAGE_TYPE = UsageType("Add a dependency on the Google Analytics SDK")

    val REMOVE_FABRIC_NDK_USAGE_TYPE = UsageType("Remove the Fabric NDK dependency")
    val ADD_FIREBASE_CRASHLYTICS_NDK_USAGE_TYPE = UsageType("Add the Firebase Crashlytics NDK dependency")
    val REMOVE_CRASHLYTICS_ENABLE_NDK_USAGE_INFO = UsageType("Remove the enableNdk crashlytics flag")
    val ADD_FIREBASE_CRASHLYTICS_NATIVE_SYMBOL_UPLOAD = UsageType("Enable native symbol upload for the release buildType")
  }
}

class RemoveFabricMavenRepositoryUsageInfo(
  element: WrappedPsiElement,
  private val repositories: RepositoriesModel,
  private val repository: RepositoryModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    repositories.removeRepository(repository)
  }

  override fun getTooltipText() = "Remove the Fabric Maven repository"
}

// TODO(xof): investigate unifying this with the NoGMavenUsageInfo class above

class AddGoogleMavenRepositoryUsageInfo(
  element: WrappedPsiElement,
  private val repositories: RepositoriesModel,
  private val gradleVersion: GradleVersion
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    repositories.addGoogleMavenRepository(gradleVersion)
  }

  override fun getTooltipText() = "Add the Google Maven repository"
}

class RemoveFabricClasspathDependencyUsageInfo(
  element: WrappedPsiElement,
  private val dependencies: DependenciesModel,
  private val dependency: DependencyModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    dependencies.remove(dependency)
  }

  override fun getTooltipText() = "Remove the dependency on the Fabric Gradle plugin"
}

class AddGoogleServicesClasspathDependencyUsageInfo(
  element: WrappedPsiElement,
  private val dependencies: DependenciesModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    // TODO(xof): how to find the current version?  Or the version contemporaneous with this AGP/Studio?
    dependencies.addArtifact("classpath", "com.google.gms:google-services:4.3.3")
  }

  override fun getTooltipText() = "Add a dependency on the Google Services Gradle plugin"
}

class AddFirebaseCrashlyticsClasspathDependencyUsageInfo(
  element: WrappedPsiElement,
  private val dependencies: DependenciesModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    // TODO(xof): how to find the current version?  Or the version contemporaneous with this AGP/Studio?
    dependencies.addArtifact("classpath", "com.google.firebase:firebase-crashlytics-gradle:2.3.0")
  }

  override fun getTooltipText() = "Add a dependency on the Firebase Crashlytics Gradle plugin"
}

class ReplaceFabricPluginUsageInfo(
  element: WrappedPsiElement,
  private val plugin: PluginModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    plugin.name().setValue("com.google.firebase.crashlytics")
  }

  override fun getTooltipText() = "Replace the Fabric plugin with the Firebase Crashlytics plugin"
}

class ApplyGoogleServicesPluginUsageInfo(
  element: WrappedPsiElement,
  private val model: GradleBuildModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    model.applyPlugin("com.google.gms.google-services")
  }

  override fun getTooltipText() = "Apply the Google Services plugin"
}

class RemoveFabricCrashlyticsSdkUsageInfo(
  element: WrappedPsiElement,
  private val dependencies: DependenciesModel,
  private val dependency: DependencyModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    dependencies.remove(dependency)
  }

  override fun getTooltipText() = "Remove the dependency on the Fabric SDK"
}

class AddFirebaseCrashlyticsSdkUsageInfo(
  element: WrappedPsiElement,
  private val dependencies: DependenciesModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    dependencies.addArtifact("implementation", "com.google.firebase:firebase-crashlytics:17.2.1")
  }

  override fun getTooltipText() = "Add a dependency on the Firebase Crashlytics SDK"
}

class AddGoogleAnalyticsSdkUsageInfo(
  element: WrappedPsiElement,
  private val dependencies: DependenciesModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    dependencies.addArtifact("implementation", "com.google.firebase:firebase-analytics:17.5.0")
  }

  override fun getTooltipText() = "Add a dependency on the Google Analytics SDK"
}

class RemoveFabricNdkUsageInfo(
  element: WrappedPsiElement,
  private val dependencies: DependenciesModel,
  private val dependency: DependencyModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    dependencies.remove(dependency)
  }

  override fun getTooltipText() = "Remove the Fabric NDK dependency"
}

class AddFirebaseCrashlyticsNdkUsageInfo(
  element: WrappedPsiElement,
  private val dependencies: DependenciesModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    dependencies.addArtifact("implementation", "com.google.firebase:firebase-crashlytics-ndk:17.2.1")
  }

  override fun getTooltipText() = "Add the Firebase Crashlytics NDK dependency"
}

class RemoveCrashlyticsEnableNdkUsageInfo(
  element: WrappedPsiElement,
  private val model: GradleBuildModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    model.crashlytics().enableNdk().delete()
  }

  override fun getTooltipText() = "Remove the enableNdk crashlytics flag"
}

class AddBuildTypeFirebaseCrashlyticsUsageInfo(
  element: WrappedPsiElement,
  private val buildType: BuildTypeModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    buildType.firebaseCrashlytics().nativeSymbolUploadEnabled().setValue(true)
  }

  override fun getTooltipText() = "Enable native symbol upload for the release buildType"
}

/**
 * Usage Types for usages coming from [AgpUpgradeComponentRefactoringProcessor]s.
 *
 * This usage type provider will only provide a usage type if the element in question is a [WrappedPsiElement], which is not
 * intended for use outside this package; it will return null in all other cases.  The [UsageType] it returns should give
 * a high-level description of the effect the refactoring will have on this usage.
 */
class AgpComponentUsageTypeProvider : UsageTypeProvider {
  override fun getUsageType(element: PsiElement?): UsageType? =
    if (StudioFlags.AGP_UPGRADE_ASSISTANT.get()) (element as? WrappedPsiElement)?.usageType else null
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
internal fun AgpUpgradeRefactoringProcessor.trackProcessorUsage(kind: UpgradeAssistantEventKind, usages: Int? = null) {
  val processorEvent = UpgradeAssistantProcessorEvent.newBuilder()
    .setUpgradeUuid(uuid)
    .setCurrentAgpVersion(current.toString()).setNewAgpVersion(new.toString())
    .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(kind).apply { usages?.let { setUsages(it) } }.build())
  processorEvent.addComponentInfo(classpathRefactoringProcessor.getComponentInfo())
  componentRefactoringProcessors.forEach {
    processorEvent.addComponentInfo(it.getComponentInfo())
  }

  val studioEvent = AndroidStudioEvent.newBuilder()
    .setCategory(PROJECT_SYSTEM).setKind(UPGRADE_ASSISTANT_PROCESSOR_EVENT).withProjectId(project)
    .setUpgradeAssistantProcessorEvent(processorEvent.build())

  UsageTracker.log(studioEvent)
}

private fun AgpUpgradeComponentRefactoringProcessor.trackComponentUsage(kind: UpgradeAssistantEventKind, usages: Int) {
  val componentEvent = UpgradeAssistantComponentEvent.newBuilder()
    .setUpgradeUuid(uuid)
    .setCurrentAgpVersion(current.toString()).setNewAgpVersion(new.toString())
    .setComponentInfo(getComponentInfo().build())
    .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(kind).setUsages(usages).build())
    .build()
  val studioEvent = AndroidStudioEvent.newBuilder()
    .setCategory(PROJECT_SYSTEM).setKind(UPGRADE_ASSISTANT_COMPONENT_EVENT).withProjectId(project)
    .setUpgradeAssistantComponentEvent(componentEvent)

  UsageTracker.log(studioEvent)
}
