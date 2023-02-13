/*
 * Copyright (C) 2020 The Android Open Source Project
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
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE") // TODO: remove usage of sun.swing.DefaultLookup.
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.projectsystem.TestArtifactSearchScopes
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfigurationType
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ActionPlaces
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultStats
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultsTreeNode
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.getFullTestCaseName
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.getFullTestClassName
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.getRoundedTotalDuration
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.getSummaryResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.plus
import com.android.tools.idea.testartifacts.instrumented.testsuite.logging.AndroidTestSuiteLogger
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuiteResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.benchmark.BenchmarkOutput
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.getName
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.state.AndroidTestResultsUserPreferencesManager
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.ParallelAndroidTestReportUiEvent
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.testframework.sm.runner.ui.SMPoolOfTestIcons
import com.intellij.icons.AllIcons
import com.intellij.ide.CommonActionsManager
import com.intellij.ide.DataManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.ide.OccurenceNavigator
import com.intellij.ide.actions.EditSourceAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.util.ColorProgressBar
import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.Navigatable
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.ui.RelativeFont
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dualView.TreeTableView
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo
import com.intellij.ui.treeStructure.treetable.TreeTableCellRenderer
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import sun.swing.DefaultLookup
import java.awt.Color
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.time.Duration
import java.util.Vector
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.ListSelectionModel
import javax.swing.SortOrder
import javax.swing.SwingConstants
import javax.swing.event.ListSelectionEvent
import javax.swing.event.TableModelEvent
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.JTableHeader
import javax.swing.table.TableCellRenderer
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath
import kotlin.math.max

/**
 * A table to display Android test results. Test results are grouped by device and test case. The column is a device name
 * and the row is a test case.
 */
class AndroidTestResultsTableView(listener: AndroidTestResultsTableListener,
                                  javaPsiFacade: JavaPsiFacade,
                                  module: Module?,
                                  scopes: TestArtifactSearchScopes?,
                                  logger: AndroidTestSuiteLogger,
                                  androidTestResultsUserPreferencesManager: AndroidTestResultsUserPreferencesManager?) {
  private val myModel = AndroidTestResultsTableModel()
  private val myTableView =
    AndroidTestResultsTableViewComponent(myModel, listener, javaPsiFacade, module, scopes, logger, androidTestResultsUserPreferencesManager)
  private val myTableViewContainer = JBScrollPane(myTableView)
  private val failedTestsNavigator = FailedTestsNavigator(myTableView)

  @get:UiThread
  val preferredTableWidth: Int
    get() = myTableView.preferredSize.width

  @get:UiThread
  val rootResultsNode: AndroidTestResultsTreeNode = myModel.myRootAggregationRow.toAndroidTestResultsTreeNode()

  /**
   * Adds a device to the table.
   *
   * @param device a new column will be added to the table for the given device
   */
  @UiThread
  fun addDevice(device: AndroidDevice) {
    myModel.addDeviceColumn(device)
    refreshTable()
  }

  @UiThread
  fun setTestSuiteResultForDevice(device: AndroidDevice, result: AndroidTestSuiteResult?) {
    myModel.myRootAggregationRow.setTestSuiteResultForDevice(device, result)
    refreshTable()
  }

  /**
   * Adds a test case to the table. If you change value of any properties of [testCase] later,
   * you need to call [refreshTable] to reflect changes to the table.
   *
   * @param device a device which the given [testCase] belongs to
   * @param testCase a test case to be displayed in the table
   * @return a sequence of AndroidTestResults which are added or updated
   */
  @UiThread
  fun addTestCase(device: AndroidDevice, testCase: AndroidTestCase): Sequence<AndroidTestResults> {
    val testRow = myModel.addTestResultsRow(device, testCase)
    refreshTable()
    myTableView.tree.expandPath(TreeUtil.getPath(myModel.myRootAggregationRow, testRow.parent))
    return sequence<AndroidTestResults> {
      yield(testRow)
      var parent = testRow.parent
      while (parent != null) {
        if (parent is AndroidTestResults) {
          yield(parent)
        }
        parent = parent.parent
      }
    }
  }

  /**
   * Sets a filter to hide specific rows from this table.
   *
   * @param filter a predicate which returns false for an item to be hidden
   */
  @UiThread
  fun setRowFilter(filter: (AndroidTestResults) -> Boolean) {
    myModel.setRowFilter(filter)
    refreshTable()
  }

  /**
   * Sets a filter to hide specific columns from this table.
   *
   * @param filter a predicate which returns false for an column to be hidden
   */
  @UiThread
  fun setColumnFilter(filter: (AndroidDevice) -> Boolean) {
    myModel.setColumnFilter(filter)
    refreshTable()
  }

  /**
   * Refreshes and redraws the table.
   */
  @UiThread
  fun refreshTable() {
    myTableView.refreshTable()
  }

  /**
   * Selects the root item.
   */
  @UiThread
  fun selectRootItem() {
    myTableView.setColumnSelectionInterval(0, 0)
    myTableView.setRowSelectionInterval(0, 0)
  }

  /**
   * Clears currently selected items in the table.
   */
  @UiThread
  fun clearSelection() {
    myTableView.clearSelection()
    myTableView.resetLastReportedValues()
  }

  @UiThread
  fun selectAndroidTestCase(testCase: AndroidTestCase) {
    myModel.getTestResultsRow(testCase)?.let { row ->
      myTableView.addSelection(row)
    }
  }

  /**
   * Returns a root component of the table view.
   */
  @UiThread
  fun getComponent(): JComponent = myTableViewContainer

  /**
   * Returns a component which should request a user focus.
   */
  @UiThread
  fun getPreferredFocusableComponent(): JComponent = myTableView

  /**
   * Creates an action which expands all items in the test results tree table.
   */
  @UiThread
  fun createExpandAllAction(): AnAction {
    val treeExpander = object: DefaultTreeExpander(myTableView.tree) {
      override fun canCollapse(): Boolean = true
      override fun canExpand(): Boolean = true
    }
    return CommonActionsManager.getInstance().createExpandAllAction(treeExpander, myTableView.tree)
  }

  /**
   * Creates an action which expands all items in the test results tree table.
   */
  @UiThread
  fun createCollapseAllAction(): AnAction {
    val treeExpander = object: DefaultTreeExpander(myTableView.tree) {
      override fun canCollapse(): Boolean = true
      override fun canExpand(): Boolean = true
      override fun collapseAll(tree: JTree, keepSelectionLevel: Int) {
        collapseAll(tree, false, keepSelectionLevel)
      }
    }
    return CommonActionsManager.getInstance().createCollapseAllAction(treeExpander, myTableView.tree)
  }

  @UiThread
  fun createNavigateToPreviousFailedTestAction(): AnAction {
    return CommonActionsManager.getInstance().createPrevOccurenceAction(failedTestsNavigator)
  }

  @UiThread
  fun createNavigateToNextFailedTestAction(): AnAction {
    return CommonActionsManager.getInstance().createNextOccurenceAction(failedTestsNavigator)
  }

  /**
   * Returns an internal model class for testing.
   */
  @VisibleForTesting
  fun getModelForTesting(): ListTreeTableModelOnColumns = myModel

  /**
   * Returns an internal view class for testing.
   */
  @VisibleForTesting
  fun getTableViewForTesting(): TreeTableView = myTableView
}

