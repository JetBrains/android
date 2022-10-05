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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.GradleVersion.AgpVersion
import com.android.tools.adtui.HtmlLabel
import com.android.tools.adtui.HtmlLabel.setUpAsHtmlLabel
import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.adtui.model.stdui.CommonComboBoxModel
import com.android.tools.adtui.model.stdui.DefaultCommonComboBoxModel
import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.adtui.model.stdui.EditingErrorCategory
import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.adtui.model.stdui.EditingValidation
import com.android.tools.adtui.model.stdui.EditorCompletion
import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.adtui.stdui.CommonTextField
import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.adtui.stdui.registerActionKey
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.GradleVersionCatalogDetector
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentRefactoringProcessor.BlockReason
import com.android.tools.idea.gradle.repositories.IdeGoogleMavenRepository
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo
import com.intellij.build.BuildContentManager
import com.intellij.history.Label
import com.intellij.history.LocalHistory
import com.intellij.history.integration.LocalHistoryImpl
import com.intellij.history.integration.ui.views.DirectoryHistoryDialog
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckboxTreeHelper
import com.intellij.ui.CheckboxTreeListener
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeModelAdapter
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.JTree
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.TreeModelEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

private val LOG = Logger.getInstance(LOG_CATEGORY)

// "Model" here loosely in the sense of Model-View-Controller
class ToolWindowModel(
  val project: Project,
  val currentVersionProvider: () -> AgpVersion?,
  val recommended: AgpVersion? = null,
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
      processorsForCheckedPresentations().let { processors ->
        when {
          processors.isEmpty() -> uiState.set(UIState.NoStepsSelected)
          processors.any { it.isBlocked } -> uiState.set(UIState.Blocked)
          else -> uiState.set(UIState.ReadyToRun)
        }
      }
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
      current = currentVersionProvider()
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
      invokeLater(ModalityState.NON_MODAL) { setEnabled(newProcessor, projectFilesClean, versionCatalogs) }
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
    val blockReasons: List<BlockReason>
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
    override val blockReasons: List<BlockReason>
      get() = processor.blockProcessorReasons()
  }
}

class ContentManagerImpl(val project: Project): ContentManager {
  init {
    ApplicationManager.getApplication().invokeAndWait {
      // Force EDT here to ease the testing (see com.intellij.ide.plugins.CreateAllServicesAndExtensionsAction: it instantiates services
      //  on a background thread). There is no performance penalties when already invoked on EDT.
      ToolWindowManager.getInstance(project).registerToolWindow(
        RegisterToolWindowTask.closable(TOOL_WINDOW_ID, icons.GradleIcons.ToolWindowGradle)
      )
    }
  }

