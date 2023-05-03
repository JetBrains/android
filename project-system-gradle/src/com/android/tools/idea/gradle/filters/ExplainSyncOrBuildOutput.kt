package com.android.tools.idea.gradle.filters

import com.android.tools.idea.explainer.IssueExplainer
import com.intellij.build.ExecutionNode
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.tree.TreePath

private fun getSelectedNodes(myTree: Tree): List<ExecutionNode> {
  val result = mutableListOf<ExecutionNode>()
  if (myTree != null) {
    val nodes =
      TreeUtil.collectSelectedObjects<ExecutionNode?>(myTree) { path: TreePath? ->
        TreeUtil.getLastUserObject<ExecutionNode>(ExecutionNode::class.java, path)
      }
    return nodes
  }
  return result
}

class ExplainSyncOrBuildOutput : DumbAwareAction(service<IssueExplainer>().getShortLabel(), service<IssueExplainer>().getShortLabel(),
                                                 service<IssueExplainer>().getIcon()) {

  override fun update(e: AnActionEvent) {
    // we don't want to ask question about intermediate nodes which are just file names
    val component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
    val tree = component as? Tree
    val rowNumber = tree?.selectionRows?.singleOrNull()
    if (rowNumber == null) {
      e.presentation.isEnabled = false
    } else {
      // treePath could be null when running on BGT
      val treePath = tree.getPathForRow(rowNumber) ?: return
      // skip "Download info" node with rowNumber == 1
      e.presentation.isEnabled = rowNumber > 1 && tree.model.isLeaf(treePath.lastPathComponent)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
    val tree = component as? Tree ?: return
    val selectedNodes = getSelectedNodes(tree)
    if (selectedNodes.isEmpty()) return
    val text = selectedNodes[0].name
    service<IssueExplainer>().explain(e.project!!, text, IssueExplainer.RequestKind.BUILD_ISSUE)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

}