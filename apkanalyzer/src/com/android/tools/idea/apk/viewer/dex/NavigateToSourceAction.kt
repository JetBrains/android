/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.apk.analyzer.dex.tree.DexClassNode
import com.android.tools.apk.analyzer.dex.tree.DexElementNode
import com.android.tools.apk.analyzer.dex.tree.DexFieldNode
import com.android.tools.apk.analyzer.dex.tree.DexMethodNode
import com.android.tools.apk.analyzer.dex.tree.DexPackageNode
import com.android.tools.idea.AndroidPsiUtils
import com.intellij.execution.ExecutionBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiClassInitializer
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiTarget
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.treeStructure.Tree
import java.awt.event.MouseEvent

class NavigateToSourceAction(private val tree: Tree) :
  AnAction("Navigate to Source", null, AllIcons.Actions.EditSource) {

    init {
      registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_GOTO_DECLARATION).shortcutSet, tree)

    }
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    presentation.isEnabled = false
    val project = getEventProject(e) ?: return
    val node = getSelectedNode() ?: return
    if (node.canNavigate(project)) {
      presentation.isEnabled = true
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val node = getSelectedNode() ?: return
    val project = getEventProject(e) ?: return
    node.navigate(project, e)
  }

  private fun DexElementNode.navigate(project: Project, e: AnActionEvent) {
    val targets = getNavigators(project)
    when (targets.size) {
      0 -> return
      1 -> targets.first().navigate(true)
      else -> showChooser(targets, e)
    }
  }

  private fun DexElementNode.getNavigators(project: Project): List<PsiTarget> {
    return when (this) {
      is DexClassNode -> getClassNavigators(project)
      is DexFieldNode -> getFieldNavigators(project)
      is DexMethodNode -> getMethodNavigators(project)
      else -> emptyList()
    }
  }

  private fun DexClassNode.getClassNavigators(project: Project): List<PsiTarget> {
    val packageNode = parent as? DexPackageNode ?: return emptyList()
    val className = "${packageNode.packageName}.$name"
    return AndroidPsiUtils.resolveClasses(project, className)
      .filter { it.name == name && it.canNavigateToSource() }
  }

  private fun DexFieldNode.getFieldNavigators(project: Project): List<PsiTarget> {
    val classNode = parent as? DexClassNode ?: return emptyList()
    val packageNode = classNode.parent as? DexPackageNode ?: return emptyList()
    val className = "${packageNode.packageName}.${classNode.name}"
    return AndroidPsiUtils.resolveClasses(project, className)
      .flatMap { it.fields.asIterable() }
      .filter { it.name == name.substringAfterLast(" ") && it.canNavigateToSource() }
  }

  private fun DexMethodNode.getMethodNavigators(project: Project): List<PsiTarget> {
    val classNode = parent as? DexClassNode ?: return emptyList()
    val packageNode = classNode.parent as? DexPackageNode ?: return emptyList()
    val className = "${packageNode.packageName}.${classNode.name}"
    val classes = AndroidPsiUtils.resolveClasses(project, className)
    return if (name == "<clinit>()") {
      classes.flatMap { it.initializers.asIterable() }
        .filter { it.canNavigateToSource() }
        .map { PsiClassInitializerTarget(it) }
    }
    else {
      classes
        .flatMap { it.methods.asIterable() }
        .filter { it.toNodeName() == name && it.canNavigateToSource() }
    }
  }

  private fun DexElementNode.canNavigate(project: Project): Boolean {
    return getNavigators(project).isNotEmpty()
  }

  private fun getSelectedNode() = tree.selectionPath?.lastPathComponent as? DexElementNode

  private class PsiClassInitializerTarget(private val element: PsiClassInitializer) : PsiTarget {
    override fun isValid() = element.isValid

    override fun getNavigationElement() = element
  }
}

private fun PsiMethod.toNodeName(): String {
  return buildString {
    returnType?.let {
      append(it.canonicalText)
      append(' ')
    }
    append(if (isConstructor) "<init>" else name)
    append('(')
    val args = parameterList.parameters.joinToString(",") { it.type.canonicalText }
    append(args)
    append(')')
  }
}

private fun showChooser(targets: List<PsiTarget>, e: AnActionEvent) {
  val popup =
    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(targets.map { it.navigationElement })
      .setRenderer(DefaultPsiElementCellRenderer())
      .setTitle(ExecutionBundle.message("popup.title.choose.target.file"))
      .setItemChosenCallback {
        (it as Navigatable).navigate(true)
      }
      .createPopup()
  val inputEvent = e.inputEvent
  val mouseEvent = inputEvent as? MouseEvent
  when {
    mouseEvent != null -> popup.show(RelativePoint.fromScreen(mouseEvent.locationOnScreen))
    inputEvent != null -> popup.showInCenterOf(inputEvent.component)
    else -> popup.showInFocusCenter()
  }
}