/**
 * A listener to receive events occurred in AndroidTestResultsTable.
 */
interface AndroidTestResultsTableListener {
  /**
   * Called when a user selects a test results row. This method is only invoked when
   * the selected item is changed. e.g. If a user clicks the same row twice, the callback
   * is invoked only for the first time.
   *
   * @param selectedResults results which a user selected
   * @param selectedDevice Android device which a user selected
   *   or null if a user clicks on non-device specific column
   */
  fun onAndroidTestResultsRowSelected(selectedResults: AndroidTestResults,
                                      selectedDevice: AndroidDevice?)
}

private class FailedTestsNavigator(private val treetableView: AndroidTestResultsTableViewComponent) : OccurenceNavigator {
  override fun getNextOccurenceActionName(): String = ExecutionBundle.message("next.faled.test.action.name")
  override fun getPreviousOccurenceActionName(): String = ExecutionBundle.message("prev.faled.test.action.name")

  override fun hasNextOccurence(): Boolean {
    return getNextFailedTestNode() != null
  }

  override fun goNextOccurence(): OccurenceNavigator.OccurenceInfo? {
    val nextNode = getNextFailedTestNode() ?: return null
    treetableView.tree.selectionPath = TreePath(nextNode.path)
    treetableView.addSelection(nextNode)
    return OccurenceNavigator.OccurenceInfo(treetableView.getPsiElement(nextNode) as? Navigatable, -1, -1)
  }

  override fun hasPreviousOccurence(): Boolean {
    return getPreviousFailedTestNode() != null
  }

  override fun goPreviousOccurence(): OccurenceNavigator.OccurenceInfo? {
    val prevNode = getPreviousFailedTestNode() ?: return null
    treetableView.addSelection(prevNode)
    return OccurenceNavigator.OccurenceInfo(treetableView.getPsiElement(prevNode) as? Navigatable, -1, -1)
  }

  private fun getNextFailedTestNode(): AndroidTestResultsRow? {
    return getSequence(DefaultMutableTreeNode::getNextNode).firstOrNull()
  }

  private fun getPreviousFailedTestNode(): AndroidTestResultsRow? {
    return getSequence(DefaultMutableTreeNode::getPreviousNode).firstOrNull()
  }

  private fun getSequence(next: DefaultMutableTreeNode.() -> DefaultMutableTreeNode?): Sequence<AndroidTestResultsRow> {
    return sequence<AndroidTestResultsRow> {
      if (treetableView.rowCount == 0) {
        return@sequence
      }
      val selectedNode = (treetableView.selectedObject ?: treetableView.getValueAt(0, 0)) as? DefaultMutableTreeNode ?: return@sequence
      var node = selectedNode.next()
      while (node != null) {
        if (node is AndroidTestResultsRow && node.getTestResultSummary() == AndroidTestCaseResult.FAILED) {
          yield(node)
        }
        node = node.next()
      }
    }
  }
}

/**
 * Returns an icon which represents a given [androidTestResult].
 */
@JvmOverloads
fun getIconFor(androidTestResult: AndroidTestCaseResult?,
               animationEnabled: Boolean = true): Icon? {
  return when(androidTestResult) {
    AndroidTestCaseResult.PASSED -> AllIcons.RunConfigurations.TestPassed
    AndroidTestCaseResult.SKIPPED -> AllIcons.RunConfigurations.TestIgnored
    AndroidTestCaseResult.FAILED -> AllIcons.RunConfigurations.TestFailed
    AndroidTestCaseResult.IN_PROGRESS -> if (animationEnabled) {
      SMPoolOfTestIcons.RUNNING_ICON
    } else {
      AllIcons.Process.Step_1
    }
    AndroidTestCaseResult.CANCELLED -> SMPoolOfTestIcons.TERMINATED_ICON
    else -> null
  }
}

/**
 * Returns a color which represents a given [androidTestResult].
 */
fun getColorFor(androidTestResult: AndroidTestCaseResult?): Color? {
  return when(androidTestResult) {
    AndroidTestCaseResult.PASSED -> ColorProgressBar.GREEN
    AndroidTestCaseResult.FAILED -> ColorProgressBar.RED_TEXT
    AndroidTestCaseResult.SKIPPED -> SKIPPED_TEST_TEXT_COLOR
    AndroidTestCaseResult.CANCELLED -> ColorProgressBar.RED_TEXT
    else -> null
  }
}

private val SKIPPED_TEST_TEXT_COLOR = JBColor(Gray._130, Gray._200)

/**
 * An internal swing view component implementing AndroidTestResults table view.
 */
