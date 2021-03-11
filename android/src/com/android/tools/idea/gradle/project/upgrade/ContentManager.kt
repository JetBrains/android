package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.GradleVersion
import com.android.tools.adtui.model.stdui.CommonComboBoxModel
import com.android.tools.adtui.model.stdui.DefaultCommonComboBoxModel
import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.adtui.model.stdui.EditingErrorCategory
import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.adtui.model.stdui.EditingValidation
import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.OPTIONAL_CODEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.OPTIONAL_INDEPENDENT
import com.android.tools.idea.gradle.repositories.IdeGoogleMavenRepository
import com.android.tools.idea.observable.BindingsManager
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckboxTreeHelper
import com.intellij.ui.CheckboxTreeListener
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeModelAdapter
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.util.EventListener
import javax.swing.JButton
import javax.swing.JTree
import javax.swing.SwingConstants
import javax.swing.event.TreeModelEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

// "Model" here loosely in the sense of Model-View-Controller
internal class ToolWindowModel(val project: Project, val current: GradleVersion) {

  val selectedVersion = OptionalValueProperty<GradleVersion>(GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get()))
  private var processor: AgpUpgradeRefactoringProcessor? = null

  //TODO introduce single state object describing controls and error instead.
  val showLoadingState = BoolValueProperty(true)
  val runDisabledTooltip = StringValueProperty()
  val runEnabled = BoolValueProperty(true)

  val knownVersions = OptionalValueProperty<List<GradleVersion>>()

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
        MANDATORY_INDEPENDENT -> findNecessityNode(MANDATORY_CODEPENDENT)?.let { it.isEnabled = allChildrenChecked(parentNode) }
        MANDATORY_CODEPENDENT -> {
          findNecessityNode(MANDATORY_INDEPENDENT)?.let { if (node.isChecked) disableNode(it) else enableNode(it) }
          findNecessityNode(OPTIONAL_CODEPENDENT)?.let { if (node.isChecked) enableNode(it) else disableNode(it) }
        }
        OPTIONAL_CODEPENDENT -> findNecessityNode(MANDATORY_CODEPENDENT)?.let { it.isEnabled = !anyChildrenChecked(parentNode) }
      }
    }
  }

  init {
    refresh()
    selectedVersion.addListener { refresh() }

    // Request known versions.
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Looking for known versions", false) {
      override fun run(indicator: ProgressIndicator) {
        val knownVersionsList = IdeGoogleMavenRepository.getVersions("com.android.tools.build", "gradle")
          .filter { it > current }
          .toList()
          .sortedDescending()
        invokeLater(ModalityState.NON_MODAL) { knownVersions.value = knownVersionsList }
      }
    })
  }

  fun refresh() {
    showLoadingState.set(true)
    // First clear state
    runEnabled.set(false)
    runDisabledTooltip.clear()
    val root = (treeModel.root as CheckedTreeNode)
    root.removeAllChildren()
    treeModel.nodeStructureChanged(root)

    val newVersion = selectedVersion.valueOrNull
    // TODO(xof/mlazeba): check new version is greater than current here
    // TODO(xof/mlazeba): should we somehow preserve the existing uuid of the processor?
    val newProcessor = newVersion?.let { AgpUpgradeRefactoringProcessor(project, current, it) }
    processor = newProcessor

    if (newProcessor == null) {
      showLoadingState.set(false)
    }
    else {
      ApplicationManager.getApplication().executeOnPooledThread {
        newProcessor.ensureParsedModels()

        val projectFilesClean = isCleanEnoughProject(project)
        invokeLater(ModalityState.NON_MODAL) {
          if (!projectFilesClean) {
            runEnabled.set(false)
            runDisabledTooltip.set("There are uncommitted changes in project build files.  Before upgrading, " +
                                   "you should commit or revert changes to the build files so that changes from the upgrade process " +
                                   "can be handled separately.")
          }
          else {
            refreshTree(newProcessor)
            runEnabled.set(true)
          }
          showLoadingState.set(false)
        }
      }
    }
  }

  private fun refreshTree(processor: AgpUpgradeRefactoringProcessor) {
    val root = treeModel.root as CheckedTreeNode
    root.removeAllChildren()
    //TODO(mlazeba): do we need the check about 'classpathRefactoringProcessor.isAlwaysNoOpForProject' meaning upgrade can not run?
    fun <T : DefaultMutableTreeNode> populateNecessity(
      necessity: AgpUpgradeComponentNecessity,
      constructor: (Any) -> (T)
    ): CheckedTreeNode {
      val node = CheckedTreeNode(necessity)
      processor.activeComponentsForNecessity(necessity).forEach { component -> node.add(constructor(toStepPresentation(component))) }
      node.let { if (it.childCount > 0) root.add(it) }
      return node
    }
    populateNecessity(MANDATORY_INDEPENDENT) { o -> CheckedTreeNode(o).also { it.isEnabled = false } }.isEnabled = false
    populateNecessity(MANDATORY_CODEPENDENT) { o -> CheckedTreeNode(o).also { it.isEnabled = false } }
    populateNecessity(OPTIONAL_CODEPENDENT) { o -> CheckedTreeNode(o).also { it.isChecked = false } }
    populateNecessity(OPTIONAL_INDEPENDENT) { o -> CheckedTreeNode(o).also { it.isChecked = false } }
    treeModel.nodeStructureChanged(root)
  }

  fun runUpgrade(showPreview: Boolean) = processor?.let { processor ->
    processor.components().forEach { it.isEnabled = false }
    CheckboxTreeHelper.getCheckedNodes(DefaultStepPresentation::class.java, null, treeModel)
      .forEach { it.processor.isEnabled = true }

    DumbService.getInstance(processor.project).smartInvokeLater {
      processor.setPreviewUsages(showPreview)
      processor.run()
      // TODO(xof): add callback to refresh tree and textField
    }
  }

  interface ChangeListener : EventListener {
    fun modelChanged()
  }

  interface StepUiPresentation {
    val pageHeader: String
    val treeText: String
    val helpLinkUrl: String?
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
    }
    else -> DefaultStepPresentation(processor)
  }

  private open class DefaultStepPresentation(val processor: AgpUpgradeComponentRefactoringProcessor) : StepUiPresentation {
    override val pageHeader: String
      get() = treeText
    override val treeText: String
      get() = processor.commandName
    override val helpLinkUrl: String?
      get() = processor.getReadMoreUrl()
  }
}

