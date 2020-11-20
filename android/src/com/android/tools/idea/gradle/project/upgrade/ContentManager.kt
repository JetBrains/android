package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.OPTIONAL_CODEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.OPTIONAL_INDEPENDENT
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckboxTreeListener
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.Component.LEFT_ALIGNMENT
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

// "Model" here loosely in the sense of Model-View-Controller
internal class ToolWindowModel(var processor: AgpUpgradeRefactoringProcessor) {
}

class ContentManager(val project: Project) {
  init {
    ToolWindowManager.getInstance(project).registerToolWindow(RegisterToolWindowTask.closable("Upgrade Assistant", icons.GradleIcons.GradleFile))
  }

  fun showContent() {
    val current = AndroidPluginInfo.find(project)?.pluginVersion ?: return
    val new = GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Upgrade Assistant")!!
    toolWindow.contentManager.removeAllContents(true)
    val processor = AgpUpgradeRefactoringProcessor(project, current, new)
    val model = ToolWindowModel(processor)
    val component = JBPanel<JBPanel<*>>().apply {
      layout = BorderLayout()
      val tree = makeCenterComponent(model) as JTree
      val textField = makeTopComponent(model, tree)
      add(textField, BorderLayout.NORTH)
      add(tree, BorderLayout.CENTER)
    }
    val content = ContentFactory.SERVICE.getInstance().createContent(component, "Hello, Upgrade!", true)
    content.isPinned = true
    toolWindow.contentManager.addContent(content)
    toolWindow.show()
  }

  internal fun makeTopComponent(model: ToolWindowModel, tree: JTree): JComponent {
    val panel = JBPanel<JBPanel<*>>()
    panel.alignmentX = LEFT_ALIGNMENT // obviously this doesn't work to left-align the panel itself, *sigh*
    panel.add(JBLabel("Upgrading from ${model.processor.current} to"))
    val textField = JBTextField("${model.processor.new}")
    textField.addActionListener { _ ->
      val text = textField.text
      model.processor = AgpUpgradeRefactoringProcessor(project, model.processor.current, GradleVersion.parse(text))
      refreshTree(model, tree)
    }
    textField.addFocusListener(object: FocusListener {
      override fun focusGained(e: FocusEvent?) {

      }
      override fun focusLost(e: FocusEvent?) {
        val text = textField.text
        model.processor = AgpUpgradeRefactoringProcessor(project, model.processor.current, GradleVersion.parse(text))
        refreshTree(model, tree)
      }
    })
    panel.add(textField)
    val refreshButton = JButton("Refresh")
    refreshButton.addActionListener { _ ->
      refreshTree(model, tree)
    }
    val okButton = JButton("OK Go")
    okButton.addActionListener { _ ->
      model.processor.components().forEach { it.isEnabled = false }
      (tree as CheckboxTree).getCheckedNodes(AgpUpgradeComponentRefactoringProcessor::class.java, null)
        .forEach { it.isEnabled = true }
      val runProcessor = showAndGetAgpUpgradeDialog(model.processor)
      if (runProcessor) {
        DumbService.getInstance(model.processor.project).smartInvokeLater {
          model.processor.run()
          // TODO(xof): add callback to refresh tree and textField
        }
      }
    }
    // TODO(xof): make these buttons come in a platform-dependent order
    panel.add(refreshButton)
    // TODO(xof): make this look like a default button
    panel.add(okButton)
    return panel
  }

  internal fun makeCenterComponent(model: ToolWindowModel): JComponent {
    val renderer = object : CheckboxTree.CheckboxTreeCellRenderer(true, true) {
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
            is AgpUpgradeComponentRefactoringProcessor -> {
              textRenderer.append(o.commandName)
              o.getReadMoreUrl()?.let { textRenderer.append(" Read more.") }
            }
          }
        }
        super.customizeRenderer(tree, value, selected, expanded, leaf, row, hasFocus)
      }
    }
    val root = CheckedTreeNode(null)
    val tree = CheckboxTree(renderer, root)
    tree.isRootVisible = false
    tree.addCheckboxTreeListener(object : CheckboxTreeListener {
      override fun nodeStateChanged(node: CheckedTreeNode) {
        fun findNecessityNode(necessity: AgpUpgradeComponentNecessity): CheckedTreeNode? =
          root.children().asSequence().firstOrNull { (it as CheckedTreeNode).userObject == necessity } as? CheckedTreeNode
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
    })
    refreshTree(model, tree)
    return tree
  }

  fun AgpUpgradeRefactoringProcessor.components() = this.componentRefactoringProcessors + this.classpathRefactoringProcessor

  fun AgpUpgradeRefactoringProcessor.activeComponentsForNecessity(necessity: AgpUpgradeComponentNecessity) =
    this.components().filter { it.isEnabled }.filter { it.necessity() == necessity }.filter { !it.isAlwaysNoOpForProject }

  fun AgpUpgradeComponentNecessity.description() = when(this) {
    MANDATORY_INDEPENDENT -> "Pre-upgrade steps"
    MANDATORY_CODEPENDENT -> "Upgrade steps"
    OPTIONAL_CODEPENDENT -> "Post-upgrade steps"
    OPTIONAL_INDEPENDENT -> "Optional steps"
    else -> "Irrelevant steps" // TODO(xof): log this -- should never happen
  }

  private fun refreshTree(model: ToolWindowModel, tree: JTree) {
    val root = tree.model.root as CheckedTreeNode
    root.removeAllChildren()
    fun <T : DefaultMutableTreeNode> populateNecessity(necessity: AgpUpgradeComponentNecessity, constructor: (Any) -> (T)): CheckedTreeNode {
      val node = CheckedTreeNode(necessity)
      model.processor.activeComponentsForNecessity(necessity).forEach { component -> node.add(constructor(component)) }
      node.let { if (it.childCount > 0) root.add(it) }
      return node
    }
    populateNecessity(MANDATORY_INDEPENDENT) { o -> CheckedTreeNode(o).also { it.isEnabled = false } }.isEnabled = false
    populateNecessity(MANDATORY_CODEPENDENT) { o -> CheckedTreeNode(o).also { it.isEnabled = false } }
    populateNecessity(OPTIONAL_CODEPENDENT) { o -> CheckedTreeNode(o).also { it.isChecked = false } }
    populateNecessity(OPTIONAL_INDEPENDENT) { o -> CheckedTreeNode(o).also { it.isChecked = false } }
    (tree.model as DefaultTreeModel).nodeStructureChanged(root)
    TreeUtil.expandAll(tree)
  }
}