private class AndroidTestResultsTableViewComponent(private val model: AndroidTestResultsTableModel,
                                                   private val listener: AndroidTestResultsTableListener,
                                                   private val javaPsiFacade: JavaPsiFacade,
                                                   private val module: Module?,
                                                   private val scopes: TestArtifactSearchScopes?,
                                                   private val logger: AndroidTestSuiteLogger,
                                                   private val androidTestResultsUserPreferencesManager: AndroidTestResultsUserPreferencesManager?)
  : TreeTableView(model), DataProvider {

  private var myLastReportedResults: AndroidTestResults? = null
  private var myLastReportedDevice: AndroidDevice? = null

  private var mySortOrder: SortOrder = SortOrder.UNSORTED

  init {
    putClientProperty(AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true)
    selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
    autoResizeMode = AUTO_RESIZE_OFF
    tableHeader.resizingAllowed = true
    tableHeader.reorderingAllowed = false
    val originalDefaultHeaderRenderer = tableHeader.defaultRenderer
    tableHeader.defaultRenderer = object: TableCellRenderer {
      override fun getTableCellRendererComponent(table: JTable,
                                                 value: Any,
                                                 isSelected: Boolean,
                                                 hasFocus: Boolean,
                                                 row: Int,
                                                 column: Int): Component {
        val renderComponent = originalDefaultHeaderRenderer.getTableCellRendererComponent(
          table, value, isSelected, hasFocus, row, column)
        val label = renderComponent as? JLabel ?: return renderComponent
        if (column > 0) {
          label.horizontalAlignment = SwingConstants.CENTER
          label.border = JBUI.Borders.empty()
        }
        return renderComponent
      }
    }
    showHorizontalLines = false
    tree.isRootVisible = true
    tree.showsRootHandles = true
    tree.cellRenderer = object: ColoredTreeCellRenderer() {
      private val mySelectedTextAttributes =
        SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, UIUtil.getTreeSelectionForeground(true))
      override fun customizeCellRenderer(tree: JTree,
                                         value: Any?,
                                         selected: Boolean,
                                         expanded: Boolean,
                                         leaf: Boolean,
                                         row: Int,
                                         hasFocus: Boolean) {
        val results = value as? AndroidTestResults ?: return
        val text = when {
          results.methodName.isNotBlank() -> {
            results.methodName
          }
          results.className.isNotBlank() -> {
            results.className
          }
          else -> {
            "Test Results"
          }
        }
        // This cell renderer is used inside the TreeTableView, so we need
        // to check the TableView's focus.
        val reallyHasFocus = this@AndroidTestResultsTableViewComponent.hasFocus()
        val textAttributes = if(selected && reallyHasFocus) {
          mySelectedTextAttributes
        } else {
          SimpleTextAttributes.REGULAR_ATTRIBUTES
        }
        append(text, textAttributes)
        icon = getIconFor(results.getTestResultSummary())
      }
    }

    TreeUtil.installActions(tree)
    PopupHandler.installPopupMenu(this, IdeActions.GROUP_TESTTREE_POPUP, ActionPlaces.ANDROID_TEST_SUITE_TABLE)
    addMouseListener(object: MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        logger.reportClickInteraction(ParallelAndroidTestReportUiEvent.UiElement.TEST_SUITE_VIEW_TABLE_ROW)
        when (e?.clickCount) {
          2 -> {
            EditSourceAction().actionPerformed(
              AnActionEvent.createFromInputEvent(
                e, ActionPlaces.ANDROID_TEST_SUITE_TABLE, null,
                DataManager.getInstance().getDataContext(this@AndroidTestResultsTableViewComponent)))
          }
        }
      }
    })
  }

  val selectedObject: AndroidTestResults?
    get() = selection?.firstOrNull() as? AndroidTestResults

  override fun valueChanged(event: ListSelectionEvent) {
    super.valueChanged(event)
    handleSelectionChanged(event)
  }

  override fun columnSelectionChanged(event: ListSelectionEvent) {
    super.columnSelectionChanged(event)
    handleSelectionChanged(event)
  }

  override fun getScrollableTracksViewportWidth(): Boolean {
    return preferredSize.width < parent.width
  }

  private fun handleSelectionChanged(event: ListSelectionEvent) {
    // Ignore intermediate values.
    if (event.valueIsAdjusting) {
      return
    }

    selectedObject?.let {
      notifyAndroidTestResultsRowSelectedIfValueChanged(
        it,
        (model.columnInfos.getOrNull(selectedColumn) as? AndroidTestResultsColumn)?.device)
    }
  }

  private fun notifyAndroidTestResultsRowSelectedIfValueChanged(results: AndroidTestResults,
                                                                device: AndroidDevice?) {
    if (myLastReportedResults == results && myLastReportedDevice == device) {
      return
    }
    myLastReportedResults = results
    myLastReportedDevice = device
    listener.onAndroidTestResultsRowSelected(results, device)
  }

  fun resetLastReportedValues() {
    myLastReportedResults = null
    myLastReportedDevice = null
  }

  override fun tableChanged(e: TableModelEvent?) {
    // JDK-4276786: JTable doesn't preserve the selection so we manually restore the previous selection.
    val prevSelectedObject = selectedObject
    super.tableChanged(e)
    prevSelectedObject?.let { addSelection(it) }
  }

  override fun getData(dataId: String): Any? {
    return when {
      PlatformCoreDataKeys.BGT_DATA_PROVIDER.`is`(dataId) -> {
        DataProvider { slowId -> getSlowData(slowId) }
      }
      CommonDataKeys.PROJECT.`is`(dataId) -> {
        javaPsiFacade.project
      }
      RunConfiguration.DATA_KEY.`is`(dataId) -> {
        return AndroidTestRunConfiguration(javaPsiFacade.project, AndroidTestRunConfigurationType.getInstance().factory)
      }

      else -> null
    }
  }

  private fun getSlowData(dataId: String): Any? {
    return when {
      CommonDataKeys.PSI_ELEMENT.`is`(dataId) -> {
        val selectedTestResults = selectedObject ?: return null
        getPsiElement(selectedTestResults)
      }

      Location.DATA_KEY.`is`(dataId) -> {
        val psiElement = getSlowData(CommonDataKeys.PSI_ELEMENT.name) as? PsiElement ?: return null
        PsiLocation.fromPsiElement(psiElement, module)
      }

      else -> null
    }
  }

  private val myPsiElementCache: MutableMap<AndroidTestResults, Lazy<PsiElement?>> = mutableMapOf()
  private val myParameterizedTestMethodNameRegex: Regex = "\\[.*\\]$".toRegex()

  fun getPsiElement(androidTestResults: AndroidTestResults): PsiElement? {
    val androidTestSourceScope = scopes?.androidTestSourceScope ?: return null
    return myPsiElementCache.getOrPut(androidTestResults) {
      lazy<PsiElement?> {
        val testClasses = androidTestResults.getFullTestClassName().let {
          javaPsiFacade.findClasses(it, androidTestSourceScope)
        }
        testClasses.firstNotNullOfOrNull {
          it.findMethodsByName(androidTestResults.methodName, true).firstOrNull()
        }
        ?: testClasses.firstNotNullOfOrNull {
          it.findMethodsByName(androidTestResults.methodName.replace(myParameterizedTestMethodNameRegex, ""), true).firstOrNull()
        }
        ?: testClasses.firstOrNull()
      }
    }.value
  }

  override fun getCellRenderer(row: Int, column: Int): TableCellRenderer? {
    getColumnInfo(column).getRenderer(getRowElement(row))?.let { return it }
    getColumnModel().getColumn(column).cellRenderer?.let { return it }
    return getDefaultRenderer(getColumnClass(column))
  }

  override fun createTableRenderer(treeTableModel: TreeTableModel): TreeTableCellRenderer {
    return object: TreeTableCellRenderer(this, tree) {
      override fun getTableCellRendererComponent(table: JTable,
                                                 value: Any,
                                                 isSelected: Boolean,
                                                 hasFocus: Boolean,
                                                 row: Int,
                                                 column: Int): Component {
        return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column).also {
          tree.border = null
        }
      }
    }
  }

  override fun createDefaultTableHeader(): JTableHeader {
    return super.createDefaultTableHeader().apply {
      val originalHeaderCellRenderer = defaultRenderer
      defaultRenderer = object: TableCellRenderer {
        override fun getTableCellRendererComponent(table: JTable, value: Any?, isSelected: Boolean,
                                                   hasFocus: Boolean, row: Int, column: Int): Component {
          val component = originalHeaderCellRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
          if (component !is JLabel) {
            return component
          }
          component.icon = if (column == model.mySortKeyColumn) {
            when(mySortOrder) {
              SortOrder.ASCENDING -> DefaultLookup.getIcon(component, ui, "Table.ascendingSortIcon")
              SortOrder.DESCENDING -> DefaultLookup.getIcon(component, ui, "Table.descendingSortIcon")
              else -> DefaultLookup.getIcon(component, ui, "Table.naturalSortIcon")
            }
          } else {
            null
          }
          return component
        }
      }
      addMouseListener(object: MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          if (e.clickCount != 1) {
            return
          }
          val clickedColumnIndex = columnAtPoint(e.point)
          if (clickedColumnIndex < 0) {
            return
          }
          if (model.mySortKeyColumn == clickedColumnIndex) {
            mySortOrder = when(mySortOrder) {
              SortOrder.ASCENDING -> SortOrder.DESCENDING
              SortOrder.DESCENDING -> SortOrder.UNSORTED
              else -> SortOrder.ASCENDING
            }
          } else {
            model.mySortKeyColumn = clickedColumnIndex
            mySortOrder = SortOrder.ASCENDING
          }
          refreshTable()
        }
        override fun mouseReleased(e: MouseEvent) {
          if (androidTestResultsUserPreferencesManager != null) {
            for ((index, column) in columnModel.columns.iterator().withIndex()) {
              androidTestResultsUserPreferencesManager.setUserPreferredColumnWidth(model.columns[index].name, column.width)
            }
          }
        }
      })
    }
  }

  override fun processKeyEvent(e: KeyEvent) {
    // Moves the keyboard focus to the next component instead of the next row in the table.
    if (e.keyCode == KeyEvent.VK_TAB) {
      if (e.id == KeyEvent.KEY_PRESSED) {
        val keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        if (e.isShiftDown) {
          keyboardFocusManager.focusPreviousComponent(this)
        } else {
          keyboardFocusManager.focusNextComponent(this)
        }
      }
      e.consume()
      return
    }

    super.processKeyEvent(e)
  }

  fun refreshTable() {
    val prevSelectedObject = selectedObject
    val prevExpandedPaths = TreeUtil.collectExpandedPaths(tree)
    model.applyFilter()

    // TreeTableView doesn't support TableRowSorter so we sort items
    // directly in the model (IDEA-248054).
    val rowComparator = if (model.mySortKeyColumn >= 0 && model.mySortKeyColumn < getColumnModel().columnCount) {
      when(mySortOrder) {
        SortOrder.ASCENDING -> getColumnInfo(model.mySortKeyColumn).comparator
        SortOrder.DESCENDING -> getColumnInfo(model.mySortKeyColumn).comparator?.reversed()
        else -> null
      }
    } else {
      null
    } as? Comparator<AndroidTestResults> ?: model.insertionOrderComparator
    model.sort(rowComparator)

    model.reload()
    tableChanged(null)
    for ((index, column) in getColumnModel().columns.iterator().withIndex()) {
      column.resizable = true
      column.maxWidth = Int.MAX_VALUE / 2
      column.preferredWidth = androidTestResultsUserPreferencesManager?.getUserPreferredColumnWidth(model.columns[index].name, model.columns[index].getWidth(this))
                              ?: model.columns[index].getWidth(this)
      column.minWidth = column.preferredWidth
    }

    TreeUtil.restoreExpandedPaths(tree, prevExpandedPaths)
    prevSelectedObject?.let { addSelection(it) }
  }
}

