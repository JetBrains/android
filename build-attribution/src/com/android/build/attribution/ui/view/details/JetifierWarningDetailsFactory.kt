/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.build.attribution.ui.view.details

import com.android.build.attribution.analyzers.JetifierCanBeRemoved
import com.android.build.attribution.analyzers.JetifierNotUsed
import com.android.build.attribution.analyzers.JetifierRequiredForLibraries
import com.android.build.attribution.analyzers.JetifierUsageAnalyzerResult
import com.android.build.attribution.analyzers.JetifierUsedCheckRequired
import com.android.build.attribution.ui.BuildAnalyzerBrowserLinks
import com.android.build.attribution.ui.HtmlLinksHandler
import com.android.build.attribution.ui.htmlTextLabelWithFixedLines
import com.android.build.attribution.ui.insertBRTags
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.android.ide.common.attribution.CheckJetifierResult
import com.intellij.find.FindModel
import com.intellij.find.findInProject.FindInProjectManager
import com.intellij.ide.CommonActionsManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.PopupHandler
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.SwingConstants
import javax.swing.tree.DefaultMutableTreeNode

class JetifierWarningDetailsFactory(
  private val actionHandlers: ViewActionHandlers
) {

  fun createPage(data: JetifierUsageAnalyzerResult): JPanel = when (data) {
    JetifierUsedCheckRequired -> createCheckRequiredPage()
    JetifierCanBeRemoved -> createJetifierNotRequiredPage()
    is JetifierRequiredForLibraries -> createJetifierRequiredForLibrariesPage(data)
    JetifierNotUsed -> JPanel()
  }

  private fun createCheckRequiredPage() = JPanel().apply {
    layout = VerticalLayout(10, SwingConstants.LEFT)
    val linksHandler = HtmlLinksHandler(actionHandlers)
    val learnMoreLink = linksHandler.externalLink("Learn more", BuildAnalyzerBrowserLinks.JETIIFER_MIGRATE)
    val contentHtml = """
          <b>Confirm need for Jetifier flag in your project</b>
          Your project’s gradle.settings file includes ‘enableJetifier’. This flag is needed
          to enable AndroidX for libraries that don’t support it natively. $learnMoreLink.
  
          Your project may no longer need this flag and could save build time by removing it.
          Click the button below to verify if enableJetifier is needed.
        """.trimIndent().insertBRTags()
    add(htmlTextLabelWithFixedLines(contentHtml, linksHandler))
    add(JButton("Check Jetifier").apply { addActionListener { actionHandlers.runCheckJetifierTask() } })
  }

  private fun createJetifierNotRequiredPage() = JPanel().apply {
    layout = VerticalLayout(10, SwingConstants.LEFT)
    val linksHandler = HtmlLinksHandler(actionHandlers)
    val removeJetifierLink = linksHandler.actionLink("Remove enableJetifier", "remove") {
      actionHandlers.turnJetifierOffInProperties()
    }
    val contentHtml = """
      <b>Remove Jetifier flag</b>
      Your project’s gradle.settings includes enableJetifier. This flag is not needed by your project
      and removing it will improve build performance.
      
      $removeJetifierLink
      """.trimIndent().insertBRTags()
    add(htmlTextLabelWithFixedLines(contentHtml, linksHandler))
  }

  private fun createJetifierRequiredForLibrariesPage(data: JetifierRequiredForLibraries) = JPanel().apply {
    layout = VerticalLayout(10, SwingConstants.LEFT)
    val contentHtml = """
      <b>Jetifier flag is needed by some libraries in your project</b>
      The following libraries rely on the ‘enableJetifier’ flag to work with AndroidX.
      Please consider upgrading to versions of these libraries that directly depend
      on AndroidX. Please contact the library authors to request native AndroidX support,
      if it’s not available yet.
      """.trimIndent().insertBRTags()
    val root = createLibsTree(data.checkJetifierResult)
    val tree = Tree(root).apply {
      isRootVisible = false
      cellRenderer = LibsTreeCellRenderer()
    }
    DefaultActionGroup().let { group ->
      val treeExpander = DefaultTreeExpander(tree)
      group.add(FindSelectedLibUsagesAction(tree))
      group.addSeparator()
      group.add(CommonActionsManager.getInstance().createExpandAllAction(treeExpander, this))
      group.add(CommonActionsManager.getInstance().createCollapseAllAction(treeExpander, this))
      PopupHandler.installPopupHandler(tree, group, ActionPlaces.POPUP, ActionManager.getInstance())
    }
    add(htmlTextLabelWithFixedLines(contentHtml))
    add(JButton("Refresh").apply { addActionListener { actionHandlers.runCheckJetifierTask() } })
    add(tree)
  }

  private fun createLibsTree(checkJetifierResult: CheckJetifierResult) = DefaultMutableTreeNode().also { root ->
    val nodes = mutableMapOf<String, DefaultMutableTreeNode>()

    checkJetifierResult.dependenciesDependingOnSupportLibs.asSequence().forEach {
      nodes.computeIfAbsent(it.key) { dependency -> LibTreeNode(LibDescriptor(dependency)) }.apply {
        (userObject as LibDescriptor).usedDirectly = true
      }

      it.value.dependencyPath.elements.drop(1).forEach { dependency ->
        nodes.computeIfAbsent(dependency) { LibTreeNode(LibDescriptor(dependency)) }.apply {
          (userObject as LibDescriptor).usedTransitively = true
        }
      }
    }

    fun DefaultMutableTreeNode.addPath(path: List<String>) {
      if (path.isEmpty()) return
      nodes[path.first()]?.let {
        it.addPath(path.drop(1))
        add(it)
      }
    }
    checkJetifierResult.dependenciesDependingOnSupportLibs.asSequence().forEach {
      val reversedPath = it.value.dependencyPath.elements.reversed().run { if (size > 1) drop(1) else this }
      root.addPath(reversedPath)
    }
  }

  private class LibsTreeCellRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
      tree: JTree,
      value: Any?,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean
    ) {
      val node = value as DefaultMutableTreeNode
      val userObj = node.userObject
      if (userObj is LibDescriptor) {
        append(userObj.fullName)
        append(userObj.usageSuffix, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
    }
  }

  private class LibTreeNode(val libDescriptor: LibDescriptor) : DefaultMutableTreeNode(libDescriptor) {
    override fun toString(): String {
      return libDescriptor.fullName
    }
  }

  class LibDescriptor(
    /** Full dependency name in 'group:name:version' format. */
    val fullName: String,
  ) {
    var usedDirectly: Boolean = false
    var usedTransitively: Boolean = false
    val usageSuffix: String
      get() = when {
        usedDirectly && usedTransitively -> " used directly and transitively"
        usedDirectly -> " used directly"
        usedTransitively -> " used transitively"
        else -> ""
      }

    /**
     * Returns only 'group:name' part of this dependency.
     */
    val groupAndName: String
      get() = fullName.substringBeforeLast(":")
  }

  private class FindSelectedLibUsagesAction(val tree: Tree) : AnAction("Find Usages") {
    override fun update(e: AnActionEvent) {
      val libPresentation = (TreeUtil.getSelectedPathIfOne(tree)?.lastPathComponent as? LibTreeNode)?.libDescriptor
      if (libPresentation?.usedDirectly == true) {
        e.presentation.text = "Find Usages of ${libPresentation.groupAndName}"
      }
      else {
        e.presentation.isVisible = false
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      val selectedLib = (TreeUtil.getSelectedPathIfOne(tree)?.lastPathComponent as? LibTreeNode)?.libDescriptor ?: return
      val project = CommonDataKeys.PROJECT.getData(e.dataContext)

      val findModel = FindModel().apply {
        FindModel.initStringToFind(this, selectedLib.groupAndName)
        isReplaceState = false
        searchContext = FindModel.SearchContext.IN_STRING_LITERALS
      }
      FindInProjectManager.getInstance(project).findInProject({ null }, findModel)
    }
  }
}