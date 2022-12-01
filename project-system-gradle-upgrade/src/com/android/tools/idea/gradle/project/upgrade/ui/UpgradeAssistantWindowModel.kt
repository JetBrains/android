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
package com.android.tools.idea.gradle.project.upgrade.ui

import com.android.ide.common.repository.AgpVersion
import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.adtui.model.stdui.EditingErrorCategory
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.AndroidGradleProjectSettingsControlBuilder
import com.android.tools.idea.gradle.project.GradleVersionCatalogDetector
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentRefactoringProcessor
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeRefactoringProcessor
import com.android.tools.idea.gradle.project.upgrade.GradlePluginsRefactoringProcessor
import com.android.tools.idea.gradle.project.upgrade.Java8DefaultRefactoringProcessor
import com.android.tools.idea.gradle.project.upgrade.LOG_CATEGORY
import com.android.tools.idea.gradle.project.upgrade.ProjectJdkRefactoringProcessor
import com.android.tools.idea.gradle.project.upgrade.ProjectJdkRefactoringProcessor.NewJdkInfo
import com.android.tools.idea.gradle.project.upgrade.R8FullModeDefaultRefactoringProcessor
import com.android.tools.idea.gradle.project.upgrade.RefactoringProcessorInstantiator
import com.android.tools.idea.gradle.project.upgrade.WellKnownGradlePluginDependencyUsageInfo
import com.android.tools.idea.gradle.project.upgrade.WellKnownGradlePluginDslUsageInfo
import com.android.tools.idea.gradle.project.upgrade.computeGradlePluginUpgradeState
import com.android.tools.idea.gradle.project.upgrade.isCleanEnoughProject
import com.android.tools.idea.gradle.project.upgrade.trackProcessorUsage
import com.android.tools.idea.gradle.project.upgrade.versionsAreIncompatible
import com.android.tools.idea.gradle.repositories.IdeGoogleMavenRepository
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo
import com.intellij.history.Label
import com.intellij.history.LocalHistory
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkVersionUtil
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.roots.ui.configuration.SdkComboBox
import com.intellij.openapi.roots.ui.configuration.SdkComboBoxModel
import com.intellij.openapi.roots.ui.configuration.SdkListItem
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.CheckboxTreeHelper
import com.intellij.ui.CheckboxTreeListener
import com.intellij.ui.CheckedTreeNode
import com.intellij.util.ui.UIUtil
import java.awt.event.ActionEvent
import java.util.function.Predicate
import javax.swing.AbstractAction
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

private val LOG = Logger.getInstance(LOG_CATEGORY)