/**
 * A view model class of [AndroidTestResultsTableViewComponent].
 */
private class AndroidTestResultsTableModel : ListTreeTableModelOnColumns(AggregationRow(), arrayOf()) {
  /**
   * A map of test results rows. The key is [AndroidTestCase.id] and the value is [AndroidTestResultsRow].
   * Note that [AndroidTestResultsRow] has test results for every device.
   */
  val myTestResultsRows = mutableMapOf<String, AndroidTestResultsRow>()
  val myTestClassAggregationRow = mutableMapOf<String, AggregationRow>()
  val myRootAggregationRow: AggregationRow = root as AggregationRow
  var mySortKeyColumn = -1

  private val myRowInsertionOrder: MutableMap<AndroidTestResults, Int> = mutableMapOf(myRootAggregationRow to 0)
  val insertionOrderComparator: Comparator<AndroidTestResults> = compareBy {
    myRowInsertionOrder.getOrDefault(it, Int.MAX_VALUE)
  }

  private val myDeviceColumns: MutableList<AndroidTestResultsColumn> = mutableListOf()
  private lateinit var myFilteredColumns: Array<ColumnInfo<Any, Any>>

  /**
   * A filter to show and hide columns.
   */
  private var myColumnFilter: ((AndroidDevice) -> Boolean)? = null

