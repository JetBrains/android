/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.apk.viewer.dex

import com.android.tools.apk.analyzer.dex.DexReferences
import com.android.tools.apk.analyzer.dex.PackageTreeCreator
import com.android.tools.apk.analyzer.dex.tree.DexClassNode
import com.android.tools.apk.analyzer.dex.tree.DexElementNode
import com.android.tools.apk.analyzer.dex.tree.DexFieldNode
import com.android.tools.apk.analyzer.dex.tree.DexMethodNode
import com.android.tools.proguard.ProguardMap
import com.android.tools.proguard.ProguardSeedsMap
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.Reference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.concurrency.EdtExecutorService
import java.awt.Dimension
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.ExpandVetoException
import javax.swing.tree.TreePath

class ShowReferencesAction(private val tree: Tree, private val dexFileViewer: DexFileViewer) :
  AnAction(ProjectBundle.message("find.usages.action.text"), null, AllIcons.Actions.Find) {

  init {
    registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES).shortcutSet, tree)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return BGT
  }

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    presentation.isEnabled = false

    if (canShowReferences(getSelectedNode())) {
      presentation.isEnabled = true
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val node = checkNotNull(getSelectedNode())
    val references = checkNotNull(dexFileViewer.dexReferences)
    val project = checkNotNull(getEventProject(e))

    Futures.addCallback(references, object : FutureCallback<DexReferences?> {
      override fun onSuccess(result: DexReferences?) {
        showReferenceTree(e, node, project, result!!)
      }

      override fun onFailure(t: Throwable) {
      }
    }, EdtExecutorService.getInstance())
  }

  private fun showReferenceTree(e: AnActionEvent, node: DexElementNode, project: Project?, references: DexReferences) {
    val proguardMappings = dexFileViewer.proguardMappings
    val proguardMap = proguardMappings?.map
    val seedsMap = proguardMappings?.seeds
    val deobfuscate = dexFileViewer.isDeobfuscateNames

    val reference = node.reference
    checkNotNull(reference)
    val tree = Tree(DefaultTreeModel(references.getReferenceTreeFor(reference, true)))
    tree.showsRootHandles = true
    tree.addTreeWillExpandListener(object : TreeWillExpandListener {
      override fun treeWillExpand(event: TreeExpansionEvent) {
        val path = event.path
        if (path.lastPathComponent is DexElementNode) {
          val lastNode = path.lastPathComponent as DexElementNode
          if (!DexReferences.isAlreadyLoaded(lastNode)) {
            lastNode.removeAllChildren()
            checkNotNull(lastNode.reference)
            references.addReferencesForNode(lastNode, true)
            lastNode.sort(DexReferences.NODE_COMPARATOR)
          }
        }
      }

      @Throws(ExpandVetoException::class)
      override fun treeWillCollapse(event: TreeExpansionEvent) {
      }
    })

    tree.addTreeSelectionListener {
      onSelectionChanged(it)
    }

    tree.cellRenderer = ReferenceRenderer(seedsMap, proguardMap, deobfuscate)

    val pane = JBScrollPane(tree)
    pane.preferredSize = Dimension(600, 400)
    val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(pane, null)
      .setProject(project)
      .setDimensionServiceKey(project, ShowReferencesAction::class.java.name, false)
      .setResizable(true)
      .setMovable(true)
      .setTitle("References to " + node.name)
      .setFocusable(true)
      .setRequestFocus(true)
      .createPopup()
    popup.showInBestPositionFor(e.dataContext)
  }

  private fun onSelectionChanged(e: TreeSelectionEvent) {
    val selectedNode = e.path.lastPathComponent as DexElementNode
    val ref = selectedNode.reference ?: return
    val root = tree.model.root as DexElementNode
    val descendant = root.findDescendant(ref) ?: return
    tree.selectionPath = descendant.toTreePath()
  }

  private fun getSelectedNode(): DexElementNode? = tree.selectionPath?.lastPathComponent as? DexElementNode

  companion object {
    @VisibleForTesting
    fun canShowReferences(node: DexElementNode?): Boolean {
      if (node?.reference == null) {
        return false
      }
      return node is DexClassNode || node is DexMethodNode || node is DexFieldNode
    }
  }

  private class ReferenceRenderer(
    private val seedsMap: ProguardSeedsMap?,
    private val proguardMap: ProguardMap?,
    private val deobfuscate: Boolean) : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
      tree: JTree,
      value: Any,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean
    ) {
      val node = value as DexElementNode
      val ref = node.reference

      val isSeed = node.isSeed(seedsMap, proguardMap, false)
      val attr = SimpleTextAttributes(
        if (isSeed) SimpleTextAttributes.STYLE_BOLD else SimpleTextAttributes.STYLE_PLAIN,
        null
      )

      val usedProguardMap = if (deobfuscate) proguardMap else null

      when (ref) {
        is TypeReference -> renderTypeReference(ref, usedProguardMap, attr)
        is MethodReference -> renderMethodReference(ref, usedProguardMap, attr)
        is FieldReference -> renderFieldReference(ref, usedProguardMap, attr)
      }
      icon = DexNodeIcons.forNode(node)
    }

    private fun renderTypeReference(
      ref: TypeReference,
      usedProguardMap: ProguardMap?,
      attr: SimpleTextAttributes
    ) {
      append(PackageTreeCreator.decodeClassName(ref.type, usedProguardMap), attr)
    }

    private fun renderMethodReference(
      ref: MethodReference,
      usedProguardMap: ProguardMap?,
      attr: SimpleTextAttributes
    ) {
      append(PackageTreeCreator.decodeClassName(ref.definingClass, usedProguardMap), attr)
      append(": ", attr)
      append(PackageTreeCreator.decodeClassName(ref.returnType, usedProguardMap), attr)
      append(" ", attr)
      append(PackageTreeCreator.decodeMethodName(ref, usedProguardMap), attr)
      append(PackageTreeCreator.decodeMethodParams(ref, usedProguardMap), attr)
    }

    private fun renderFieldReference(
      ref: FieldReference,
      usedProguardMap: ProguardMap?,
      attr: SimpleTextAttributes
    ) {
      append(PackageTreeCreator.decodeClassName(ref.definingClass, usedProguardMap), attr)
      append(": ", attr)
      append(PackageTreeCreator.decodeClassName(ref.type, usedProguardMap), attr)
      append(" ", attr)
      append(PackageTreeCreator.decodeFieldName(ref, usedProguardMap), attr)
    }
  }
}

private fun DexElementNode.findDescendant(reference: Reference): DexElementNode? {
  if (this.reference == reference) {
    return this
  }
  children().iterator().forEach {
    val child = it as DexElementNode
    val node = child.findDescendant(reference)
    if (node != null) {
      return node
    }
  }
  return null
}

private fun DexElementNode.toTreePath(): TreePath {
  val nodes = buildList<DexElementNode> {
    var node: DexElementNode? = this@toTreePath
    while (node != null) {
      add(0, node)
      node = node.parent
    }
  }
  return TreePath(nodes.toTypedArray())
}