// "Model" here loosely in the sense of Model-View-Controller
class UpgradeAssistantWindowModel(
  val project: Project,
  val currentVersionProvider: () -> AgpVersion?,
  var recommended: AgpVersion? = null,
  val knownVersionsRequester: () -> Set<AgpVersion> = { IdeGoogleMavenRepository.getAgpVersions() }
) : GradleSyncListener, Disposable {

  val latestKnownVersion = AgpVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())

  var current: AgpVersion? = currentVersionProvider()
    private set
  private var _selectedVersion: AgpVersion? = recommended ?: latestKnownVersion
  val selectedVersion: AgpVersion?
    get() = _selectedVersion
  var processor: AgpUpgradeRefactoringProcessor? = null
  var beforeUpgradeFilesStateLabel: Label? = null

  val uiState = ObjectValueProperty<UIState>(UIState.Loading)
  val uiRefreshNotificationTimestamp = ObjectValueProperty<Long>(0L)

  enum class Severity(val icon: Icon) {
    ERROR(AllIcons.General.Error),
    WARNING(AllIcons.General.Warning),
  }

  data class StatusMessage(
    val severity: Severity,
    val text: String,
    val url: String? = null
  )

  sealed class UIState{
    protected abstract val controlsEnabledState: ControlsEnabledState
    val runEnabled: Boolean
      get() = controlsEnabledState.runEnabled
    val showPreviewEnabled: Boolean
      get() = controlsEnabledState.showPreviewEnabled
    val comboEnabled: Boolean
      get() = controlsEnabledState.comboEnabled
    protected abstract val layoutState: LayoutState
    val showLoadingState: Boolean
      get() = layoutState.showLoadingState
    val showTree: Boolean
      get() = layoutState.showTree
    abstract val runTooltip: String
    open val loadingText: String = ""
    open val statusMessage: StatusMessage? = null

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is UIState) return false
      if (other::class !== this::class) return false

      if (controlsEnabledState != other.controlsEnabledState) return false
      if (layoutState != other.layoutState) return false
      if (runTooltip != other.runTooltip) return false
      if (loadingText != other.loadingText) return false
      if (statusMessage != other.statusMessage) return false

      return true
    }

    override fun hashCode(): Int {
      var result = controlsEnabledState.hashCode()
      result = 31 * result + layoutState.hashCode()
      result = 31 * result + runTooltip.hashCode()
      result = 31 * result + loadingText.hashCode()
      result = 31 * result + (statusMessage?.hashCode() ?: 0)
      return result
    }

    object ReadyToRun : UIState() {
      override val controlsEnabledState = ControlsEnabledState.BOTH
      override val layoutState = LayoutState.READY
      override val runTooltip = ""
    }
    object Blocked : UIState() {
      override val controlsEnabledState = ControlsEnabledState.NO_RUN_BUT_PREVIEW
      override val layoutState = LayoutState.READY
      override val runTooltip = "Upgrade Blocked"
    }
    object NoStepsSelected : UIState() {
      override val controlsEnabledState = ControlsEnabledState.NO_RUN
      override val layoutState = LayoutState.READY
      override val runTooltip = "No Steps Selected"
    }
    object Loading : UIState() {
      override val controlsEnabledState = ControlsEnabledState.NEITHER
      override val layoutState = LayoutState.LOADING
      override val runTooltip = ""
      override val loadingText = "Loading"
    }
    object RunningUpgrade : UIState() {
      override val controlsEnabledState = ControlsEnabledState.NEITHER
      override val layoutState = LayoutState.LOADING
      override val runTooltip = ""
      override val loadingText = "Running Upgrade"
    }
    object RunningSync : UIState() {
      override val controlsEnabledState = ControlsEnabledState.NEITHER
      override val layoutState = LayoutState.LOADING
      override val runTooltip = ""
      override val loadingText = "Running Sync"
    }
    object RunningUpgradeSync : UIState() {
      override val controlsEnabledState = ControlsEnabledState.NEITHER
      override val layoutState = LayoutState.LOADING
      override val runTooltip = ""
      override val loadingText = "Running Sync"
    }
    object AllDone : UIState() {
      override val controlsEnabledState = ControlsEnabledState.NO_RUN
      override val layoutState = LayoutState.HIDE_TREE
      override val runTooltip = "Nothing to do for this upgrade."
    }
    object ProjectFilesNotCleanWarning : UIState() {
      override val controlsEnabledState = ControlsEnabledState.BOTH
      override val layoutState = LayoutState.READY
      override val loadingText = ""
      override val statusMessage = StatusMessage(Severity.WARNING, "Uncommitted changes in build files.")
      override val runTooltip = "There are uncommitted changes in project build files.  Before upgrading, " +
                                 "you should commit or revert changes to the build files so that changes from the upgrade process " +
                                 "can be handled separately."
    }
    object ProjectUsesVersionCatalogs : UIState() {
      override val controlsEnabledState = ControlsEnabledState.BOTH
      override val layoutState = LayoutState.READY
      override val loadingText = ""
      override val statusMessage = StatusMessage(Severity.WARNING, "Project uses Gradle Version Catalogs.")
      override val runTooltip = "This project uses Gradle Version Catalogs in its build definition.  Some AGP Upgrade Assistant " +
                                "functionality may not work as expected."
    }
    class InvalidVersionError(
      override val statusMessage: StatusMessage
    ) : UIState() {
      override val controlsEnabledState = ControlsEnabledState.NO_RUN
      override val layoutState = LayoutState.READY
      override val runTooltip: String
        get() = statusMessage.text
    }
    class CaughtException(
      val errorMessage: String
    ): UIState() {
      override val controlsEnabledState = ControlsEnabledState.NEITHER
      override val layoutState = LayoutState.HIDE_TREE
      override val statusMessage: StatusMessage = StatusMessage(Severity.ERROR, errorMessage.lines().first())
      override val runTooltip: String
        get() = statusMessage.text
    }
    class UpgradeSyncFailed(
      val errorMessage: String
    ): UIState() {
      override val controlsEnabledState = ControlsEnabledState.NEITHER
      override val layoutState = LayoutState.HIDE_TREE
      override val statusMessage: StatusMessage = StatusMessage(Severity.ERROR, errorMessage.lines().first())
      override val runTooltip: String
        get() = statusMessage.text
    }
    object UpgradeSyncSucceeded : UIState() {
      override val controlsEnabledState = ControlsEnabledState.NEITHER
      override val layoutState = LayoutState.HIDE_TREE
      override val runTooltip = ""
    }

    enum class ControlsEnabledState(val runEnabled: Boolean, val showPreviewEnabled: Boolean, val comboEnabled: Boolean) {
      BOTH(true, true, true),
      NO_RUN(false, false, true),
      NO_RUN_BUT_PREVIEW(false, true, true),
      NEITHER(false, false, false),
    }

    enum class LayoutState(val showLoadingState: Boolean, val showTree: Boolean) {
      READY(false, true),
      LOADING(true, false),
      HIDE_TREE(false, false),
    }
  }

  val knownVersions = OptionalValueProperty<Set<AgpVersion>>()
  val suggestedVersions = OptionalValueProperty<List<AgpVersion>>()

  val treeModel = DefaultTreeModel(CheckedTreeNode(null))

  val checkboxTreeStateUpdater = object : CheckboxTreeListener {
    override fun nodeStateChanged(node: CheckedTreeNode) {
      fun findNecessityNode(necessity: AgpUpgradeComponentNecessity): CheckedTreeNode? =
        (treeModel.root as CheckedTreeNode).children().asSequence().firstOrNull { (it as CheckedTreeNode).userObject == necessity } as? CheckedTreeNode

      fun enableNode(node: CheckedTreeNode) {
        node.isEnabled = true
        node.children().asSequence().forEach { (it as? CheckedTreeNode)?.isEnabled = true }
      }

      fun disableNode(node: CheckedTreeNode) {
        node.isEnabled = false
        node.children().asSequence().forEach { (it as? CheckedTreeNode)?.isEnabled = false }
      }

      fun allChildrenChecked(node: CheckedTreeNode) = node.children().asSequence().all { (it as? CheckedTreeNode)?.isChecked ?: true }
      fun anyChildrenChecked(node: CheckedTreeNode) = node.children().asSequence().any { (it as? CheckedTreeNode)?.isChecked ?: true }
      // We change the enabled states of nodes in the nodeStateChanged calls for the leaves (where the parents are the necessities) so
      // that we can largely ignore issues of checked state propagation; tempting though it is to do this for the state changes on the
      // necessity nodes themselves, it's not possible to get it right, because we can't tell whether we are deselecting a node because
      // of an explicit user action on that node or a propagated deselection of a child, and selecting a child node does not cause a
      // state change in the parent directly in any case.
      val parentNode = (node.parent as? CheckedTreeNode)?.also { if (it.userObject !is AgpUpgradeComponentNecessity) return } ?: return
      // The MANDATORY_CODEPENDENT node is special in two ways:
      // - its children's checkboxes are always disabled;
      // - it acts as a gateway for the other two necessities mentioned here: if it's enabled, then OPTIONAL_CODEPENDENT processors may
      //   be selected; if it's disabled, then MANDATORY_INDEPENDENT processors may be deselected.
      when (parentNode.userObject) {
        AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT -> findNecessityNode(AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT)?.let { it.isEnabled = allChildrenChecked(parentNode) }
        AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT -> {
          findNecessityNode(AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT)?.let { if (node.isChecked) disableNode(it) else enableNode(it) }
          findNecessityNode(AgpUpgradeComponentNecessity.OPTIONAL_CODEPENDENT)?.let { if (node.isChecked) enableNode(it) else disableNode(it) }
        }
        AgpUpgradeComponentNecessity.OPTIONAL_CODEPENDENT -> findNecessityNode(AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT)?.let { it.isEnabled = !anyChildrenChecked(parentNode) }
      }
      recheckBlockageState()
    }
  }

  private var processorRequestedSync: Boolean = false

  init {
    Disposer.register(project, this)
    refresh()

    GradleSyncState.subscribe(project, this, this)
    // Initialize known versions (e.g. in case of offline work with no cache)
    suggestedVersions.value = suggestedVersionsList(setOf())

    // Request known versions.
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Looking for known versions", false) {
      override fun run(indicator: ProgressIndicator) {
        knownVersions.value = knownVersionsRequester()
        val suggestedVersionsList = suggestedVersionsList(knownVersions.value)
        invokeLater(ModalityState.NON_MODAL) { suggestedVersions.value = suggestedVersionsList }
      }
    })
  }

  override fun syncStarted(project: Project) =
    when(processorRequestedSync) {
      true -> uiState.set(UIState.RunningUpgradeSync)
      false -> uiState.set(UIState.RunningSync)
    }
  override fun syncFailed(project: Project, errorMessage: String) = syncFinished(success = false, errorMessage = errorMessage)
  override fun syncSucceeded(project: Project) = syncFinished()
  override fun syncSkipped(project: Project) = syncFinished()

  private fun syncFinished(success: Boolean = true, errorMessage: String = "") {
    when {
      !processorRequestedSync -> uiState.set(UIState.Loading).also { refresh(true) }
      success -> uiState.set(UIState.UpgradeSyncSucceeded).also { processorRequestedSync = false }
      else -> uiState.set(UIState.UpgradeSyncFailed(errorMessage)).also { processorRequestedSync = false }
    }
  }

  override fun dispose() {
    processor?.usageView?.close()
  }

  fun editingValidation(value: String?): Pair<EditingErrorCategory, String> {
    val parsed = value?.let { AgpVersion.tryParse(it) }
    val current = current
    return when {
      current == null -> Pair(EditingErrorCategory.ERROR, "Unknown current AGP version.")
      parsed == null -> Pair(EditingErrorCategory.ERROR, "Invalid AGP version format.")
      parsed < current -> Pair(EditingErrorCategory.ERROR, "Cannot downgrade AGP version.")
      parsed > latestKnownVersion ->
        if (parsed.major > latestKnownVersion.major)
          Pair(EditingErrorCategory.ERROR, "Target AGP version is unsupported.")
        else
          Pair(EditingErrorCategory.WARNING, "Upgrade to target AGP version is unverified.")

      else -> EDITOR_NO_ERROR
    }
  }

  fun newVersionSet(newVersionString: String) {
    val status = editingValidation(newVersionString)
    _selectedVersion = if (status.first == EditingErrorCategory.ERROR) {
      uiState.set(UIState.InvalidVersionError(StatusMessage(Severity.ERROR, status.second)))
      null
    }
    else {
      AgpVersion.tryParse(newVersionString)
    }
    refresh()
  }

  fun suggestedVersionsList(gMavenVersions: Set<AgpVersion>): List<AgpVersion> = gMavenVersions
    // Make sure the current (if known), recommended, and latest known versions are present, whether published or not
    .union(listOfNotNull(current, recommended, latestKnownVersion))
    // Keep only versions that are later than or equal to current
    .filter { current?.let { current -> it >= current } ?: false }
    // Keep only versions that are no later than the latest version we support
    .filter { it <= latestKnownVersion }
    // Do not keep versions that would force an upgrade from on sync
    .filter { !versionsAreIncompatible(it, latestKnownVersion) }
    .toList()
    .sortedDescending()

  fun refresh(refindPlugin: Boolean = false) {
    val oldState = uiState.get()
    uiState.set(UIState.Loading)
    // First clear some state
    val root = (treeModel.root as CheckedTreeNode)
    root.removeAllChildren()
    treeModel.nodeStructureChanged(root)
    processor?.usageView?.close()
    processor = null

    if (refindPlugin) {
      current = currentVersionProvider()?.also { current ->
        recommended = computeGradlePluginUpgradeState(current, latestKnownVersion, knownVersions.valueOrNull ?: setOf()).target
      }
      suggestedVersions.value = suggestedVersionsList(knownVersions.valueOrNull ?: setOf())
    }
    val newVersion = selectedVersion
    // TODO(xof/mlazeba): should we somehow preserve the existing uuid of the processor?
    val newProcessor = newVersion?.let {
      current?.let { current ->
        if (newVersion >= current && !project.isDisposed)
          project.getService(RefactoringProcessorInstantiator::class.java).createProcessor(project, current, it)
        else
          null
      }
    }

    if (newProcessor == null) {
      // Preserve existing message and run button tooltips from newVersion validation.
      uiState.set(oldState)
    }
    else {
      newProcessor.showBuildOutputOnSyncFailure = false
      newProcessor.syncRequestCallback = { processorRequestedSync = true }
      newProcessor.previewExecutedCallback = {
        ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.run { if (isAvailable) show() }
      }
      newProcessor.backFromPreviewAction = object : AbstractAction(UIUtil.replaceMnemonicAmpersand("&Back")) {
        override fun actionPerformed(e: ActionEvent?) {
          ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.run { if (isAvailable) show() }
        }
      }
      val application = ApplicationManager.getApplication()
      if (application.isUnitTestMode) {
        parseAndSetEnabled(newProcessor)
      } else {
        application.executeOnPooledThread { parseAndSetEnabled(newProcessor) }
      }
    }
  }

  private fun parseAndSetEnabled(newProcessor: AgpUpgradeRefactoringProcessor) {
    val application = ApplicationManager.getApplication()
    newProcessor.ensureParsedModels()
    val projectFilesClean = isCleanEnoughProject(project)
    val versionCatalogs = GradleVersionCatalogDetector.getInstance(project).isVersionCatalogProject
    if (application.isUnitTestMode) {
      setEnabled(newProcessor, projectFilesClean, versionCatalogs)
    } else {
      DumbService.getInstance(newProcessor.project).smartInvokeLater {
        setEnabled(newProcessor, projectFilesClean, versionCatalogs)
      }
    }
  }

  private fun setEnabled(newProcessor: AgpUpgradeRefactoringProcessor, projectFilesClean: Boolean, versionCatalogs: Boolean) {
    refreshTree(newProcessor)
    processor = newProcessor
    if (processorsForCheckedPresentations().any { it.isBlocked }) {
      newProcessor.trackProcessorUsage(UpgradeAssistantEventInfo.UpgradeAssistantEventKind.BLOCKED)
      // at this stage, agpVersionRefactoringProcessor will always be enabled.
      if (newProcessor.agpVersionRefactoringProcessor.isBlocked) {
        newProcessor.trackProcessorUsage(UpgradeAssistantEventInfo.UpgradeAssistantEventKind.FAILURE_PREDICTED)
      }
      uiState.set(UIState.Blocked)
    }
    else if (!projectFilesClean) {
      uiState.set(UIState.ProjectFilesNotCleanWarning)
    }
    else if ((treeModel.root as? CheckedTreeNode)?.childCount == 0) {
      uiState.set(UIState.AllDone)
    }
    else if (versionCatalogs) {
      uiState.set(UIState.ProjectUsesVersionCatalogs)
    }
    else {
      uiState.set(UIState.ReadyToRun)
    }
  }

  private fun refreshTree(processor: AgpUpgradeRefactoringProcessor) {
    val root = treeModel.root as CheckedTreeNode
    if (root.childCount > 0) {
      // this can happen if we call refresh() twice in quick succession: both refreshes clear the tree before either
      // gets here.
      root.removeAllChildren()
    }
    fun <T : DefaultMutableTreeNode> populateNecessity(
      necessity: AgpUpgradeComponentNecessity,
      constructor: (Any) -> (T)
    ): CheckedTreeNode {
      val node = CheckedTreeNode(necessity)
      processor.activeComponentsForNecessity(necessity).forEach { component -> node.add(constructor(toStepPresentation(component))) }
      node.let { if (it.childCount > 0) root.add(it) }
      return node
    }
    populateNecessity(AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT) { o -> CheckedTreeNode(o).also { it.isEnabled = false } }.isEnabled = false
    populateNecessity(AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT) { o -> CheckedTreeNode(o).also { it.isEnabled = false } }
    populateNecessity(AgpUpgradeComponentNecessity.OPTIONAL_CODEPENDENT) { o -> CheckedTreeNode(o).also { it.isChecked = false } }
    val hasAnyNodes = root.childCount > 0
    populateNecessity(AgpUpgradeComponentNecessity.OPTIONAL_INDEPENDENT) { o -> CheckedTreeNode(o).also { it.isChecked = !hasAnyNodes } }
    treeModel.nodeStructureChanged(root)
  }

  private fun processorsForCheckedPresentations(): List<AgpUpgradeComponentRefactoringProcessor> = processor?.let {
    CheckboxTreeHelper.getCheckedNodes(DefaultStepPresentation::class.java, null, treeModel)
      .map { it.processor }
  } ?: listOf()

  fun recheckBlockageState() {
    processorsForCheckedPresentations().let { processors ->
      when {
        processors.isEmpty() -> uiState.set(UIState.NoStepsSelected)
        processors.any { it.isBlocked } -> uiState.set(UIState.Blocked)
        else -> uiState.set(UIState.ReadyToRun)
      }
    }
  }

  fun notifyUiNeedsToRefresh() {
    uiRefreshNotificationTimestamp.set(System.currentTimeMillis())
  }

  fun runUpgrade(showPreview: Boolean) = processor?.let { processor ->
    if (!showPreview) uiState.set(UIState.RunningUpgrade)
    processor.componentRefactoringProcessors.forEach { it.isEnabled = false }
    processorsForCheckedPresentations().forEach { it.isEnabled = true }

    val runnable = {
      try {
        beforeUpgradeFilesStateLabel = LocalHistory.getInstance().putSystemLabel(project, "Before upgrade to ${processor.new}")
        processor.run()
      }
      catch (e: Exception) {
        processor.trackProcessorUsage(UpgradeAssistantEventInfo.UpgradeAssistantEventKind.INTERNAL_ERROR)
        uiState.set(UIState.CaughtException(e.message ?: "Unknown error"))
      }
    }

    if (ApplicationManager.getApplication().isUnitTestMode) {
      runnable.invoke()
    }
    else {
      DumbService.getInstance(processor.project).smartInvokeLater {
        processor.usageView?.close()
        processor.setPreviewUsages(showPreview)
        runnable.invoke()
      }
    }
  }

  fun runRevert() {
    try {
      val rollback = {
        // TODO (mlazeba/xof): baseDir is deprecated, how can we avoid it here?
        beforeUpgradeFilesStateLabel!!.revert(project, project.getBaseDir())
      }
      ProgressManager.getInstance()
        .runProcessWithProgressSynchronously(rollback, "Revert to pre-upgrade state\u2026", true, project)
      GradleSyncInvoker.getInstance()
        .requestProjectSync(project, GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_AGP_VERSION_UPDATE_ROLLED_BACK))
    }
    catch (e: Exception) {
      uiState.set(UIState.CaughtException(e.message ?: "Unknown error during revert."))
      processor?.trackProcessorUsage(UpgradeAssistantEventInfo.UpgradeAssistantEventKind.INTERNAL_ERROR)
      LOG.error("Error during revert.", e)
    }
  }

  interface StepUiPresentation {
    val pageHeader: String
    val treeText: String
    val helpLinkUrl: String?
    val shortDescription: String?
    val additionalInfo: String?
      get() = null
    val isBlocked: Boolean
    val blockReasons: List<AgpUpgradeComponentRefactoringProcessor.BlockReason>
  }

  interface StepUiWithUserSelection {
    fun createUiSelector(): JComponent
  }

  interface StepUiWithComboSelectorPresentation {
    val label: String
    val elements: List<Any>
    var selectedValue: Any
  }

  // TODO(mlazeba/xof): temporary here, need to be defined in processor itself probably
  private fun toStepPresentation(processor: AgpUpgradeComponentRefactoringProcessor) = when (processor) {
    is Java8DefaultRefactoringProcessor -> object : DefaultStepPresentation(processor), StepUiWithComboSelectorPresentation {
      override val label: String = "Action on no explicit Java language level: "
      override val pageHeader: String
        get() = processor.commandName
      override val treeText: String
        get() = processor.noLanguageLevelAction.toString()
      override val elements: List<Java8DefaultRefactoringProcessor.NoLanguageLevelAction>
        get() = listOf(
          Java8DefaultRefactoringProcessor.NoLanguageLevelAction.ACCEPT_NEW_DEFAULT,
          Java8DefaultRefactoringProcessor.NoLanguageLevelAction.INSERT_OLD_DEFAULT
        )
      override var selectedValue: Any
        get() = processor.noLanguageLevelAction
        set(value) {
          if (value is Java8DefaultRefactoringProcessor.NoLanguageLevelAction) processor.noLanguageLevelAction = value
        }
      init {
        selectedValue = Java8DefaultRefactoringProcessor.NoLanguageLevelAction.ACCEPT_NEW_DEFAULT
      }
    }
    is R8FullModeDefaultRefactoringProcessor -> object : DefaultStepPresentation(processor), StepUiWithComboSelectorPresentation {
      override val label: String = "Action on no android.enableR8.fullMode property: "
      override val pageHeader: String
        get() = processor.commandName
      override val treeText: String
        get() = processor.noPropertyPresentAction.toString()
      override val elements: List<R8FullModeDefaultRefactoringProcessor.NoPropertyPresentAction>
        get() = listOf(
          R8FullModeDefaultRefactoringProcessor.NoPropertyPresentAction.ACCEPT_NEW_DEFAULT,
          R8FullModeDefaultRefactoringProcessor.NoPropertyPresentAction.INSERT_OLD_DEFAULT
        )
      override var selectedValue: Any
        get() = processor.noPropertyPresentAction
        set(value) {
          if (value is R8FullModeDefaultRefactoringProcessor.NoPropertyPresentAction) processor.noPropertyPresentAction = value
        }
      init {
        selectedValue = R8FullModeDefaultRefactoringProcessor.NoPropertyPresentAction.ACCEPT_NEW_DEFAULT
      }
    }
    is ProjectJdkRefactoringProcessor -> object : DefaultStepPresentation(processor), StepUiWithUserSelection {
      val comboBox: SdkComboBox

      init {
        fun setupProjectSdksModel(sdksModel: ProjectSdksModel, project: Project, projectSdk: Sdk?) {
          // TODO copied from AndroidGradleProjectSettingsControlBuilder.kt. If works - try to reuse instead.
          var resolvedProjectSdk = projectSdk
          sdksModel.reset(project)
          //TODO removed for now to test
          //deduplicateSdkNames(sdksModel)
          if (resolvedProjectSdk == null) {
            resolvedProjectSdk = sdksModel.projectSdk
            // Find real sdk
            // see ProjectSdksModel#getProjectSdk for details
            resolvedProjectSdk = sdksModel.findSdk(resolvedProjectSdk)
          }
          if (resolvedProjectSdk != null) {
            // resolves executable JDK
            // e.g: for Android projects
            resolvedProjectSdk = ExternalSystemJdkUtil.resolveDependentJdk(resolvedProjectSdk)
            // Find editable sdk
            // see ProjectSdksModel#getProjectSdk for details
            resolvedProjectSdk = sdksModel.findSdk(resolvedProjectSdk.name)
          }
          sdksModel.projectSdk = resolvedProjectSdk
          sdksModel.apply()
        }
        // Setting up the same way as in AndroidGradleProjectSettingsControlBuilder.kt
        val sdksModel: ProjectSdksModel = ProjectStructureConfigurable.getInstance(project).projectJdksModel

        setupProjectSdksModel(sdksModel, project, null)

        //TODO not sure why we need to 'hide' project sdk from the model.
        val projectJdk = sdksModel.projectSdk
        sdksModel.projectSdk = null
        val gradleJdkBoxModel = SdkComboBoxModel.createJdkComboBoxModel(project, sdksModel)
        sdksModel.projectSdk = projectJdk

        comboBox = SdkComboBox(gradleJdkBoxModel).apply { name = "selection" }
        comboBox.renderer = AndroidGradleProjectSettingsControlBuilder.SdkListPathPresenter { comboBox.model.listModel }
        processor.newJdkInfo?.let { newJdkInfo ->
          val sdkItem = comboBox.model.listModel.findSdkItem(newJdkInfo.sdk) ?: return@let
          comboBox.model.selectedItem = sdkItem
        }
        comboBox.addActionListener { e ->
          val item = comboBox.model.selectedItem
          (item as? SdkListItem.SdkItem)?.sdk?.let { sdk ->
            sdk.homePath?.let { homePath ->
              SdkVersionUtil.getJdkVersionInfo(homePath)?.version?.let { version ->
                processor.newJdkInfo = NewJdkInfo(sdk, homePath, version)
              }
            }
          } ?: run { processor.newJdkInfo = null }
          notifyUiNeedsToRefresh()
          recheckBlockageState()
        }
      }

      override fun createUiSelector(): JComponent = comboBox

    }
    is GradlePluginsRefactoringProcessor -> object : DefaultStepPresentation(processor) {
      override val additionalInfo =
        processor.cachedUsages
          .filter { it is WellKnownGradlePluginDependencyUsageInfo || it is WellKnownGradlePluginDslUsageInfo }
          .map { it.tooltipText }.toSortedSet().takeIf { !it.isEmpty() }?.run {
             val result = StringBuilder()
            result.append("<p>The following Gradle plugin versions will be updated:</p>\n")
            result.append("<ul>\n")
            forEach { result.append("<li>$it</li>\n") }
            result.append("</ul>\n")
            result.toString()
          }
    }
    else -> DefaultStepPresentation(processor)
  }

  open class DefaultStepPresentation(val processor: AgpUpgradeComponentRefactoringProcessor) : StepUiPresentation {
    override val pageHeader: String
      get() = treeText
    override val treeText: String
      get() = processor.commandName
    override val helpLinkUrl: String?
      get() = processor.getReadMoreUrl()
    override val shortDescription: String?
      get() = processor.getShortDescription()
    override val isBlocked: Boolean
      get() = processor.isBlocked
    override val blockReasons: List<AgpUpgradeComponentRefactoringProcessor.BlockReason>
      get() = processor.blockProcessorReasons()
  }
}

private fun AgpUpgradeRefactoringProcessor.activeComponentsForNecessity(necessity: AgpUpgradeComponentNecessity) =
  this.componentRefactoringProcessorsWithAgpVersionProcessorLast
    .filter { it.isEnabled }
    .filter { it.necessity() == necessity }
    .filter { !it.isAlwaysNoOpForProject || it.isBlocked }