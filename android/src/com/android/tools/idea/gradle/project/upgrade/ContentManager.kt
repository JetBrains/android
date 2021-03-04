package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.GradleVersion
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
import com.android.tools.idea.observable.ui.TextProperty
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
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeModelAdapter
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.util.EventListener
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JTextField
import javax.swing.JTree
import javax.swing.SwingConstants
import javax.swing.event.TreeModelEvent
import javax.swing.plaf.basic.BasicComboBoxEditor
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

// "Model" here loosely in the sense of Model-View-Controller
internal class ToolWindowModel(
  var processor: AgpUpgradeRefactoringProcessor
) {

  val showLoadingState = BoolValueProperty(true)

  val version = StringValueProperty(processor.new.toString())

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
    //Listen for value changes
    version.addListener {
      processor = AgpUpgradeRefactoringProcessor(processor.project, processor.current, GradleVersion.parse(version.get()))
      refresh()
    }
    refresh()

    // Request known versions.
    ProgressManager.getInstance().run(object : Task.Backgroundable(processor.project, "Looking for known versions", false) {
      override fun run(indicator: ProgressIndicator) {
        knownVersions.value = IdeGoogleMavenRepository.getVersions("com.android.tools.build", "gradle")
          .filter { it > processor.current }
          .toList()
          .sortedDescending()
      }
    })
  }

  fun refresh() {
    showLoadingState.set(true)
    // First clear state
    runEnabled.set(true)
    runDisabledTooltip.clear()
    val root = (treeModel.root as CheckedTreeNode)
    root.removeAllChildren()
    treeModel.nodeStructureChanged(root)


    ApplicationManager.getApplication().executeOnPooledThread {
      processor.ensureParsedModels()

      val projectFilesClean = isCleanEnoughProject(processor.project)
      invokeLater(ModalityState.NON_MODAL) {
        if (!projectFilesClean) {
          runEnabled.set(false)
          runDisabledTooltip.set("There are uncommitted changes in project build files.  Before upgrading, " +
                                 "you should commit or revert changes to the build files so that changes from the upgrade process " +
                                 "can be handled separately.")
        }
        refreshTree()
        showLoadingState.set(false)
      }
    }
  }

  private fun refreshTree() {
    val root = treeModel.root as CheckedTreeNode
    root.removeAllChildren()
    //TODO(mlazeba): do we need the check about 'classpathRefactoringProcessor.isAlwaysNoOpForProject' meaning upgrade can not run?
    fun <T : DefaultMutableTreeNode> populateNecessity(necessity: AgpUpgradeComponentNecessity,
                                                       constructor: (Any) -> (T)): CheckedTreeNode {
      val node = CheckedTreeNode(necessity)
      processor.activeComponentsForNecessity(necessity).forEach { component -> node.add(constructor(component)) }
      node.let { if (it.childCount > 0) root.add(it) }
      return node
    }
    populateNecessity(MANDATORY_INDEPENDENT) { o -> CheckedTreeNode(o).also { it.isEnabled = false } }.isEnabled = false
    populateNecessity(MANDATORY_CODEPENDENT) { o -> CheckedTreeNode(o).also { it.isEnabled = false } }
    populateNecessity(OPTIONAL_CODEPENDENT) { o -> CheckedTreeNode(o).also { it.isChecked = false } }
    populateNecessity(OPTIONAL_INDEPENDENT) { o -> CheckedTreeNode(o).also { it.isChecked = false } }
    treeModel.nodeStructureChanged(root)
  }

  fun runUpgrade(showPreview: Boolean) {
    processor.components().forEach { it.isEnabled = false }
    CheckboxTreeHelper.getCheckedNodes(AgpUpgradeComponentRefactoringProcessor::class.java, null, treeModel)
      .forEach { it.isEnabled = true }

    DumbService.getInstance(processor.project).smartInvokeLater {
      processor.setPreviewUsages(showPreview)
      processor.run()
      // TODO(xof): add callback to refresh tree and textField
    }
  }

  interface ChangeListener : EventListener {
    fun modelChanged()
  }
}

class ContentManager(val project: Project) {
  init {
    ToolWindowManager.getInstance(project).registerToolWindow(
      RegisterToolWindowTask.closable("Upgrade Assistant", icons.GradleIcons.GradleFile))
  }