  override fun showContent(recommended: AgpVersion?) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    toolWindow.contentManager.removeAllContents(true)
    val model = ToolWindowModel(
      project, currentVersionProvider = { AndroidPluginInfo.find(project)?.pluginVersion }, recommended = recommended
    )
    val view = View(model, toolWindow.contentManager)
    val content = ContentFactory.getInstance().createContent(view.content, model.current.contentDisplayName(), true)
    content.setDisposer(model)
    content.isPinned = true
    toolWindow.contentManager.addContent(content)
    toolWindow.show()
  }

  class View(val model: ToolWindowModel, contentManager: com.intellij.ui.content.ContentManager) {
    private val myListeners = ListenerManager()

    val treePanel = JBPanel<JBPanel<*>>(BorderLayout())

    val detailsPanel = JBPanel<JBPanel<*>>().apply {
      layout = VerticalLayout(0, SwingConstants.LEFT)
      border = JBUI.Borders.empty(20)
      myListeners.listen(this@View.model.uiState) { refreshDetailsPanel() }
    }

    val tree: CheckboxTree = CheckboxTree(UpgradeAssistantTreeCellRenderer(), null)

    init {
      treePanel.apply {
        add(ScrollPaneFactory.createScrollPane(tree, SideBorder.NONE), BorderLayout.WEST)
        add(JSeparator(SwingConstants.VERTICAL), BorderLayout.CENTER)
      }

      tree.apply {
        model = this@View.model.treeModel
        isRootVisible = false
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        addCheckboxTreeListener(this@View.model.checkboxTreeStateUpdater)
        addTreeSelectionListener { e -> refreshDetailsPanel() }
        background = primaryContentBackground
        isOpaque = true
        fun update(uiState: ToolWindowModel.UIState) {
          isEnabled = !uiState.showLoadingState
          treePanel.isVisible = uiState.showTree
          if (!uiState.showTree) {
            selectionModel.clearSelection()
            refreshDetailsPanel()
          }
        }
        update(this@View.model.uiState.get())
        myListeners.listen(this@View.model.uiState, ::update)
      }
    }

    val upgradeLabel = JBLabel(model.current.upgradeLabelText()).also { it.border = JBUI.Borders.empty(0, 6) }

    val versionTextField = CommonComboBox<AgpVersion, CommonComboBoxModel<AgpVersion>>(
      // TODO this model needs to be enhanced to know when to commit value, instead of doing it in document listener below.
      object : DefaultCommonComboBoxModel<AgpVersion>(
        model.selectedVersion?.toString() ?: "",
        model.suggestedVersions.valueOrNull ?: emptyList()
      ) {
        init {
          selectedItem = model.selectedVersion
          myListeners.listen(model.suggestedVersions) { suggestedVersions ->
            val selectedVersion = model.selectedVersion
            for (i in size - 1 downTo 0) {
              if (getElementAt(i) != selectedVersion) removeElementAt(i)
            }
            suggestedVersions.orElse(emptyList()).forEachIndexed { i, it ->
              when {
                selectedVersion == null -> addElement(it)
                it > selectedVersion -> insertElementAt(it, i)
                it == selectedVersion -> Unit
                else -> addElement(it)
              }
            }
            selectedItem = selectedVersion
          }
          placeHolderValue = "Select new version"
        }

        // Given the ComponentValidator installation below, one might expect this not to be necessary,
        // but the outline highlighting does not work without it.
        // This is happening because not specifying validation here does not remove validation but just using default 'accept all' one.
        // This validation is triggered after the ComponentValidator and overrides the outline set by ComponentValidator.
        // The solution would be either add support of the tooltip to this component validation logic or use a different component.
        override val editingSupport = object : EditingSupport {
          override val validation: EditingValidation = model::editingValidation
          override val completion: EditorCompletion = { model.suggestedVersions.getValueOr(emptyList()).map { it.toString() }}
        }
      }
    ).apply {
      isEnabled = this@View.model.uiState.get().comboEnabled
      myListeners.listen(this@View.model.uiState) { uiState ->
        isEnabled = uiState.comboEnabled
      }

      // Need to register additional key listeners to the textfield that would hide main combo-box popup.
      // Otherwise textfield consumes these events without hiding popup making it impossible to do with a keyboard.
      val textField = editor.editorComponent as CommonTextField<*>
      textField.registerActionKey({ hidePopup(); textField.enterInLookup() }, KeyStrokes.ENTER, "enter")
      textField.registerActionKey({ hidePopup(); textField.escapeInLookup() }, KeyStrokes.ESCAPE, "escape")
      ComponentValidator(this@View.model).withValidator { ->
        val text = editor.item.toString()
        val validation = this@View.model.editingValidation(text)
        when (validation.first) {
          EditingErrorCategory.ERROR -> ValidationInfo(validation.second, this)
          EditingErrorCategory.WARNING -> ValidationInfo(validation.second, this).asWarning()
          else -> null
        }
      }.installOn(this)
      textField.document?.addDocumentListener(
        object: DocumentAdapter() {
          override fun textChanged(e: DocumentEvent) {
            ComponentValidator.getInstance(this@apply).ifPresent { v -> v.revalidate() }
            this@View.model.newVersionSet(editor.item.toString())
          }
        }
      )
    }

    val refreshButton = JButton("Refresh").apply {
      isEnabled = !this@View.model.uiState.get().showLoadingState
      myListeners.listen(this@View.model.uiState) { uiState ->
        isEnabled = !uiState.showLoadingState
      }
      addActionListener {
        this@View.model.run {
          refresh(true)
        }
      }
    }
    val okButton = JButton("Run selected steps").apply {
      addActionListener { this@View.model.runUpgrade(false) }
      this@View.model.uiState.get().let { uiState ->
        toolTipText = uiState.runTooltip
        isEnabled = uiState.runEnabled
      }
      myListeners.listen(this@View.model.uiState) { uiState ->
        toolTipText = uiState.runTooltip
        isEnabled = uiState.runEnabled
      }
      putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
    }
    val previewButton = JButton("Show Usages").apply {
      addActionListener { this@View.model.runUpgrade(true) }
      this@View.model.uiState.get().let { uiState ->
        toolTipText = uiState.runTooltip
        isEnabled = uiState.showPreviewEnabled
      }
      myListeners.listen(this@View.model.uiState) { uiState ->
        toolTipText = uiState.runTooltip
        isEnabled = uiState.showPreviewEnabled
      }
    }
    val messageLabel = JBLabel().apply {
      fun update(uiState: ToolWindowModel.UIState) {
        icon = uiState.statusMessage?.severity?.icon
        text = uiState.statusMessage?.text
      }
      update(this@View.model.uiState.get())
      myListeners.listen(this@View.model.uiState, ::update)
    }
    val hyperlinkLabel = object : ActionLink("Read more") {
      var url: String? = null
    }
      .apply {
        addActionListener { url?.let { BrowserUtil.browse(it) } }
        setExternalLinkIcon()
        fun update(uiState: ToolWindowModel.UIState) {
          url = uiState.statusMessage?.url
          isVisible = url != null
        }
        update(this@View.model.uiState.get())
        myListeners.listen(this@View.model.uiState, ::update)
      }

    val content = JBLoadingPanel(BorderLayout(), contentManager).apply {
      val controlsPanel = makeTopComponent()
      val topPanel = JBPanel<JBPanel<*>>().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(controlsPanel)
        add(JSeparator(SwingConstants.HORIZONTAL))
      }
      add(topPanel, BorderLayout.NORTH)
      add(treePanel, BorderLayout.WEST)
      add(detailsPanel, BorderLayout.CENTER)

      fun updateLoadingState(uiState: ToolWindowModel.UIState) {
        setLoadingText(uiState.loadingText)
        if (uiState.showLoadingState) {
          startLoading()
        }
        else {
          stopLoading()
          upgradeLabel.text = model.current.upgradeLabelText()
          contentManager.getContent(this)?.displayName = model.current.contentDisplayName()
        }
      }

      myListeners.listen(model.uiState, ::updateLoadingState)
      updateLoadingState(model.uiState.get())
    }

    init {
      model.treeModel.addTreeModelListener(object : TreeModelAdapter() {
        override fun treeStructureChanged(event: TreeModelEvent?) {
          // Tree expansion should not run in 'treeStructureChanged' as another listener clears the nodes expanded state
          // in the same event listener that is normally called after this one. Probably this state is cached somewhere else
          // making this diversion not immediately visible but on page hide and restore it uses all-folded state form the model.
          invokeLater(ModalityState.NON_MODAL) {
            tree.setHoldSize(false)
            TreeUtil.expandAll(tree) {
              tree.setHoldSize(true)
              content.revalidate()
            }
          }
        }
      })
      TreeUtil.expandAll(tree)
      tree.setHoldSize(true)
    }

    private fun makeTopComponent() = JBPanel<JBPanel<*>>().apply {
      // This layout, rather than com.intellij.ide.plugins.newui.HorizontalLayout (used elsewhere in ContentManager), is needed to make
      // the baseline of the hyperlinkLabel be aligned with the baselines of unstyled text in other elements.  It does not align the text
      // in the versionTextField combo box with this baseline, however; instead the borders of the combo are aligned with the borders of
      // the button.  Using GroupLayout (with BASELINE alignment) aligns all the text baselines, at the cost of misaligning the combo
      // borders; altering the combo's dimensions or insets somehow might allow complete unity.
      layout = HorizontalLayout(5)
      add(upgradeLabel, HorizontalLayout.LEFT)
      add(versionTextField, HorizontalLayout.LEFT)
      add(okButton, HorizontalLayout.LEFT)
      add(previewButton, HorizontalLayout.LEFT)
      add(refreshButton, HorizontalLayout.LEFT)
      add(messageLabel, HorizontalLayout.LEFT)
      add(hyperlinkLabel, HorizontalLayout.LEFT)
    }

    private fun refreshDetailsPanel() {
      detailsPanel.removeAll()
      val selectedStep = (tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode)?.userObject
      val uiState = this@View.model.uiState.get()
      val label = HtmlLabel().apply { name = "content" }
      setUpAsHtmlLabel(label)
      when {
        uiState is ToolWindowModel.UIState.CaughtException -> {
          val sb = StringBuilder()
          sb.append("<div><b>Caught exception</b></div>")
          sb.append("<p>Something went wrong (an internal exception occurred).  ")
          sb.append("You should revert to a known-good state before doing anything else.</p>")
          sb.append("<p>The status message is:<br/>")
          sb.append(uiState.statusMessage.text)
          sb.append("</p>")
          label.text = sb.toString()
          detailsPanel.add(label)
          detailsPanel.addRevertInfo(showRevertButton = false, markRevertAsDefault = false)
        }
        uiState is ToolWindowModel.UIState.UpgradeSyncFailed -> {
          val sb = StringBuilder()
          sb.append("<div><b>Sync Failed</b></div>")
          sb.append("<p>The project failed to sync with the IDE.  You should revert<br/>")
          sb.append("to a known-good state before making further changes.</p>")
          label.text = sb.toString()
          val layoutPanel = JPanel()
          layoutPanel.layout = BorderLayout()
          detailsPanel.add(layoutPanel)
          val realDetailsPanel = JBPanel<JBPanel<*>>().apply {
            layout = VerticalLayout(0, SwingConstants.LEFT)
            border = JBUI.Borders.empty(0, 0, 0, 20)
          }
          layoutPanel.add(realDetailsPanel, BorderLayout.WEST)
          val errorPanel = JBPanel<JBPanel<*>>().apply {
            layout = VerticalLayout(0, SwingConstants.LEFT)
            border = JBUI.Borders.empty(0, 0, 0, 0)
          }
          layoutPanel.add(errorPanel, BorderLayout.CENTER)
          realDetailsPanel.add(label)
          errorPanel.add(JBLabel("The error message from sync is:"))
          uiState.errorMessage.trimEnd().let { errorMessage ->
            val rows = minOf(errorMessage.lines().size, 10)
            JBTextArea(errorMessage, rows, 80).let { textArea ->
              textArea.isEditable = false
              JBScrollPane(textArea).run {
                errorPanel.add(this)
              }
            }
          }
          realDetailsPanel.addRevertInfo(showRevertButton = true, markRevertAsDefault = true)
          realDetailsPanel.addBuildWindowInfo()
        }
        uiState is ToolWindowModel.UIState.UpgradeSyncSucceeded -> {
          val sb = StringBuilder()
          sb.append("<div><b>Sync succeeded</b></div>")
          sb.append("<p>The upgraded project successfully synced with the IDE.  ")
          sb.append("You should test that the upgraded project builds and passes its tests successfully before making further changes.</p>")
          model.processor?.let { processor ->
            sb.append("<p>The upgrade consisted of the following steps:</p>")
            sb.append("<ul>")
            processor.componentRefactoringProcessors.filter { it.isEnabled }.forEach {
              sb.append("<li>${it.commandName}</li>")
            }
            sb.append("</ul>")
          }
          label.text = sb.toString()
          detailsPanel.add(label)
          detailsPanel.addRevertInfo(showRevertButton = true, markRevertAsDefault = false)
        }
        uiState is ToolWindowModel.UIState.AllDone -> {
          val sb = StringBuilder()
          sb.append("<div><b>Up-to-date for Android Gradle Plugin version ${this@View.model.current}</b></div>")
          if (this@View.model.current?.let { it < this@View.model.latestKnownVersion } == true) {
            sb.append("<p>Upgrades to newer versions of Android Gradle Plugin (up to ${this@View.model.latestKnownVersion}) can be")
            sb.append("<br>performed by selecting those versions from the dropdown.</p>")
          }
          label.text = sb.toString()
          detailsPanel.add(label)
        }
        selectedStep is AgpUpgradeComponentNecessity -> {
          label.text = "<div><b>${selectedStep.treeText()}</b></div><p>${selectedStep.description().replace("\n", "<br>")}</p>"
          detailsPanel.add(label)
        }
        selectedStep is ToolWindowModel.StepUiPresentation -> {
          val text = StringBuilder("<div><b>${selectedStep.pageHeader}</b></div>")
          val paragraph = selectedStep.helpLinkUrl != null || selectedStep.shortDescription != null
          if (paragraph) text.append("<p>")
          selectedStep.shortDescription?.let { description ->
            text.append(description.replace("\n", "<br>"))
            selectedStep.helpLinkUrl?.let { text.append("  ") }
          }
          selectedStep.helpLinkUrl?.let { url ->
            // TODO(xof): what if we end near the end of the line, and this sticks out in an ugly fashion?
            text.append("<a href='$url'>Read more</a><icon src='AllIcons.Ide.External_link_arrow'>.")
          }
          selectedStep.additionalInfo?.let { text.append(it) }
          if (selectedStep.isBlocked) {
            text.append("<br><br><div><b>This step is blocked</b></div>")
            text.append("<ul>")
            selectedStep.blockReasons.forEach { reason ->
              reason.shortDescription.let { text.append("<li>$it") }
              reason.description?.let { text.append("<br>${it.replace("\n", "<br>")}") }
              reason.readMoreUrl?.let { text.append("  <a href='${it.url}'>Read more</a><icon src='AllIcons.Ide.External_link_arrow'>.") }
              text.append("</li>")
            }
            text.append("</ul>")
          }
          label.text = text.toString()
          detailsPanel.add(label)
          if (selectedStep is ToolWindowModel.StepUiWithComboSelectorPresentation) {
            ComboBox(selectedStep.elements.toTypedArray()).apply {
              name = "selection"
              item = selectedStep.selectedValue
              addActionListener {
                selectedStep.selectedValue = this.item
                tree.repaint()
                refreshDetailsPanel()
              }
              val comboPanel = JBPanel<JBPanel<*>>()
              comboPanel.layout = com.intellij.ide.plugins.newui.HorizontalLayout(0)
              comboPanel.add(JBLabel(selectedStep.label).also { it.border = JBUI.Borders.empty(0, 4); it.name = "label" })
              comboPanel.add(this)
              detailsPanel.add(comboPanel)
            }
          }
        }
      }
      detailsPanel.revalidate()
      detailsPanel.repaint()
    }

    private fun JBPanel<JBPanel<*>>.addBuildWindowInfo() {
      JPanel().apply {
        name = "build window info panel"
        layout = VerticalLayout(0);
        border = JBUI.Borders.empty(10, 0, 0, 0)
        add(JBLabel("There may be more information about the sync failure in the"))
        ActionLink("'Build' window") {
          val project = this@View.model.project
          invokeLater {
            if (!project.isDisposed) {
              val buildContentManager = BuildContentManager.getInstance(project)
              val buildToolWindow = buildContentManager.getOrCreateToolWindow()
              if (!buildToolWindow.isAvailable) return@invokeLater
              buildToolWindow.show()
              val contentManager = buildToolWindow.contentManager
              contentManager.findContent("Sync")?.let { content -> contentManager.setSelectedContent(content) }
            }
          }
        }
          .apply { name = "open build window link" }
          .also { actionLink -> add(actionLink) }
      }
        .also { panel -> add(panel) }
    }

    private fun JBPanel<JBPanel<*>>.addRevertInfo(showRevertButton: Boolean, markRevertAsDefault: Boolean) {
      if (showRevertButton) {
        JButton("Revert Project Files")
          .apply {
            name = "revert project button"
            toolTipText = "Revert all project files to a state recorded just before running last upgrade."
            putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, markRevertAsDefault)
            addActionListener { this@View.model.runRevert() }
          }
          .also { revertButton -> add(revertButton) }
      }

      JPanel().apply {
        name = "revert information panel"
        layout = FlowLayout(FlowLayout.LEADING, 0, 0)
        border = JBUI.Borders.empty(20, 0, 0, 0)
        add(JBLabel("You can review the applied changes in the "))
        ActionLink("'Local History' dialog") {
          val ideaGateway = LocalHistoryImpl.getInstanceImpl().getGateway()
          // TODO (mlazeba/xof): baseDir is deprecated, how can we avoid it here? might be better to show RecentChangeDialog instead
          val dialog = DirectoryHistoryDialog(this@View.model.project, ideaGateway, this@View.model.project.baseDir)
          dialog.show()
        }
          .apply { name = "open local history link" }
          .also { actionLink -> add(actionLink) }
      }
        .also { panel -> add(panel) }

    }
  }

  private class UpgradeAssistantTreeCellRenderer : CheckboxTree.CheckboxTreeCellRenderer(true, true) {
    override fun customizeRenderer(tree: JTree?,
                                   value: Any?,
                                   selected: Boolean,
                                   expanded: Boolean,
                                   leaf: Boolean,
                                   row: Int,
                                   hasFocus: Boolean) {
      if (value is DefaultMutableTreeNode) {
        when (val o = value.userObject) {
          is AgpUpgradeComponentNecessity -> {
            textRenderer.append(o.treeText())
            myCheckbox.let { toolTipText = o.checkboxToolTipText(it.isEnabled, it.isSelected) }
          }
          is ToolWindowModel.StepUiPresentation -> {
            (value.parent as? DefaultMutableTreeNode)?.let { parent ->
              if (parent.userObject == AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT) {
                toolTipText = null
                myCheckbox.isVisible = false
                textRenderer.append("")
                val totalXoffset = myCheckbox.width + myCheckbox.margin.left + myCheckbox.margin.right
                val firstXoffset = 2 * myCheckbox.width / 5 // approximate padding needed to put the bullet centrally in the space
                textRenderer.appendTextPadding(firstXoffset)
                textRenderer.append("\u2022", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, true)
                // Although this looks wrong (one might expect e.g. `totalXoffset - firstXoffset`), it does seem to be the case that
                // SimpleColoredComponent interprets padding from the start of the extent, rather than from the previous end.  Of course this
                // might be a bug, and if the behaviour of SimpleColoredComponent is changed this will break alignment of the Upgrade steps.
                textRenderer.appendTextPadding(totalXoffset)
              }
              else {
                myCheckbox.let {
                  toolTipText = (parent.userObject as? AgpUpgradeComponentNecessity)?.let { n ->
                    n.checkboxToolTipText(it.isEnabled, it.isSelected)
                  }
                }
              }
            }
            textRenderer.append(o.treeText, SimpleTextAttributes.REGULAR_ATTRIBUTES, true)
            if ((o as? ToolWindowModel.StepUiPresentation)?.isBlocked == true) {
              textRenderer.icon = AllIcons.General.Error
              textRenderer.isIconOnTheRight = true
              textRenderer.iconTextGap = 10
            }
            else if (o is ToolWindowModel.StepUiWithComboSelectorPresentation) {
              textRenderer.icon = AllIcons.Actions.Edit
              textRenderer.isIconOnTheRight = true
              textRenderer.iconTextGap = 10
            }
          }
        }
      }
      super.customizeRenderer(tree, value, selected, expanded, leaf, row, hasFocus)
    }
  }
}