  /**
   * A filter to show and hide rows.
   */
  private var myRowFilter: ((Any) -> Boolean)? = null

  /**
   * Creates and adds a new column for a given device.
   */
  fun addDeviceColumn(device: AndroidDevice) {
    myDeviceColumns.add(AndroidTestResultsColumn(device))
    updateFilteredColumns()
  }

  /**
   * Creates and adds a new row for a pair of given [device] and [testCase]. If the row for the [testCase.id] has existed already,
   * it adds the [testCase] to that row.
   */
  fun addTestResultsRow(device: AndroidDevice, testCase: AndroidTestCase): AndroidTestResultsRow {
    val row = myTestResultsRows.getOrPut(testCase.id) {
      AndroidTestResultsRow(testCase.methodName, testCase.className, testCase.packageName).also { resultsRow ->
        val testClassAggRow = myTestClassAggregationRow.getOrPut(resultsRow.getFullTestClassName()) {
          AggregationRow(resultsRow.packageName, resultsRow.className).also {
            myRootAggregationRow.add(it)
            myRowInsertionOrder.putIfAbsent(it, myRowInsertionOrder.size)
          }
        }
        testClassAggRow.add(resultsRow)
        myRowInsertionOrder.putIfAbsent(resultsRow, myRowInsertionOrder.size)
      }
    }
    row.addTestCase(device, testCase)
    return row
  }

  /**
   * Returns [AndroidTestResultsRow] for a given test case if exists, otherwise null.
   */
  fun getTestResultsRow(testCase: AndroidTestCase): AndroidTestResultsRow? {
    return myTestResultsRows[testCase.id]
  }

  /**
   * Sets a visible condition.
   *
   * @param columnFilter a predicate which returns true for an column to be displayed
   */
  fun setColumnFilter(columnFilter: (AndroidDevice) -> Boolean) {
    myColumnFilter = columnFilter
    updateFilteredColumns()
  }

  override fun setColumns(columns: Array<out ColumnInfo<Any, Any>>): Boolean {
    val valueChanged = super.setColumns(columns)
    if (valueChanged) {
      updateFilteredColumns()
    }
    return valueChanged
  }

  override fun getColumns(): Array<ColumnInfo<Any, Any>> {
    if (!::myFilteredColumns.isInitialized) {
      updateFilteredColumns()
    }
    return myFilteredColumns
  }

  private fun updateFilteredColumns() {
    // Store current sortKeyColumn in case it gets filtered out
    val sortColumn = if (mySortKeyColumn != -1) { columns[mySortKeyColumn].name } else { null }

    // We always display test name and test duration columns.
    val filteredColumns = mutableListOf(
      TestNameColumn,
      TestDurationColumn as ColumnInfo<Any, Any>)

    // Show test status and device columns.
    // We should hide test status column if there is only one device
    // because the information displayed in test status and device column
    // would be the same and redundant.
    val filteredDeviceColumns = myDeviceColumns.filter {
      myColumnFilter?.invoke(it.device) ?: true
    }
    if (filteredDeviceColumns.size > 1) {
      val filteredDevices = filteredDeviceColumns.map {
        it.device
      }.toList()
      filteredColumns.add(TestStatusColumn(filteredDevices) as ColumnInfo<Any, Any>)
    }
    filteredColumns.addAll(filteredDeviceColumns as List<ColumnInfo<Any, Any>>)

    myFilteredColumns = filteredColumns.toTypedArray()

    // Update sortKeyColumn index after columns are filtered
    updateSortKeyColumnAfterFilter(sortColumn)
  }

  private fun updateSortKeyColumnAfterFilter(sortKeyColumn: String?) {
    // Do not need to update if no column is selected for sorting
    if (sortKeyColumn == null) {
      return
    }

    var sortKeyColumnRemainsAfterFilter = false
    for ((index, column) in columns.iterator().withIndex()) {
      if (column.name == sortKeyColumn) {
        sortKeyColumnRemainsAfterFilter = true
        mySortKeyColumn = index
        break
      }
    }
    if (!sortKeyColumnRemainsAfterFilter) {
      mySortKeyColumn = -1
    }
  }

  override fun getColumnClass(column: Int): Class<*> {
    return columns[column].columnClass
  }

  override fun getColumnName(column: Int): String {
    return columns[column].name
  }

  override fun getColumnCount(): Int {
    return columns.size
  }

  override fun getValueAt(value: Any?, column: Int): Any? {
    return columns[column].valueOf(value)
  }

  override fun isCellEditable(node: Any?, column: Int): Boolean {
    return columns[column].isCellEditable(node)
  }

  override fun getColumnInfos(): Array<ColumnInfo<Any, Any>> {
    return columns
  }

  /**
   * Sets a filter to hide specific rows from this table.
   *
   * @param filter a predicate which returns false for an item to be hidden
   */
  fun setRowFilter(filter: (AndroidTestResults) -> Boolean) {
    myRowFilter = {
      when(it) {
        is AndroidTestResultsRow -> filter(it)
        is AggregationRow -> it.childCount > 0
        else -> true
      }
    }
    applyFilter()
  }

