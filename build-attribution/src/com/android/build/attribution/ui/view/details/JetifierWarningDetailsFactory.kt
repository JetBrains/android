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

import com.android.build.attribution.analyzers.AnalyzerNotRun
import com.android.build.attribution.analyzers.JetifierCanBeRemoved
import com.android.build.attribution.analyzers.JetifierNotUsed
import com.android.build.attribution.analyzers.JetifierRequiredForLibraries
import com.android.build.attribution.analyzers.JetifierUsageAnalyzerResult
import com.android.build.attribution.analyzers.JetifierUsedCheckRequired
import com.android.build.attribution.ui.BuildAnalyzerBrowserLinks
import com.android.build.attribution.ui.HtmlLinksHandler
import com.android.build.attribution.ui.htmlTextLabelWithLinesWrap
import com.android.build.attribution.ui.insertBRTags
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.android.ide.common.attribution.FullDependencyPath
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI.DEFAULT_STYLE_KEY
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.ListSpeedSearch
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.PlatformIcons.LIBRARY_ICON
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.getListBackground
import com.intellij.util.ui.UIUtil.getListForeground
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class JetifierWarningDetailsFactory(
  private val actionHandlers: ViewActionHandlers
) {

  fun createPage(data: JetifierUsageAnalyzerResult): JPanel = when (data.projectStatus) {
    JetifierUsedCheckRequired -> createJetifierWarningPage(data)
    JetifierCanBeRemoved -> createJetifierWarningPage(data)
    is JetifierRequiredForLibraries -> createJetifierWarningPage(data)
    JetifierNotUsed -> JPanel()
    AnalyzerNotRun -> JPanel()
  }

  private fun createJetifierWarningPage(data: JetifierUsageAnalyzerResult): JPanel {
    val linksHandler = HtmlLinksHandler(actionHandlers)
    val learnMoreLink = linksHandler.externalLink("Learn more", BuildAnalyzerBrowserLinks.JETIIFER_MIGRATE)
    val headerStatus = when (data.projectStatus) {
      JetifierUsedCheckRequired -> "Check if you need Jetifier in your project"
      is JetifierRequiredForLibraries -> "Some project dependencies require Jetifier"
      JetifierCanBeRemoved -> "Jetifier flag can be removed"
      JetifierNotUsed -> error("Warning should not be shown in this state.")
      AnalyzerNotRun -> error("Warning should not be shown in this state.")
    }

    val callToActionLine = when (data.projectStatus) {
      // The following dependencies are recognized as support libraries: com.android.support, com.android.databinding, android.arch.
      JetifierUsedCheckRequired -> "To disable Jetifier your project should have no dependencies on legacy support libraries. " +
                                   "Run check to see if you have any of such dependencies in your project."
      is JetifierRequiredForLibraries -> when (val size = data.projectStatus.checkJetifierResult.dependenciesDependingOnSupportLibs.size) {
                                           1 -> "This check found a declared dependency that requires legacy support libraries. " +
                                                "To disable Jetifier you need to upgrade it to a version that does not require legacy support libraries or find an alternative."
                                           else -> "This check found $size declared dependencies that require legacy support libraries. " +
                                                   "To disable Jetifier you need to upgrade them to versions that do not require legacy support libraries or find alternatives."
                                         } + " Run this check again to include recent changes to project files."
      JetifierCanBeRemoved -> "The last check did not find any dependencies that require Jetifier in your project. " +
                              "You can safely remove the 'android.enableJetifier' flag."
      JetifierNotUsed -> error("Warning should not be shown in this state.")
      AnalyzerNotRun -> error("Warning should not be shown in this state.")
    }

    val contentHtml = """
          <b>$headerStatus</b>
          
          Your project’s gradle.properties file includes 'android.enableJetifier=true'. This flag is needed to enable AndroidX for libraries that don’t support it natively.  $learnMoreLink.
          
          Removing Jetifier could reduce project build time. $callToActionLine
        """.trimIndent().insertBRTags()
    val header = htmlTextLabelWithLinesWrap(contentHtml, linksHandler)
    val runCheckButton = JButton("Run Jetifier check").apply {
      name = "run-check-button"
      addActionListener { actionHandlers.runCheckJetifierTask() }
      putClientProperty(DEFAULT_STYLE_KEY, data.projectStatus !is JetifierCanBeRemoved)
    }
    val removeJetifierButton = JButton("Disable Jetifier").apply {
      name = "disable-jetifier-button"
      toolTipText = "Remove the 'android.enableJetifier' flag from gradle.properties"
      addActionListener { actionHandlers.turnJetifierOffInProperties { RelativePoint.getSouthOf(this) } }
      putClientProperty(DEFAULT_STYLE_KEY, data.projectStatus is JetifierCanBeRemoved)
      isVisible = data.projectStatus is JetifierCanBeRemoved
      // Making this button same size as a bigger "Run Check" button so that they look better when stacked.
      preferredSize = runCheckButton.preferredSize
      maximumSize = runCheckButton.maximumSize
      minimumSize = runCheckButton.minimumSize
    }
    val result = createCheckJetifierResultPresentation(data)

    val buttonsPanel = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      add(removeJetifierButton)
      add(runCheckButton)
    }

    val headerPanel = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.X_AXIS)
      // TODO determine size from font metrics?
      header.maximumSize = JBUI.size(800, Int.MAX_VALUE)
      // Align both components to the bottom.
      buttonsPanel.alignmentY = Component.BOTTOM_ALIGNMENT
      header.alignmentY = Component.BOTTOM_ALIGNMENT
      add(header)
      add(buttonsPanel)
    }

    return JPanel().apply {
      layout = GridBagLayout()
      add(headerPanel, GridBagConstraints().apply {
        gridx = 0
        gridy = 0
        weightx = 1.0
        fill = GridBagConstraints.HORIZONTAL
      })
      add(result, GridBagConstraints().apply {
        gridx = 0
        gridy = 1
        gridwidth = GridBagConstraints.REMAINDER
        weightx = 1.0
        weighty = 1.0
        insets = JBUI.insetsTop(10)
        fill = GridBagConstraints.BOTH
      })
    }
  }

  private fun createCheckJetifierResultPresentation(data: JetifierUsageAnalyzerResult) = JPanel().apply {
    val projectStatus = data.projectStatus
    name = "jetifier-libraries-list"
    layout = BorderLayout()

    val declaredDependenciesListValues = (projectStatus as? JetifierRequiredForLibraries)
                                           ?.checkJetifierResult
                                           ?.dependenciesDependingOnSupportLibs
                                           ?.entries
                                           ?.map { DirectDependencyDescriptor(it) }
                                           ?.sortedBy { it.fullName }
                                         ?: emptyList()
    val declaredDependenciesList = JBList(declaredDependenciesListValues).apply {
      name = "declared-dependencies-list"
      cellRenderer = object : ColoredListCellRenderer<DirectDependencyDescriptor>() {
        override fun customizeCellRenderer(list: JList<out DirectDependencyDescriptor>, value: DirectDependencyDescriptor?, index: Int, selected: Boolean, hasFocus: Boolean) {
          if (value == null) return
          icon = LIBRARY_ICON
          isIconOpaque = true
          setFocusBorderAroundIcon(true)
          background = getListBackground(selected, hasFocus)
          mySelectionForeground = getListForeground(selected, hasFocus)
          if (projectStatus is JetifierRequiredForLibraries) {
            toolTipText = treeToolTip(supportLibrary = value.isSupportLibrary, declaredDependency = true)
          }
          append(value.fullName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }
      }
      border = JBUI.Borders.empty()
      selectionMode = ListSelectionModel.SINGLE_SELECTION
      emptyText.clear()
      when (projectStatus) {
        is JetifierUsedCheckRequired -> {
          emptyText.appendText("Run check", SimpleTextAttributes.LINK_ATTRIBUTES) {
            actionHandlers.runCheckJetifierTask()
          }
          emptyText.appendText(" to see if you need Jetifier in your project.")
        }
        is JetifierCanBeRemoved -> {
          emptyText.appendText("No dependencies require jetifier, ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
          emptyText.appendText("remove 'android.enableJetifier' flag.", SimpleTextAttributes.LINK_ATTRIBUTES) {
            val pointBelowCenter = emptyText.pointBelow.apply { translate(emptyText.preferredSize.width / 2, 0) }
            actionHandlers.turnJetifierOffInProperties { RelativePoint(this, pointBelowCenter) }
          }
        }
      }
      installResultsTableActions(this)
    }

    val tableHeader = SimpleColoredComponent().apply {
      name = "declared-dependencies-header"
      ipad = JBUI.insetsLeft(8)
      val lastUpdatedSuffix = data.lastCheckJetifierBuildTimestamp?.let {
        val lastUpdatedTime = DateFormatUtil.formatDateTime(it)
        " (last updated $lastUpdatedTime)"
      } ?: ""
      append("Declared Dependencies Requiring Jetifier$lastUpdatedSuffix")
      border = JBUI.Borders.customLineBottom(JBUI.CurrentTheme.ToolWindow.headerBorderBackground())
      background = UIUtil.getTreeBackground()
    }
    val treeHeader = SimpleColoredComponent().apply {
      name = "dependency-structure-header"
      ipad = JBUI.insetsLeft(8)
      append("Dependency Structure")
      border = JBUI.Borders.customLineBottom(JBUI.CurrentTheme.ToolWindow.headerBorderBackground())
      background = UIUtil.getTreeBackground()
    }
    val dependencyTreeModel = DefaultTreeModel(null)
    val tree = Tree(dependencyTreeModel).apply {
      isRootVisible = false
      cellRenderer = DependenciesStructureTreeRenderer()
    }

    declaredDependenciesList.selectionModel.addListSelectionListener {
      if (projectStatus is JetifierRequiredForLibraries) {
        val newRoot = DefaultMutableTreeNode()
        val selectedDependency = declaredDependenciesList.selectedValue
        if (selectedDependency != null) {
          val descriptors = selectedDependency.pathToSupportLibrary.map { DependencyDescriptor(it) }
          descriptors.last().supportLibrary = true
          descriptors.first().declaredDependency = true
          descriptors.foldRight(newRoot) { descriptor: DependencyDescriptor, parentNode: DefaultMutableTreeNode ->
            DependencyTreeNode(descriptor).also { parentNode.add(it) }
          }
        }
        dependencyTreeModel.setRoot(newRoot)
        TreeUtil.expandAll(tree)
      }
    }

    val declaredDependenciesListPanel = ScrollPaneFactory.createScrollPane().apply {
      setColumnHeaderView(tableHeader)
      setViewportView(declaredDependenciesList)
    }
    val librariesStructurePanel = ScrollPaneFactory.createScrollPane().apply {
      setColumnHeaderView(treeHeader)
      setViewportView(tree)

    }
    val splitter = OnePixelSplitter(false, 0.5f)
    splitter.firstComponent = declaredDependenciesListPanel
    splitter.secondComponent = librariesStructurePanel

    add(splitter, BorderLayout.CENTER)

    ListSpeedSearch(declaredDependenciesList)
  }

  private fun installResultsTableActions(resultsTable: JBList<DirectDependencyDescriptor>) {
    val findSelectedLibVersionDeclarationAction = actionHandlers.createFindSelectedLibVersionDeclarationAction { resultsTable.selectedValue }
    DefaultActionGroup().let { group ->
      group.add(findSelectedLibVersionDeclarationAction)
      PopupHandler.installPopupMenu(resultsTable, group, ActionPlaces.POPUP)
    }
    object : DoubleClickListener() {
      override fun onDoubleClick(e: MouseEvent): Boolean {
        ActionManager.getInstance().tryToExecute(findSelectedLibVersionDeclarationAction, e, resultsTable, null, true)
        return true
      }
    }.installOn(resultsTable)
    resultsTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).apply {
      put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enter")
      put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "enter")
    }
    resultsTable.actionMap.apply {
      put("enter", object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
          ActionManager.getInstance().tryToExecute(findSelectedLibVersionDeclarationAction, null, resultsTable, null, true)
        }
      })
    }
  }


  private class DependencyTreeNode(val descriptor: DependencyDescriptor) : DefaultMutableTreeNode(descriptor) {
    override fun toString(): String {
      return descriptor.fullName
    }
  }

  class DependencyDescriptor(
    /** Full dependency name in 'group:name:version' format. */
    val fullName: String,
  ) {
    var declaredDependency: Boolean = false
    var supportLibrary: Boolean = false
    val prefix: String
      get() = when {
        declaredDependency -> ""
        supportLibrary -> "depends on "
        else -> "via "
      }
    val tooltip: String?
      get() = treeToolTip(supportLibrary, declaredDependency)
  }

  data class DirectDependencyDescriptor(
    val fullName: String,
    val projects: List<String>,
    val pathToSupportLibrary: List<String>
  ) {
    val isSupportLibrary: Boolean get() = pathToSupportLibrary.size == 1

    constructor(resultEntry: Map.Entry<String, List<FullDependencyPath>>): this(
      resultEntry.key,
      resultEntry.value.map { it.projectPath },
      resultEntry.value.first().dependencyPath.elements
    )
  }

  private class DependenciesStructureTreeRenderer : NodeRenderer() {

    override fun customizeCellRenderer(tree: JTree,
                                       value: Any?,
                                       selected: Boolean,
                                       expanded: Boolean,
                                       leaf: Boolean,
                                       row: Int,
                                       hasFocus: Boolean) {
      val node = value as DefaultMutableTreeNode
      val userObj = node.userObject as? DependencyDescriptor
      if (userObj == null) {
        super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus)
      }
      else {
        icon = LIBRARY_ICON
        append(userObj.prefix, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        append(userObj.fullName)
        toolTipText = userObj.tooltip
      }
    }
  }
}

private fun treeToolTip(supportLibrary: Boolean, declaredDependency: Boolean): String? = when {
  supportLibrary -> "This is a legacy support library. All dependencies on such libraries should be removed before disabling jetifier."
  declaredDependency -> "This library depends on a legacy support library. In order to disable Jetifier, please, migrate to a version " +
                        "that no longer depends on legacy support libraries."
  else -> null
}