class ContentManager(val project: Project) {
  init {
    ToolWindowManager.getInstance(project).registerToolWindow(
      RegisterToolWindowTask.closable("Upgrade Assistant", icons.GradleIcons.GradleFile))
  }

  fun showContent() {
    val current = AndroidPluginInfo.find(project)?.pluginVersion ?: return
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Upgrade Assistant")!!
    toolWindow.contentManager.removeAllContents(true)
    val model = ToolWindowModel(project, current)
    val view = View(model, toolWindow.contentManager)
    val content = ContentFactory.SERVICE.getInstance().createContent(view.content, "Upgrading project from AGP $current", true)
    content.isPinned = true
    toolWindow.contentManager.addContent(content)
    toolWindow.show()
  }

  private class View(val model: ToolWindowModel, disposable: Disposable) {
    /*
    Experiment of usage of observable property bindings I have found in our code base.
    Taking inspiration from com/android/tools/idea/avdmanager/ConfigureDeviceOptionsStep.java:85 at the moment (Jan 2021).
     */
    private val myBindings = BindingsManager()
    private val myListeners = ListenerManager()

    val tree = CheckboxTree(UpgradeAssistantTreeCellRenderer(), null).apply {
      model = this@View.model.treeModel
      isRootVisible = false
      selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
      addCheckboxTreeListener(this@View.model.checkboxTreeStateUpdater)
      addTreeSelectionListener { e -> refreshDetailsPanel() }
    }

    val versionTextField = CommonComboBox<GradleVersion, CommonComboBoxModel<GradleVersion>>(
      object : DefaultCommonComboBoxModel<GradleVersion>(
        model.selectedVersion.valueOrNull?.toString() ?: "",
        model.knownVersions.valueOrNull ?: emptyList()
      ) {
        init {
          selectedItem = model.selectedVersion.valueOrNull
          myListeners.listen(model.knownVersions) { knownVersions ->
            removeAllElements()
            selectedItem = model.selectedVersion.valueOrNull
            knownVersions.orElse(emptyList()).forEach { addElement(it) }
          }
          placeHolderValue = "Select new version"
        }

        override val editingSupport = object : EditingSupport {
          override val validation: EditingValidation = { value ->
            val parsed = value?.let { GradleVersion.tryParseAndroidGradlePluginVersion(it) }
            when {
              parsed == null -> Pair(EditingErrorCategory.ERROR, "Invalid AGP version format.")
              parsed <= model.current -> Pair(EditingErrorCategory.ERROR, "Selected version too low.")
              else -> EDITOR_NO_ERROR
            }
          }
        }
      }).apply {
      addActionListener {
        this@View.model.selectedVersion.setNullableValue(
          if (model.editingSupport.validation(model.text).first == EditingErrorCategory.ERROR)
            null
          else
            when (val selected = selectedItem) {
              is GradleVersion -> selected
              is String ->
                if (model.editingSupport.validation(selected).first == EditingErrorCategory.ERROR) null
                else GradleVersion.tryParseAndroidGradlePluginVersion(selected)
              else -> null
            }
        )
      }
    }