  /**
   * Applies a filter to update rows and columns.
   */
  fun applyFilter() {
    updateFilteredColumns()
    myRowFilter?.let {
      myRootAggregationRow.applyFilter(it)
    }
  }

  fun sort(comparator: Comparator<AndroidTestResults>) {
    fun doSort(node: AggregationRow) {
      node.sort(comparator)
      node.children().asSequence().forEach {
        if (it is AggregationRow) {
          doSort(it)
        }
      }
    }
    doSort(myRootAggregationRow)
  }
}

/**
 * A column for displaying a test name.
 */
private object TestNameColumn : TreeColumnInfo("Tests") {
  private val myComparator = compareBy<AndroidTestResults> {
    it.methodName
  }.thenBy {
    it.className
  }.thenBy {
    it.getFullTestCaseName()
  }
  override fun getComparator(): Comparator<AndroidTestResults> = myComparator
  override fun getWidth(table: JTable?): Int = 360
}

/**
 * A column for displaying a test duration.
 */
private object TestDurationColumn : ColumnInfo<AndroidTestResults, AndroidTestResults>("Duration") {
  private val myComparator = compareBy<AndroidTestResults> {
    it.getTotalDuration()
  }
  override fun valueOf(item: AndroidTestResults): AndroidTestResults = item
  override fun getComparator(): Comparator<AndroidTestResults> = myComparator
  override fun getWidth(table: JTable?): Int = 90
  override fun getRenderer(item: AndroidTestResults?): TableCellRenderer = TestDurationColumnCellRenderer
  override fun getCustomizedRenderer(o: AndroidTestResults?, renderer: TableCellRenderer?): TableCellRenderer {
    return TestDurationColumnCellRenderer
  }
}

private object TestDurationColumnCellRenderer : DefaultTableCellRenderer() {
  override fun getTableCellRendererComponent(table: JTable,
                                             value: Any?,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    val results = value as? AndroidTestResults ?: return this
    val durationText = StringUtil.formatDuration(results.getRoundedTotalDuration().toMillis(), "\u2009")
    super.getTableCellRendererComponent(table, durationText, isSelected, hasFocus, row, column)
    icon = null
    horizontalTextPosition = CENTER
    horizontalAlignment = RIGHT
    foreground = if(isSelected && table.hasFocus()) {
      UIUtil.getTreeSelectionForeground(true)
    } else {
      SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor
    }
    font = RelativeFont.SMALL.derive(font)
    background = UIUtil.getTableBackground(isSelected, table.hasFocus())
    return this
  }
}

/**
 * A column for displaying an aggregated test result grouped by a test case ID.
 */
private class TestStatusColumn(val devices: List<AndroidDevice>) : ColumnInfo<AndroidTestResults, AndroidTestResults>("Status") {
  private val myComparator = Comparator<AndroidTestResults> { lhs, rhs ->
    compareValues(lhs.getTestResultSummary(devices), rhs.getTestResultSummary(devices))
  }
  private val renderer: TableCellRenderer = TestStatusColumnCellRenderer(devices)
  override fun valueOf(item: AndroidTestResults): AndroidTestResults = item
  override fun getComparator(): Comparator<AndroidTestResults> = myComparator
  override fun getWidth(table: JTable): Int = 80
  override fun getRenderer(item: AndroidTestResults?): TableCellRenderer = renderer
  override fun getCustomizedRenderer(o: AndroidTestResults?, renderer: TableCellRenderer?): TableCellRenderer {
    return this.renderer
  }
}

private class TestStatusColumnCellRenderer(val devices: List<AndroidDevice>) : DefaultTableCellRenderer() {
  override fun getTableCellRendererComponent(table: JTable,
                                             value: Any?,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    val results = value as? AndroidTestResults ?: return this
    super.getTableCellRendererComponent(table, results.getTestResultSummaryText(devices), isSelected, hasFocus, row, column)
    icon = null
    horizontalTextPosition = CENTER
    horizontalAlignment = CENTER
    foreground = if(isSelected && table.hasFocus()) {
      UIUtil.getTreeSelectionForeground(true)
    } else {
      getColorFor(results.getTestResultSummary(devices))
    }
    background = UIUtil.getTableBackground(isSelected, table.hasFocus())
    return this
  }
}

/**
 * A column for displaying an individual test case result on a given [device].
 *
 * @param device shows an individual test case result in this column for a given [device]
 */
private class AndroidTestResultsColumn(val device: AndroidDevice) :
  ColumnInfo<AndroidTestResults, AndroidTestResultStats>(device.getName()) {
  private val myComparator = Comparator<AndroidTestResults> { lhs, rhs ->
    compareValues(lhs.getTestCaseResult(device), rhs.getTestCaseResult(device))
  }
  override fun getName(): String = device.getName()
  override fun valueOf(item: AndroidTestResults): AndroidTestResultStats {
    return item.getResultStats(device)
  }
  override fun getComparator(): Comparator<AndroidTestResults> = myComparator
  override fun getWidth(table: JTable): Int = 120
  override fun getRenderer(item: AndroidTestResults?): TableCellRenderer {
    return if (item is AggregationRow) {
      AndroidTestAggregatedResultsColumnCellRenderer
    } else {
      AndroidTestResultsColumnCellRenderer
    }
  }
  override fun getCustomizedRenderer(o: AndroidTestResults?, renderer: TableCellRenderer?): TableCellRenderer {
    return if (o is AggregationRow) {
      AndroidTestAggregatedResultsColumnCellRenderer
    } else {
      AndroidTestResultsColumnCellRenderer
    }
  }
}

private object AndroidTestResultsColumnCellRenderer : DefaultTableCellRenderer() {
  override fun getTableCellRendererComponent(table: JTable,
                                             value: Any?,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column)
    val stats = value as? AndroidTestResultStats ?: return this
    horizontalAlignment = CENTER
    horizontalTextPosition = CENTER
    icon = getIconFor(stats.getSummaryResult())
    background = UIUtil.getTableBackground(isSelected, table.hasFocus())
    return this
  }
}