const val TOOL_WINDOW_ID = "Upgrade Assistant"

private fun AgpUpgradeRefactoringProcessor.activeComponentsForNecessity(necessity: AgpUpgradeComponentNecessity) =
  this.componentRefactoringProcessorsWithAgpVersionProcessorLast
    .filter { it.isEnabled }
    .filter { it.necessity() == necessity }
    .filter { !it.isAlwaysNoOpForProject || it.isBlocked }

fun AgpUpgradeComponentNecessity.treeText() = when (this) {
  AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT -> "Upgrade prerequisites"
  AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT -> "Upgrade"
  AgpUpgradeComponentNecessity.OPTIONAL_CODEPENDENT -> "Recommended post-upgrade steps"
  AgpUpgradeComponentNecessity.OPTIONAL_INDEPENDENT -> "Recommended steps"
  else -> {
    LOG.error("Irrelevant steps tree text requested")
    "Irrelevant steps"
  }
}

fun AgpUpgradeComponentNecessity.checkboxToolTipText(enabled: Boolean, selected: Boolean) =
  if (enabled) null
  else when (this to selected) {
    AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT to true -> "Cannot be deselected while ${AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT.treeText()} is selected"
    AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT to false -> "Cannot be selected while ${AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT.treeText()} is unselected"
    AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT to true -> "Cannot be deselected while ${AgpUpgradeComponentNecessity.OPTIONAL_CODEPENDENT.treeText()} is selected"
    AgpUpgradeComponentNecessity.OPTIONAL_CODEPENDENT to false -> "Cannot be selected while ${AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT.treeText()} is unselected"
    else -> {
      LOG.error("Irrelevant step tooltip text requested")
      null
    }
  }

