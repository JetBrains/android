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
import com.android.build.attribution.analyzers.JetifierUsageProjectStatus
import com.android.build.attribution.analyzers.JetifierUsedCheckRequired
import com.android.build.attribution.ui.BuildAnalyzerBrowserLinks
import com.android.build.attribution.ui.HtmlLinksHandler
import com.android.build.attribution.ui.htmlTextLabelWithLinesWrap
import com.android.build.attribution.ui.insertBRTags
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI.DEFAULT_STYLE_KEY
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.components.JBPanel
import com.intellij.ui.table.TableView
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.PlatformIcons.LIBRARY_ICON
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.ListSelectionModel
import javax.swing.table.TableCellRenderer
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
      addActionListener { actionHandlers.runCheckJetifierTask() }
      putClientProperty(DEFAULT_STYLE_KEY, true)
    }
    val result = createCheckJetifierResultPresentation(data.projectStatus)

    val headerPanel = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.X_AXIS)
      // TODO determine size from font metrics?
      header.maximumSize = JBUI.size(800, Int.MAX_VALUE)
      // Align both components to the bottom.
      runCheckButton.alignmentY = Component.BOTTOM_ALIGNMENT
      header.alignmentY = Component.BOTTOM_ALIGNMENT
      add(header)
      add(runCheckButton)
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

  private fun createCheckJetifierResultPresentation(projectState: JetifierUsageProjectStatus) = JPanel().apply {
    name = "jetifier-libraries-list"
    layout = BorderLayout()
    val resultsTable = TableView(object : ListTableModel<String>() {
      init {
        columnInfos = arrayOf(object : ColumnInfo<String, String>("Declared Dependencies Requiring Jetifier") {
          override fun valueOf(item: String?): String? {
            return item
          }

          override fun getRenderer(item: String?): TableCellRenderer {
            return object : ColoredTableCellRenderer() {
              override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
                icon = LIBRARY_ICON
                isIconOpaque = true
                setFocusBorderAroundIcon(true)
                setPaintFocusBorder(false)
                if (projectState is JetifierRequiredForLibraries) {
                  val supportLibrary = projectState.checkJetifierResult.dependenciesDependingOnSupportLibs[value]?.dependencyPath?.elements?.size == 1
                  toolTipText = treeToolTip(supportLibrary = supportLibrary, declaredDependency = true)
                }
                append(value.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
              }
            }
          }
        })
        isSortable = true
      }
    })

    resultsTable.resetDefaultFocusTraversalKeys()
    resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    when (projectState) {
      is JetifierRequiredForLibraries -> {
        resultsTable.listTableModel.items = projectState.checkJetifierResult.dependenciesDependingOnSupportLibs.keys.sorted()
        resultsTable.updateColumnSizes()
      }
      is JetifierUsedCheckRequired -> {
        resultsTable.emptyText.apply {
          appendText("Run check", SimpleTextAttributes.LINK_ATTRIBUTES) {
            actionHandlers.runCheckJetifierTask()
          }
          appendText(" to see if you need Jetifier in your project.")
        }
      }
      is JetifierCanBeRemoved -> {
        resultsTable.emptyText.apply {
          clear()
          appendText("No dependencies require jetifier, ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
          appendText("remove 'android.enableJetifier' flag.", SimpleTextAttributes.LINK_ATTRIBUTES) {
            actionHandlers.turnJetifierOffInProperties()
          }
        }
      }
    }
    resultsTable.autoCreateRowSorter = true
    resultsTable.setShowGrid(false)
    resultsTable.tableHeader.reorderingAllowed = false

    DefaultActionGroup().let { group ->
      group.add(actionHandlers.createFindSelectedLibVersionDeclarationAction { resultsTable.selection.singleOrNull() })
      PopupHandler.installPopupMenu(resultsTable, group, ActionPlaces.POPUP)
    }
    val dependencyTreeModel = DefaultTreeModel(null)
    val treeHeader = JBPanel<JBPanel<*>>().apply {
      layout = BorderLayout()
      background = UIUtil.getTreeBackground()
      border = JBUI.Borders.customLineBottom(JBUI.CurrentTheme.ToolWindow.headerBorderBackground())
      val label = JLabel("Dependency Structure")
      label.border = JBUI.Borders.emptyLeft(8)
      withPreferredHeight(28)
      withMaximumHeight(28)
      withMinimumHeight(28)
      add(label, BorderLayout.CENTER)
    }
    val treeCellRenderer = DependenciesStructureTreeRenderer()
    val tree = Tree(dependencyTreeModel).apply {
      isRootVisible = false
      rowHeight = JBUI.scale(24)
      cellRenderer = treeCellRenderer
    }

    resultsTable.selectionModel.addListSelectionListener {
      if (projectState is JetifierRequiredForLibraries) {
        val newRoot = DefaultMutableTreeNode()
        val selectedDependency = resultsTable.selection.singleOrNull()
        if (selectedDependency != null) {
          projectState.checkJetifierResult.dependenciesDependingOnSupportLibs[selectedDependency]?.let {
            val descriptors = it.dependencyPath.elements.map { DependencyDescriptor(it) }
            descriptors.last().supportLibrary = true
            descriptors.first().declaredDependency = true
            descriptors.foldRight(newRoot) { descriptor: DependencyDescriptor, parentNode: DefaultMutableTreeNode ->
              DependencyTreeNode(descriptor).also { parentNode.add(it) }
            }
          }
        }
        dependencyTreeModel.setRoot(newRoot)
        TreeUtil.expandAll(tree)
      }
    }

    val librariesStructurePanel = ScrollPaneFactory.createScrollPane().apply {
      setColumnHeaderView(treeHeader)
      setViewportView(tree)

    }
    val splitter = OnePixelSplitter(false, 0.5f)
    splitter.firstComponent = ScrollPaneFactory.createScrollPane(resultsTable)
    splitter.secondComponent = librariesStructurePanel

    add(splitter, BorderLayout.CENTER)

    TableSpeedSearch(resultsTable)
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

  private class DependenciesStructureTreeRenderer() : NodeRenderer() {

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