private object AndroidTestAggregatedResultsColumnCellRenderer : DefaultTableCellRenderer() {
  override fun getTableCellRendererComponent(table: JTable,
                                             value: Any?,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column)
    val stats = value as? AndroidTestResultStats ?: return this
    horizontalAlignment = CENTER
    horizontalTextPosition = CENTER
    icon = null
    foreground = if(isSelected && table.hasFocus()) {
      UIUtil.getTreeSelectionForeground(true)
    } else {
      getColorFor(stats.getSummaryResult())
    }
    background = UIUtil.getTableBackground(isSelected, table.hasFocus())
    setValue("${stats.passed}/${stats.total - stats.skipped}")
    return this
  }
}

/**
 * A row for displaying test results. Each row has test results for every device.
 */
private class AndroidTestResultsRow(override val methodName: String,
                                    override val className: String,
                                    override val packageName: String) : AndroidTestResults, DefaultMutableTreeNode() {
  private val myTestCases = mutableMapOf<String, AndroidTestCase>()

  /**
   * Adds test case to this row.
   *
   * @param device a device which the given [testCase] belongs to
   * @param testCase a test case to be added to this row
   */
  fun addTestCase(device: AndroidDevice, testCase: AndroidTestCase) {
    myTestCases[device.id] = testCase
  }

  /**
   * Returns a test case result for a given [device].
   */
  override fun getTestCaseResult(device: AndroidDevice): AndroidTestCaseResult? = myTestCases[device.id]?.result

  /**
   * Returns a logcat message for a given [device].
   */
  override fun getLogcat(device: AndroidDevice): String = myTestCases[device.id]?.logcat ?: ""

  /**
   * Returns the start time of the test on a given device.
   */
  override fun getStartTime(device: AndroidDevice): Long? = myTestCases[device.id]?.startTimestampMillis

  override fun getDuration(device: AndroidDevice): Duration? {
    val start = myTestCases[device.id]?.startTimestampMillis ?: return null
    val end = myTestCases[device.id]?.endTimestampMillis ?: System.currentTimeMillis()
    return Duration.ofMillis(max(end - start, 0))
  }

  override fun getTotalDuration(): Duration {
    return Duration.ofMillis(myTestCases.values.asSequence().map {
      val start = it.startTimestampMillis ?: return@map 0L
      val end = it.endTimestampMillis ?: System.currentTimeMillis()
      max(end - start, 0L)
    }.sum())
  }

  /**
   * Returns an error stack for a given [device].
   */
  override fun getErrorStackTrace(device: AndroidDevice): String = myTestCases[device.id]?.errorStackTrace ?: ""

  /**
   * Returns a benchmark result for a given [device].
   */
  override fun getBenchmark(device: AndroidDevice): BenchmarkOutput = BenchmarkOutput(myTestCases[device.id]?.benchmark ?: "")

  /**
   * Returns the retention info artifact from Android Test Retention if available.
   */
  override fun getRetentionInfo(device: AndroidDevice): File? = myTestCases[device.id]?.retentionInfo

  /**
   * Returns the snapshot artifact from Android Test Retention if available.
   */
  override fun getRetentionSnapshot(device: AndroidDevice): File? = myTestCases[device.id]?.retentionSnapshot

  /**
   * Returns an aggregated test result.
   */
  override fun getTestResultSummary(): AndroidTestCaseResult = getResultStats().getSummaryResult()

  /**
   * Returns an aggregated test result for the given devices.
   */
  override fun getTestResultSummary(devices: List<AndroidDevice>): AndroidTestCaseResult = getResultStats(devices).getSummaryResult()

  /**
   * Returns a one liner test result summary string for the given devices.
   */
  override fun getTestResultSummaryText(devices: List<AndroidDevice>): String {
    val stats = getResultStats(devices)
    return when {
      stats.failed == 1 -> "Fail"
      stats.failed > 0 -> "Fail (${stats.failed})"
      stats.cancelled > 0 -> "Cancelled"
      stats.running > 0 -> "Running"
      stats.passed > 0 -> "Pass"
      stats.skipped > 0 -> "Skip"
      else -> ""
    }
  }

  override fun getResultStats(): AndroidTestResultStats {
    return myTestCases.values.fold(AndroidTestResultStats()) { acc, androidTestCase ->
      acc.addTestCaseResult(androidTestCase.result)
    }
  }

  override fun getResultStats(device: AndroidDevice): AndroidTestResultStats {
    val stats = AndroidTestResultStats()
    return stats.addTestCaseResult(getTestCaseResult(device))
  }

  override fun getResultStats(devices: List<AndroidDevice>): AndroidTestResultStats {
    return devices.fold(AndroidTestResultStats()) { acc, device ->
      acc.addTestCaseResult(getTestCaseResult(device))
    }
  }
}

private open class FilterableTreeNode : DefaultMutableTreeNode() {
  private var invisibleNodes: List<TreeNode> = listOf()
  val allChildren: Sequence<TreeNode>
    get() = sequence {
      // In JDK 8 DefaultMutableTreeNode.children() returns a raw Vector but as of JDK 11 the generic type matches
      // and this assignment is no longer unchecked.
      @Suppress("UNCHECKED_CAST") // In JDK 11 the cast is no longer needed.
      children?.let { yieldAll(it as Vector<TreeNode>) }
      yieldAll(invisibleNodes)
    }

  /**
   * Applies a filter to show or hide rows.
   *
   * @param filter a predicate which returns false for an item to be hidden
   */
  fun applyFilter(filter: (Any) -> Boolean) {
    if (children == null) {
      return
    }
    children.addAll(invisibleNodes)
    children.forEach {
      if (it is FilterableTreeNode) {
        it.applyFilter(filter)
      }
    }
    // In JDK 8 DefaultMutableTreeNode.children() returns a raw Vector but as of JDK 11 the generic type matches
    // and this assignment is no longer unchecked.
    @Suppress("UNCHECKED_CAST") // In JDK 11 the cast is no longer needed.
    invisibleNodes = children.filterNot(filter) as List<TreeNode>
    children.retainAll(filter)
  }
}

