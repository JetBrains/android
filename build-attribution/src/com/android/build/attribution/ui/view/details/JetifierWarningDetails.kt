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
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.android.ide.common.attribution.FullDependencyPath
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI.DEFAULT_STYLE_KEY
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.ListSpeedSearch
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBList
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Alarm
import com.intellij.util.PlatformIcons.LIBRARY_ICON
import com.intellij.util.text.JBDateFormat
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.getListBackground
import com.intellij.util.ui.UIUtil.getListForeground
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
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
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class JetifierWarningDetailsView(
  private val data: JetifierUsageAnalyzerResult,
  private val actionHandlers: ViewActionHandlers,
  private val disposable: Disposable
) {


  val pagePanel: JPanel = JPanel()

  private val headerTextArea: JEditorPane = run {
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
      JetifierUsedCheckRequired -> """
        Removing Jetifier could reduce project build time.
        To disable Jetifier your project should have no dependencies on legacy support libraries.
        Run check to see if you have any of such dependencies in your project.
        """.trimIndent()
      is JetifierRequiredForLibraries ->
        when (val size = data.projectStatus.checkJetifierResult.dependenciesDependingOnSupportLibs.size) {
          1 -> """
            This check found <b>1 declared dependency</b> that requires legacy support libraries.
            Removing Jetifier could reduce project build time.
            To disable Jetifier you need to upgrade it to a version that does not require legacy support libraries or find an alternative.
            Run this check again to include recent changes to project files.
            """.trimIndent()
          else -> """
            This check found <b>$size declared dependencies</b> that require legacy support libraries.
            Removing Jetifier could reduce project build time.
            To disable Jetifier you need to upgrade them to versions that do not require legacy support libraries or find alternatives.
            Run this check again to include recent changes to project files.
            """.trimIndent()
        }
      JetifierCanBeRemoved -> """
        This check found <b>0 declared dependencies</b> that require Jetifier in your project.
        You can safely remove the 'android.enableJetifier' flag.
      """.trimIndent()
      JetifierNotUsed -> error("Warning should not be shown in this state.")
      AnalyzerNotRun -> error("Warning should not be shown in this state.")
    }

    val contentHtml = """
          <b>$headerStatus</b><br/>
          <br/>
          Your project’s gradle.properties file includes 'android.enableJetifier=true'.
          This flag is needed to enable AndroidX for libraries that don’t support it natively.
          $learnMoreLink.<br/>
          <br/>
          $callToActionLine<br/>
        """.trimIndent()
    htmlTextLabelWithLinesWrap(contentHtml, linksHandler)
  }

  private val runCheckButton = JButton("Run Jetifier check").apply {
    name = "run-check-button"
    addActionListener { actionHandlers.runCheckJetifierTask() }
    putClientProperty(DEFAULT_STYLE_KEY, data.projectStatus !is JetifierCanBeRemoved)
  }

  private val removeJetifierButton = JButton("Disable Jetifier").apply {
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

  private val declaredDependenciesList = JBList(data.createDeclaredDependenciesList()).apply {
    name = "declared-dependencies-list"
    cellRenderer = object : ColoredListCellRenderer<DirectDependencyDescriptor>() {
      override fun customizeCellRenderer(list: JList<out DirectDependencyDescriptor>,
                                         value: DirectDependencyDescriptor?,
                                         index: Int,
                                         selected: Boolean,
                                         hasFocus: Boolean) {
        if (value == null) return
        icon = LIBRARY_ICON
        isIconOpaque = true
        setFocusBorderAroundIcon(true)
        background = getListBackground(selected, hasFocus)
        mySelectionForeground = getListForeground(selected, hasFocus)
        if (data.projectStatus is JetifierRequiredForLibraries) {
          toolTipText = treeToolTip(supportLibrary = value.isSupportLibrary, declaredDependency = true)
        }
        append(value.fullName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
      }
    }
    border = JBUI.Borders.empty()
    selectionMode = ListSelectionModel.SINGLE_SELECTION
    selectionModel.addListSelectionListener { onDeclaredDependencySelection() }

    emptyText.clear()
    when (data.projectStatus) {
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
      else -> { }
    }
    installResultsTableActions(this)
    ListSpeedSearch.installOn(this)
  }

  private val tableHeader = SimpleColoredComponent().apply {
    name = "declared-dependencies-header"
    ipad = JBUI.insetsLeft(8)
    // Text set in refreshUI.
    border = JBUI.Borders.customLineBottom(JBUI.CurrentTheme.ToolWindow.headerBorderBackground())
    background = UIUtil.getTreeBackground()
  }

  private val outdatedResultsBanner = JPanel().apply {
    name = "outdated-results-banner"
    isVisible = data.isPreviouslySavedResultReused()
    layout = HorizontalLayout(10)
    border = JBUI.Borders.empty(4, 8)
    add(JLabel("Showing previously saved results."), HorizontalLayout.LEFT)
    add(ActionLink("Re-run check.") { actionHandlers.runCheckJetifierTask() }, HorizontalLayout.RIGHT)
    background = JBUI.CurrentTheme.NotificationWarning.backgroundColor()
    foreground = JBUI.CurrentTheme.NotificationWarning.foregroundColor()
  }

  private val treeHeader = SimpleColoredComponent().apply {
    name = "dependency-structure-header"
    ipad = JBUI.insetsLeft(8)
    append("Dependency Structure")
    border = JBUI.Borders.customLineBottom(JBUI.CurrentTheme.ToolWindow.headerBorderBackground())
    background = UIUtil.getTreeBackground()
  }

  private val dependencyStructureTree = Tree().apply {
    isRootVisible = false
    cellRenderer = DependenciesStructureTreeRenderer()
    model = DefaultTreeModel(null)
  }

  private val resultPanel = JPanel().apply {
    name = "jetifier-libraries-list"
    layout = BorderLayout()

    val declaredDependenciesListPanel = ScrollPaneFactory.createScrollPane().apply {
      setColumnHeaderView(tableHeader)
      val listWithBanner = BorderLayoutPanel()
      setViewportView(listWithBanner.addToTop(outdatedResultsBanner).addToCenter(declaredDependenciesList))
    }
    val librariesStructurePanel = ScrollPaneFactory.createScrollPane().apply {
      setColumnHeaderView(treeHeader)
      setViewportView(dependencyStructureTree)

    }
    val splitter = OnePixelSplitter(false, 0.5f)
    splitter.firstComponent = declaredDependenciesListPanel
    splitter.secondComponent = librariesStructurePanel

    add(splitter, BorderLayout.CENTER)
  }

  init {
    val buttonsPanel = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      add(removeJetifierButton)
      add(runCheckButton)
    }

    val headerPanel = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.X_AXIS)
      // TODO determine size from font metrics?
      headerTextArea.maximumSize = JBUI.size(800, Int.MAX_VALUE)
      // Align both components to the bottom.
      buttonsPanel.alignmentY = Component.BOTTOM_ALIGNMENT
      headerTextArea.alignmentY = Component.BOTTOM_ALIGNMENT
      add(headerTextArea)
      add(buttonsPanel)
    }

    pagePanel.layout = GridBagLayout()
    pagePanel.add(headerPanel, GridBagConstraints().apply {
      gridx = 0
      gridy = 0
      weightx = 1.0
      fill = GridBagConstraints.HORIZONTAL
    })
    pagePanel.add(resultPanel, GridBagConstraints().apply {
      gridx = 0
      gridy = 1
      gridwidth = GridBagConstraints.REMAINDER
      weightx = 1.0
      weighty = 1.0
      insets = JBUI.insetsTop(10)
      fill = GridBagConstraints.BOTH
    })

    setupRefresh()
  }

  private fun setupRefresh() {
    // Create alarm for auto-refreshes that has page as activation component, so requests are scheduled only while it is visible.
    val refreshAlarm = Alarm(pagePanel, disposable)
    // Refresh ui state and schedule next refresh in 30s.
    object : Runnable {
      override fun run() {
        refreshUi()
        refreshAlarm.addRequest(this, 30000)
      }
    }.run()
    // Also refresh right away on page reopening, otherwise there will be 30s lag until next alarm triggers.
    UiNotifyConnector.installOn(pagePanel, object : Activatable {
      override fun showNotify() {
        refreshUi()
      }
    })
  }

  private fun refreshUi() {
    tableHeader.let {
      it.clear()
      val lastUpdatedSuffix = data.lastCheckJetifierBuildTimestamp?.let {
        val lastUpdatedTime = StringUtil.decapitalize(JBDateFormat.getFormatter().formatPrettyDateTime(it))
        " (updated $lastUpdatedTime)"
      } ?: ""
      it.append("Declared Dependencies Requiring Jetifier$lastUpdatedSuffix")
    }
  }

  private fun onDeclaredDependencySelection() {
    if (data.projectStatus is JetifierRequiredForLibraries) {
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
      (dependencyStructureTree.model as? DefaultTreeModel)?.setRoot(newRoot)
      TreeUtil.expandAll(dependencyStructureTree)
    }
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

  private fun JetifierUsageAnalyzerResult.isPreviouslySavedResultReused(): Boolean =
    !checkJetifierBuild && (projectStatus is JetifierCanBeRemoved || projectStatus is JetifierRequiredForLibraries)


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

    constructor(resultEntry: Map.Entry<String, List<FullDependencyPath>>) : this(
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

private fun JetifierUsageAnalyzerResult.createDeclaredDependenciesList(): List<JetifierWarningDetailsView.DirectDependencyDescriptor> {
  return ((projectStatus as? JetifierRequiredForLibraries) ?: return emptyList())
     .checkJetifierResult
     .dependenciesDependingOnSupportLibs
     .entries
     .map { JetifierWarningDetailsView.DirectDependencyDescriptor(it) }
     .sortedBy { it.fullName }
}