  fun showContent() {
    val current = AndroidPluginInfo.find(project)?.pluginVersion ?: return
    val new = GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Upgrade Assistant")!!
    toolWindow.contentManager.removeAllContents(true)
    val processor = AgpUpgradeRefactoringProcessor(project, current, new)
    val model = ToolWindowModel(processor)
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
    val textField = AgpVersionEditor(model, myBindings, myListeners)

    val refreshButton = JButton("Refresh").apply {
      addActionListener { this@View.model.refresh() }
    }
    val okButton = JButton("Run selected steps").apply {
      addActionListener { this@View.model.runUpgrade(false) }
      myListeners.listen(this@View.model.runDisabledTooltip) { tooltip -> toolTipText = tooltip }
      myListeners.listen(this@View.model.runEnabled) { state -> isEnabled = state }
    }
    val previewButton = JButton("Run with preview").apply {
      addActionListener { this@View.model.runUpgrade(true) }
      myListeners.listen(this@View.model.runDisabledTooltip) { tooltip -> toolTipText = tooltip }
      myListeners.listen(this@View.model.runEnabled) { state -> isEnabled = state }
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

      fun updateState(loading: Boolean) = if (loading) {
        startLoading()
        detailsPanel.removeAll()
        UIUtil.setEnabled(controlsPanel, false, true)
      }
      else {
        stopLoading()
        UIUtil.setEnabled(controlsPanel, true, true)
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
      add(JBLabel("Upgrading Android Gradle Plugin from version ${model.processor.current} to"))
      add(textField)
      // TODO(xof): make these buttons come in a platform-dependent order
      add(refreshButton)
      // TODO(xof): make this look like a default button
      add(okButton)
      add(previewButton)
    }

    private fun refreshDetailsPanel() {
      detailsPanel.removeAll()
      val selectedProcessor = (tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? AgpUpgradeComponentRefactoringProcessor
      if (selectedProcessor != null) {
        detailsPanel.add(JBLabel(selectedProcessor.commandName))
        selectedProcessor.getReadMoreUrl()?.let { url -> detailsPanel.add(HyperlinkLabel("Read more.").apply { setHyperlinkTarget(url) }) }
        if (selectedProcessor is Java8DefaultRefactoringProcessor) {
          ComboBox(arrayOf(
            Java8DefaultRefactoringProcessor.NoLanguageLevelAction.ACCEPT_NEW_DEFAULT,
            Java8DefaultRefactoringProcessor.NoLanguageLevelAction.INSERT_OLD_DEFAULT
          )).apply {
            item = selectedProcessor.noLanguageLevelAction
            addActionListener { selectedProcessor.noLanguageLevelAction = this.item }
            detailsPanel.add(this)
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
          is AgpUpgradeComponentRefactoringProcessor -> textRenderer.append(o.commandName)
        }
      }
      super.customizeRenderer(tree, value, selected, expanded, leaf, row, hasFocus)
    }
  }

  /**
  Field is inspired by PSD AGP version field that shows known versions in dropdown but allows to type anything.
  Info of where to look for further details on its implementation:
  PSD has an AGP versions field that is an editable comboBox with selectable known versions.
  The property is defined in [com.android.tools.idea.gradle.structure.model.PsProjectDescriptors.getAndroidGradlePluginVersion]
  Gets known versions as [com.android.tools.idea.gradle.structure.model.helpers.PropertyKnownValuesKt.androidGradlePluginVersionValues]
  (uses PsProject) that internally looks into all project repositories for known versions
  (e.g. see [GoogleMavenRepository][com.android.ide.common.repository.GoogleMavenRepository]).
  On UI side property uses [SimplePropertyEditor][com.android.tools.idea.gradle.structure.configurables.ui.properties.SimplePropertyEditor].

  This implementation is partly copied from [RenderedComboBox in PSD][com.android.tools.idea.gradle.structure.configurables.ui.RenderedComboBox]

   */
  private class AgpVersionEditor(
    private val model: ToolWindowModel,
    private val myBindings: BindingsManager,
    private val myListeners: ListenerManager
  ) : ComboBox<GradleVersion>() {
    private val itemsModel = DefaultComboBoxModel<GradleVersion>()
    private val comboBoxEditor = object : BasicComboBoxEditor() {
      override fun createEditorComponent(): JTextField {
        return JBTextField()
      }
    }
    val textProperty = TextProperty(comboBoxEditor.editorComponent as JTextField)

    init {
      super.setModel(itemsModel)
      isEditable = true
      setEditor(comboBoxEditor)

      myBindings.bindTwoWay(textProperty, model.version)
      myListeners.listen(model.knownVersions) { knownVersions -> setKnownValues(knownVersions.orElse(emptyList())) }
      setKnownValues(model.knownVersions.getValueOr(emptyList()))
    }

    /**
     * Populates the drop-down list of the combo-box.
     */
    fun setKnownValues(knownValues: List<GradleVersion>) {
      //Copied from com/android/tools/idea/gradle/structure/configurables/ui/RenderedComboBox.kt
      //beingLoaded = true
      try {
        val prevItemCount = itemsModel.size
        val selectedItem = itemsModel.selectedItem
        val existing = (0 until itemsModel.size).asSequence().map { itemsModel.getElementAt(it) }.toMutableSet()
        knownValues.forEachIndexed { index, value ->
          if (existing.contains(value)) {
            while (itemsModel.size > index && itemsModel.getElementAt(index) != value) {
              itemsModel.removeElementAt(index)
              existing.remove(value)
            }
          }
          if (itemsModel.size == index || itemsModel.getElementAt(index) != value) {
            itemsModel.insertElementAt(value, index)
          }
        }
        if (isPopupVisible && prevItemCount == 0) {
          hidePopup()
          showPopup()
        }
        if (itemsModel.selectedItem != selectedItem) {
          itemsModel.selectedItem = selectedItem
        }
        //updateWatermark()
      }
      finally {
        //beingLoaded = false
      }
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