fun AgpUpgradeComponentNecessity.description() = when (this) {
  AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT ->
    "These steps are required to perform the upgrade of this project.\n" +
    "You can choose to do them in separate steps, in advance of the Android\n" +
    "Gradle Plugin upgrade itself."
  AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT ->
    "These steps are required to perform the upgrade of this project.\n" +
    "They must all happen together, at the same time as the Android Gradle Plugin\n" +
    "upgrade itself."
  AgpUpgradeComponentNecessity.OPTIONAL_CODEPENDENT ->
    "These steps are not required to perform the upgrade of this project at this time,\n" +
    "but will be required when upgrading to a later version of the Android Gradle\n" +
    "Plugin.  You can choose to do them in this upgrade to prepare for the future, but\n" +
    "only if the Android Gradle Plugin is upgraded to its new version."
  AgpUpgradeComponentNecessity.OPTIONAL_INDEPENDENT ->
    "These steps are not required to perform the upgrade of this project at this time,\n" +
    "but will be required when upgrading to a later version of the Android Gradle\n" +
    "Plugin.  You can choose to do them in this upgrade to prepare for the future,\n" +
    "with or without upgrading the Android Gradle Plugin to its new version."
  else -> {
    LOG.error("Irrelevant step description requested")
    "These steps are irrelevant to this upgrade (and should not be displayed)"
  }
}

fun AgpVersion?.upgradeLabelText() = when (this) {
  null -> "Upgrade Android Gradle Plugin from unknown version to"
  else -> "Upgrade Android Gradle Plugin from version $this to"
}

fun AgpVersion?.contentDisplayName() = when (this) {
  null -> "Upgrade project from unknown AGP"
  else -> "Upgrade project from AGP $this"
}