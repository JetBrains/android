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
package com.android.tools.idea.experimental.callgraph

import com.android.tools.lint.detector.api.interprocedural.*
import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import com.intellij.analysis.AnalysisScope
import com.intellij.ide.hierarchy.*
import com.intellij.ide.hierarchy.actions.BrowseHierarchyActionBase
import com.intellij.ide.hierarchy.call.CallHierarchyNodeDescriptor
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.PopupHandler
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UastContext
import org.jetbrains.uast.convertWithParent
import org.jetbrains.uast.visitor.UastVisitor
import java.util.Comparator
import javax.swing.JTree
import kotlin.collections.ArrayList

/** Creates a collection of UFiles from a project and scope. */
fun UastVisitor.visitAll(project: Project, scope: AnalysisScope): Collection<UFile> {
  val res = ArrayList<UFile>()
  val uastContext = ServiceManager.getService(project, UastContext::class.java)
  scope.accept { virtualFile ->
    if (!uastContext.isFileSupported(virtualFile.name)) return@accept true
    val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@accept true
    val file = uastContext.convertWithParent<UFile>(psiFile) ?: return@accept true
    file.accept(this)
    true
  }
  return res
}

// TODO: Improve node descriptor for lambdas, and show intermediate call expression nodes.

class ContextualCallPathTreeStructure(
    project: Project,
    val graph: ContextualCallGraph,
    element: PsiElement,
    val reverseEdges: Boolean
) :
    HierarchyTreeStructure(
        project,
        CallHierarchyNodeDescriptor(project, null, element, true, false)) {

  val reachableContextualNodes: Multimap<HierarchyNodeDescriptor, ContextualEdge> = HashMultimap.create()

  init {
    val initialEdges = graph.contextualNodes
        .filter { it.node.target.element.psi == element }
        .map { ContextualEdge(it, it.node.target.element) }
    reachableContextualNodes.putAll(myBaseDescriptor, initialEdges)
  }

  override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<Any> {
    return reachableContextualNodes[descriptor]
        .flatMap {
          // Get neighboring contextual nodes.
          if (reverseEdges) graph.inEdges(it.contextualNode)
          else graph.outEdges(it.contextualNode)
        }
        .groupBy { it.contextualNode.node }
        .mapNotNull { (node, contextNodes) ->
          node.target.element.psi?.let { Pair(it, contextNodes) }
        }
        .map { (psi, contextNodes) ->
          val nbrDescriptor = CallHierarchyNodeDescriptor(myProject, descriptor, psi, false, false)
          reachableContextualNodes.putAll(nbrDescriptor, contextNodes)
          nbrDescriptor
        }
        .toTypedArray()
  }
}

// Note: This class is similar to CallHierarchyBrowser, but supports arbitrary PSI elements (not just PsiMethod).
open class ContextualCallPathBrowser(
    project: Project,
    val graph: ContextualCallGraph,
    element: PsiElement
) : CallHierarchyBrowserBase(project, element) {

  override fun createHierarchyTreeStructure(kind: String, psiElement: PsiElement): HierarchyTreeStructure {
    val reverseEdges = kind == CallHierarchyBrowserBase.CALLER_TYPE
    return ContextualCallPathTreeStructure(myProject, graph, psiElement, reverseEdges)
  }

  override fun createTrees(typeToTreeMap: MutableMap<String, JTree>) {
    val group = ActionManager.getInstance().getAction(IdeActions.GROUP_CALL_HIERARCHY_POPUP) as ActionGroup
    val baseOnThisMethodAction = BaseOnThisMethodAction()
    val kinds = arrayOf(
        CallHierarchyBrowserBase.CALLEE_TYPE,
        CallHierarchyBrowserBase.CALLER_TYPE)
    for (kind in kinds) {
      val tree = createTree(false)
      PopupHandler.installPopupHandler(tree, group, ActionPlaces.CALL_HIERARCHY_VIEW_POPUP, ActionManager.getInstance())
      baseOnThisMethodAction
          .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_CALL_HIERARCHY).shortcutSet, tree)
      typeToTreeMap.put(kind, tree)
    }
  }

  override fun getElementFromDescriptor(descriptor: HierarchyNodeDescriptor) = descriptor.psiElement

  override fun isApplicableElement(element: PsiElement) = when (element) {
    is PsiMethod,
    is PsiLambdaExpression,
    is PsiClass -> true
    else -> false
  }

  override fun getComparator(): Comparator<NodeDescriptor<Any>> = JavaHierarchyUtil.getComparator(myProject)
}

class ContextualCallPathProvider(val graph: ContextualCallGraph) : HierarchyProvider {

  override fun getTarget(dataContext: DataContext): PsiElement? {
    val element = CommonDataKeys.PSI_ELEMENT.getData(dataContext)
    return PsiTreeUtil.getNonStrictParentOfType(element,
        PsiMethod::class.java,
        PsiLambdaExpression::class.java,
        PsiClass::class.java)
  }

  override fun createHierarchyBrowser(target: PsiElement) = ContextualCallPathBrowser(target.project, graph, target)

  override fun browserActivated(hierarchyBrowser: HierarchyBrowser) {
    (hierarchyBrowser as ContextualCallPathBrowser).changeView(CallHierarchyBrowserBase.CALLEE_TYPE)
  }
}

class CallGraphAction : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    PsiDocumentManager.getInstance(project).commitAllDocuments() // Prevents problems with smart pointers creation.

    val scope = AnalysisScope(project)
    val cha = ClassHierarchyVisitor()
        .apply { visitAll(project, scope) }
        .classHierarchy
    val receiverEval = IntraproceduralDispatchReceiverVisitor(cha)
        .apply { visitAll(project, scope) }
        .receiverEval
    val callGraph = CallGraphVisitor(receiverEval, cha)
        .apply { visitAll(project, scope) }
        .callGraph
    val contextualGraph = callGraph.buildContextualCallGraph(receiverEval)

    val provider = ContextualCallPathProvider(contextualGraph)
    val target = provider.getTarget(e.dataContext) ?: return
    BrowseHierarchyActionBase.createAndAddToPanel(project, provider, target)
  }
}