    val refreshButton = JButton("Refresh").apply {
      addActionListener { this@View.model.refresh() }
      myListeners.listen(this@View.model.runDisabledTooltip) { toolTipText = this@View.model.runDisabledTooltip.get() }
    }
    val okButton = JButton("Run selected steps").apply {
      addActionListener { this@View.model.runUpgrade(false) }
      myListeners.listen(this@View.model.runDisabledTooltip) { toolTipText = this@View.model.runDisabledTooltip.get() }
    }
    val previewButton = JButton("Run with preview").apply {
      addActionListener { this@View.model.runUpgrade(true) }
    }

    val detailsPanel = JBPanel<JBPanel<*>>().apply {
      layout = VerticalLayout(0, SwingConstants.LEFT)
      border = JBUI.Borders.empty(10)
    }
    val content = JBLoadingPanel(BorderLayout(), disposable).apply {
      val controlsPanel = makeTopComponent(model)
      add(controlsPanel, BorderLayout.NORTH)
      add(tree, BorderLayout.WEST)
      add(detailsPanel, BorderLayout.CENTER)

      fun updateState(loading: Boolean) {
        refreshButton.isEnabled = !loading
        if (loading) {
          startLoading()
          detailsPanel.removeAll()
          okButton.isEnabled = false
          previewButton.isEnabled = false
        }
        else {
          stopLoading()
          okButton.isEnabled = model.runEnabled.get()
          previewButton.isEnabled = model.runEnabled.get()
        }
      }

      myListeners.listen(model.showLoadingState, ::updateState)
      updateState(model.showLoadingState.get())
    }

    init {
      model.treeModel.addTreeModelListener(object : TreeModelAdapter() {
        override fun treeStructureChanged(event: TreeModelEvent?) {
          TreeUtil.expandAll(tree)
        }
      })
      TreeUtil.expandAll(tree)
    }

    private fun makeTopComponent(model: ToolWindowModel) = JBPanel<JBPanel<*>>().apply {
      layout = HorizontalLayout(5)
      add(JBLabel("Upgrading Android Gradle Plugin from version ${model.current} to"))
      add(versionTextField)
      // TODO(xof): make these buttons come in a platform-dependent order
      add(refreshButton)
      // TODO(xof): make this look like a default button
      add(okButton)
      add(previewButton)
    }

    private fun refreshDetailsPanel() {
      detailsPanel.removeAll()
      val selectedStep = (tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode)?.userObject
      if (selectedStep is ToolWindowModel.StepUiPresentation) {
        detailsPanel.add(JBLabel(selectedStep.pageHeader))
        selectedStep.helpLinkUrl?.let { url -> detailsPanel.add(HyperlinkLabel("Read more.").apply { setHyperlinkTarget(url) }) }
        if (selectedStep is ToolWindowModel.StepUiWithComboSelectorPresentation) {
          ComboBox(selectedStep.elements.toTypedArray()).apply {
            item = selectedStep.selectedValue
            addActionListener {
              selectedStep.selectedValue = this.item
              tree.repaint()
              refreshDetailsPanel()
            }
            val comboPanel = JBPanel<JBPanel<*>>()
            comboPanel.layout = HorizontalLayout(0)
            comboPanel.add(JBLabel(selectedStep.label))
            comboPanel.add(this)
            detailsPanel.add(comboPanel)
          }
        }
      }
      detailsPanel.revalidate()
      detailsPanel.repaint()
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
          is AgpUpgradeComponentNecessity -> textRenderer.append(o.description())
          is ToolWindowModel.StepUiPresentation -> {
            textRenderer.append(o.treeText)
            if (o is ToolWindowModel.StepUiWithComboSelectorPresentation) {
              textRenderer.icon = AllIcons.Actions.Edit
              textRenderer.isIconOnTheRight = true
              textRenderer.iconTextGap =  10
            }
          }
        }
      }
      super.customizeRenderer(tree, value, selected, expanded, leaf, row, hasFocus)
    }
  }
}

private fun AgpUpgradeRefactoringProcessor.components() = this.componentRefactoringProcessors + this.classpathRefactoringProcessor

private fun AgpUpgradeRefactoringProcessor.activeComponentsForNecessity(necessity: AgpUpgradeComponentNecessity) =
  this.components().filter { it.isEnabled }.filter { it.necessity() == necessity }.filter { !it.isAlwaysNoOpForProject }

private fun AgpUpgradeComponentNecessity.description() = when (this) {
  MANDATORY_INDEPENDENT -> "Pre-upgrade steps"
  MANDATORY_CODEPENDENT -> "Upgrade steps"
  OPTIONAL_CODEPENDENT -> "Post-upgrade steps"
  OPTIONAL_INDEPENDENT -> "Optional steps"
  else -> "Irrelevant steps" // TODO(xof): log this -- should never happen
}