/**
 * A row for displaying aggregated test results. Each row has test results for a device.
 */
private class AggregationRow(override val packageName: String = "",
                             override val className: String = "") : AndroidTestResults, FilterableTreeNode() {

  private val myTestSuiteResult: MutableMap<String, AndroidTestSuiteResult> = mutableMapOf()

  /**
   * Sets the test suite result of the given device.
   */
  fun setTestSuiteResultForDevice(device: AndroidDevice, result: AndroidTestSuiteResult?) {
    if (result != null) {
      myTestSuiteResult[device.id] = result
    } else {
      myTestSuiteResult.remove(device.id)
    }
  }

  override val methodName: String = ""

  override fun getTestCaseResult(device: AndroidDevice): AndroidTestCaseResult {
    val result = myTestSuiteResult[device.id]
    return if (result != null) {
      when (result) {
        AndroidTestSuiteResult.PASSED -> AndroidTestCaseResult.PASSED
        AndroidTestSuiteResult.FAILED -> AndroidTestCaseResult.FAILED
        AndroidTestSuiteResult.ABORTED,
        AndroidTestSuiteResult.CANCELLED -> AndroidTestCaseResult.CANCELLED
      }
    } else {
      getResultStats(device).getSummaryResult()
    }
  }

  override fun getTestResultSummary(): AndroidTestCaseResult {
    return when {
      myTestSuiteResult.values.any { it == AndroidTestSuiteResult.FAILED } -> {
        AndroidTestCaseResult.FAILED
      }
      myTestSuiteResult.values.any { it == AndroidTestSuiteResult.CANCELLED ||
                                     it == AndroidTestSuiteResult.ABORTED } -> {
        AndroidTestCaseResult.CANCELLED
      }
      else -> {
        getResultStats().getSummaryResult()
      }
    }
  }

  override fun getTestResultSummary(devices: List<AndroidDevice>): AndroidTestCaseResult {
    return when {
      myTestSuiteResult.values.any { it == AndroidTestSuiteResult.FAILED } -> {
        AndroidTestCaseResult.FAILED
      }
      myTestSuiteResult.values.any { it == AndroidTestSuiteResult.CANCELLED ||
                                     it == AndroidTestSuiteResult.ABORTED } -> {
        AndroidTestCaseResult.CANCELLED
      }
      else -> {
        getResultStats(devices).getSummaryResult()
      }
    }
  }

  override fun getTestResultSummaryText(devices: List<AndroidDevice>): String {
    val stats = getResultStats(devices)
    return "${stats.passed}/${stats.total - stats.skipped}"
  }

  override fun getResultStats(): AndroidTestResultStats {
    return allChildren.fold(AndroidTestResultStats()) { acc, result ->
      (result as? AndroidTestResults)?.getResultStats()?.plus(acc) ?: acc
    }
  }

  override fun getResultStats(device: AndroidDevice): AndroidTestResultStats {
    return allChildren.fold(AndroidTestResultStats()) { acc, result ->
      (result as? AndroidTestResults)?.getResultStats(device)?.plus(acc) ?: acc
    }
  }

  override fun getResultStats(devices: List<AndroidDevice>): AndroidTestResultStats {
    return allChildren.fold(AndroidTestResultStats()) { acc, result ->
      (result as? AndroidTestResults)?.getResultStats(devices)?.plus(acc) ?: acc
    }
  }

  override fun getLogcat(device: AndroidDevice): String {
    return allChildren.fold("") { acc, result ->
      val logcat = (result as? AndroidTestResults)?.getLogcat(device)
      if (logcat.isNullOrBlank()) {
        acc
      } else {
        if (acc.isBlank()) {
          logcat
        } else {
          "${acc}\n${logcat}"
        }
      }
    }
  }

  override fun getStartTime(device: AndroidDevice): Long? {
    return allChildren.fold(null as Long?) { acc, result ->
      (result as? AndroidTestResults)?.getStartTime(device).also {
        if (acc == null) {
          return@fold it
        }
        if (it != null && it != 0L) {
          return@fold minOf(it, acc)
        }
        return@fold acc
      }
    }
  }

  override fun getDuration(device: AndroidDevice): Duration? {
    return  allChildren.fold(null as Duration?) { acc, result ->
      val childDuration = (result as? AndroidTestResults)?.getDuration(device) ?: return@fold acc
      if (acc == null) {
        childDuration
      } else {
        acc + childDuration
      }
    }
  }

  override fun getTotalDuration(): Duration {
    return Duration.ofMillis(allChildren.map {
      (it as? AndroidTestResults)?.getTotalDuration()?.toMillis() ?: 0
    }.sum())
  }

  override fun getErrorStackTrace(device: AndroidDevice): String = ""

  override fun getBenchmark(device: AndroidDevice): BenchmarkOutput {
    return allChildren.fold(BenchmarkOutput.Empty) { acc, result ->
      val benchmark = (result as? AndroidTestResults)?.getBenchmark(device) ?: BenchmarkOutput.Empty
      if (benchmark == BenchmarkOutput.Empty) {
        acc
      } else if (acc == BenchmarkOutput.Empty) {
        benchmark
      } else {
        acc.fold(benchmark)
      }
    }
  }

  override fun getRetentionInfo(device: AndroidDevice): File? = null

  override fun getRetentionSnapshot(device: AndroidDevice): File? = null

  /**
   * Sorts children of this tree node by a given [comparator].
   */
  fun sort(comparator: Comparator<AndroidTestResults>) {
    (children as? Vector<AndroidTestResults>)?.sortWith(comparator)
  }
}

private fun AggregationRow.toAndroidTestResultsTreeNode(): AndroidTestResultsTreeNode {
  return AndroidTestResultsTreeNode(this, sequence {
    yieldAll(allChildren.mapNotNull {
      when(it) {
        is AndroidTestResultsRow -> AndroidTestResultsTreeNode(it, emptySequence())
        is AggregationRow -> it.toAndroidTestResultsTreeNode()
        else -> null
      }
    })
  })
}