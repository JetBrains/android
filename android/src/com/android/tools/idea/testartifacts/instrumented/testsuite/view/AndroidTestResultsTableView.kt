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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.projectsystem.TestArtifactSearchScopes
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ActionPlaces
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultStats
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.getFullTestCaseName
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.getFullTestClassName
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.getSummaryResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.plus
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.getName
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.testframework.sm.runner.ui.SMPoolOfTestIcons
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.actions.EditSourceAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.progress.util.ColorProgressBar
import com.intellij.psi.JavaPsiFacade
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dualView.TreeTableView
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Color
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.util.Comparator
import javax.swing.Icon
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.ListSelectionModel
import javax.swing.RowFilter
import javax.swing.event.ListSelectionEvent
import javax.swing.event.TableModelEvent
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeNode

/**
 * A table to display Android test results. Test results are grouped by device and test case. The column is a device name
 * and the row is a test case.
 */
class AndroidTestResultsTableView(listener: AndroidTestResultsTableListener,
                                  javaPsiFacade: JavaPsiFacade,
                                  testArtifactSearchScopes: TestArtifactSearchScopes?) {
  private val myModel = AndroidTestResultsTableModel()
  private val myTableView = AndroidTestResultsTableViewComponent(myModel, listener, javaPsiFacade, testArtifactSearchScopes)
  private val myTableViewContainer = JBScrollPane(myTableView)

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

  /**
   * Adds a test case to the table. If you change value of any properties of [testCase] later,
   * you need to call [refreshTable] to reflect changes to the table.
   *
   * @param device a device which the given [testCase] belongs to
   * @param testCase a test case to be displayed in the table
   */
  @UiThread
  fun addTestCase(device: AndroidDevice, testCase: AndroidTestCase) {
    val testRow = myModel.addTestResultsRow(device, testCase)
    refreshTable()
    myTableView.tree.expandPath(TreeUtil.getPath(myModel.myRootAggregationRow, testRow.parent))
  }

  /**
   * Sets a filter to hide specific rows from this table.
   *
   * @param filter a predicate which returns false for an item to be hidden
   */
  @UiThread
  fun setRowFilter(filter: (AndroidTestResults) -> Boolean) {
    val sorter = myTableView.rowSorter as? TableRowSorter ?: return
    sorter.rowFilter = object: RowFilter<TableModel, Int>() {
      override fun include(entry: Entry<out TableModel, out Int>): Boolean {
        if (entry.valueCount == 0) {
          return false
        }
        val results = entry.getValue(0) as? AndroidTestResults ?: return false
        return filter(results)
      }
    }
  }

  /**
   * Sets a filter to hide specific columns from this table.
   *
   * @param filter a predicate which returns false for an column to be hidden
   */
  @UiThread
  fun setColumnFilter(filter: (AndroidDevice) -> Boolean) {
    myModel.setVisibleCondition(filter)
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
   * Clears currently selected items in the table.
   */
  @UiThread
  fun clearSelection() {
    myTableView.clearSelection()
  }

  /**
   * Returns a root component of the table view.
   */
  @UiThread
  fun getComponent(): Component {
    return myTableViewContainer
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
                                                   private val testArtifactSearchScopes: TestArtifactSearchScopes?)
  : TreeTableView(model), DataProvider {
  init {
    putClientProperty(AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true)
    selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
    autoResizeMode = AUTO_RESIZE_OFF
    tableHeader.resizingAllowed = false
    tableHeader.reorderingAllowed = false
    showHorizontalLines = false
    rowSorter = DefaultColumnInfoBasedRowSorter(getModel())
    tree.isRootVisible = true
    tree.showsRootHandles = true
    tree.cellRenderer = object: ColoredTreeCellRenderer() {
      override fun customizeCellRenderer(tree: JTree,
                                         value: Any?,
                                         selected: Boolean,
                                         expanded: Boolean,
                                         leaf: Boolean,
                                         row: Int,
                                         hasFocus: Boolean) {
        val results = value as? AndroidTestResults ?: return
        append(when {
          results.methodName.isNotBlank() -> {
            results.methodName
          }
          results.className.isNotBlank() -> {
            results.className
          }
          else -> {
            "Test Results"
          }
        })
        icon = getIconFor(results.getTestResultSummary())
      }
    }

    TreeUtil.installActions(tree)
    PopupHandler.installPopupHandler(this, IdeActions.GROUP_TESTTREE_POPUP, ActionPlaces.ANDROID_TEST_SUITE_TABLE)
    addMouseListener(object: MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        when (e?.clickCount) {
          1 -> {
            selectedObject?.let {
              listener.onAndroidTestResultsRowSelected(
                it,
                (model.columnInfos.getOrNull(selectedColumn) as? AndroidTestResultsColumn)?.device)
            }
          }
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
    selectedObject?.let {
      listener.onAndroidTestResultsRowSelected(
        it,
        (model.columnInfos.getOrNull(selectedColumn) as? AndroidTestResultsColumn)?.device)
    }
  }

  override fun tableChanged(e: TableModelEvent?) {
    // JDK-4276786: JTable doesn't preserve the selection so we manually restore the previous selection.
    val prevSelectedObject = selectedObject
    super.tableChanged(e)
    prevSelectedObject?.let { addSelection(it) }
  }

  override fun getData(dataId: String): Any? {
    if (CommonDataKeys.PSI_ELEMENT.`is`(dataId)) {
      val selectedTestResults = selectedObject ?: return null
      val androidTestSourceScope = testArtifactSearchScopes?.androidTestSourceScope ?: return null
      return selectedTestResults.getFullTestClassName().let {
        javaPsiFacade.findClasses(it, androidTestSourceScope)
      }.mapNotNull {
        it.findMethodsByName(selectedTestResults.methodName).firstOrNull()
      }.firstOrNull()
    }
    return null
  }

  override fun getCellRenderer(row: Int, column: Int): TableCellRenderer? {
    return getColumnInfo(column).getRenderer(getRowElement(row)) ?: super.getCellRenderer(row, column)
  }

  fun refreshTable() {
    val prevSelectedObject = selectedObject
    val prevExpandedPaths = TreeUtil.collectExpandedPaths(tree)
    tableChanged(null)
    for ((index, column) in getColumnModel().columns.iterator().withIndex()) {
      val width = model.columns[index].getWidth(this)
      column.width = width
      column.minWidth = width
      column.maxWidth = width
      column.preferredWidth = width
    }
    model.reload()
    TreeUtil.restoreExpandedPaths(tree, prevExpandedPaths)
    prevSelectedObject?.let { addSelection(it) }
  }
}

/**
 * A view model class of [AndroidTestResultsTableViewComponent].
 */
private class AndroidTestResultsTableModel :
  ListTreeTableModelOnColumns(AggregationRow(), arrayOf(TestNameColumn, TestStatusColumn)) {

  /**
   * A map of test results rows. The key is [AndroidTestCase.id] and the value is [AndroidTestResultsRow].
   * Note that [AndroidTestResultsRow] has test results for every devices.
   */
  val myTestResultsRows = mutableMapOf<String, AndroidTestResultsRow>()
  val myTestClassAggregationRow = mutableMapOf<String, AggregationRow>()
  val myRootAggregationRow: AggregationRow = root as AggregationRow

  /**
   * A current visible condition.
   */
  private var myVisibleCondition: ((AndroidDevice) -> Boolean)? = null

  /**
   * Creates and adds a new column for a given device.
   */
  fun addDeviceColumn(device: AndroidDevice) {
    columns += AndroidTestResultsColumn(device).apply {
      myVisibleCondition = this@AndroidTestResultsTableModel.myVisibleCondition
    }
  }

  /**
   * Creates and adds a new row for a pair of given [device] and [testCase]. If the row for the [testCase.id] has existed already,
   * it adds the [testCase] to that row.
   */
  fun addTestResultsRow(device: AndroidDevice, testCase: AndroidTestCase): TreeNode {
    val row = myTestResultsRows.getOrPut(testCase.id) {
      AndroidTestResultsRow(testCase.methodName, testCase.className, testCase.packageName).also { resultsRow ->
        val testClassAggRow = myTestClassAggregationRow.getOrPut(resultsRow.getFullTestClassName()) {
          AggregationRow(resultsRow.packageName, resultsRow.className).also { myRootAggregationRow.add(it) }
        }
        testClassAggRow.add(resultsRow)
      }
    }
    row.addTestCase(device, testCase)
    return row
  }

  /**
   * Sets a visible condition.
   *
   * @param visibleCondition a predicate which returns true for an column to be displayed
   */
  fun setVisibleCondition(visibleCondition: (AndroidDevice) -> Boolean) {
    myVisibleCondition = visibleCondition
    columnInfos.forEach {
      if (it is AndroidTestResultsColumn) {
        it.myVisibleCondition = myVisibleCondition
      }
    }
  }
}

/**
 * A column for displaying a test name.
 */
private object TestNameColumn : TreeColumnInfo("Tests") {
  private val myComparator = Comparator<AndroidTestResults> { lhs, rhs ->
    compareValues(lhs.getFullTestCaseName(), rhs.getFullTestCaseName())
  }
  override fun getComparator(): Comparator<AndroidTestResults> = myComparator
  override fun getWidth(table: JTable?): Int = 400
}

/**
 * A column for displaying an aggregated test result grouped by a test case ID.
 */
private object TestStatusColumn : ColumnInfo<AndroidTestResults, AndroidTestResults>("Status") {
  private val myComparator = Comparator<AndroidTestResults> { lhs, rhs ->
    compareValues(lhs.getTestResultSummary(), rhs.getTestResultSummary())
  }
  override fun valueOf(item: AndroidTestResults): AndroidTestResults = item
  override fun getComparator(): Comparator<AndroidTestResults> = myComparator
  override fun getWidth(table: JTable): Int = 80
  override fun getRenderer(item: AndroidTestResults?): TableCellRenderer = TestStatusColumnCellRenderer
  override fun getCustomizedRenderer(o: AndroidTestResults?, renderer: TableCellRenderer?): TableCellRenderer {
    return TestStatusColumnCellRenderer
  }
}

private object TestStatusColumnCellRenderer : DefaultTableCellRenderer() {
  private val myEmptyBorder = JBUI.Borders.empty()
  override fun getTableCellRendererComponent(table: JTable?,
                                             value: Any?,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    val results = value as? AndroidTestResults ?: return this
    super.getTableCellRendererComponent(table, results.getTestResultSummaryText(), isSelected, hasFocus, row, column)
    horizontalAlignment = CENTER
    horizontalTextPosition = CENTER
    foreground = getColorFor(results.getTestResultSummary())
    border = myEmptyBorder
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
  var myVisibleCondition: ((AndroidDevice) -> Boolean)? = null
  override fun getName(): String = device.getName()
  override fun valueOf(item: AndroidTestResults): AndroidTestResultStats {
    return item.getResultStats(device)
  }
  override fun getComparator(): Comparator<AndroidTestResults> = myComparator
  override fun getWidth(table: JTable): Int {
    val isVisible = myVisibleCondition?.invoke(device) ?: true
    // JTable does not support hiding columns natively. We simply set the column
    // width to 1 px to hide. Note that you cannot set zero here because it will be
    // ignored. See TableView.updateColumnSizes for details.
    return if (isVisible) { 120 } else { 1 }
  }
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
  private val myEmptyBorder = JBUI.Borders.empty()
  override fun getTableCellRendererComponent(table: JTable?,
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
    border = myEmptyBorder
    return this
  }
}

private object AndroidTestAggregatedResultsColumnCellRenderer : DefaultTableCellRenderer() {
  private val myEmptyBorder = JBUI.Borders.empty()
  override fun getTableCellRendererComponent(table: JTable?,
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
    foreground = getColorFor(stats.getSummaryResult())
    setValue("${stats.passed + stats.skipped}/${stats.total}")
    border = myEmptyBorder
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
   * Returns an error stack for a given [device].
   */
  override fun getErrorStackTrace(device: AndroidDevice): String = myTestCases[device.id]?.errorStackTrace ?: ""

  /**
   * Returns a benchmark result for a given [device].
   */
  override fun getBenchmark(device: AndroidDevice): String = myTestCases[device.id]?.benchmark ?: ""

  /**
   * Returns the snapshot artifact from Android Test Retention if available.
   */
  override fun getRetentionSnapshot(device: AndroidDevice): File? = myTestCases[device.id]?.retentionSnapshot

  /**
   * Returns an aggregated test result.
   */
  override fun getTestResultSummary(): AndroidTestCaseResult = getResultStats().getSummaryResult()

  /**
   * Returns a one liner test result summary string.
   */
  override fun getTestResultSummaryText(): String {
    val stats = getResultStats()
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
    val stats = AndroidTestResultStats()
    myTestCases.values.forEach {
      when(it.result) {
        AndroidTestCaseResult.PASSED -> stats.passed++
        AndroidTestCaseResult.FAILED -> stats.failed++
        AndroidTestCaseResult.SKIPPED -> stats.skipped++
        AndroidTestCaseResult.IN_PROGRESS -> stats.running++
        AndroidTestCaseResult.CANCELLED -> stats.cancelled++
        else -> {}
      }
    }
    return stats
  }

  override fun getResultStats(device: AndroidDevice): AndroidTestResultStats {
    val stats = AndroidTestResultStats()
    when(getTestCaseResult(device)) {
      AndroidTestCaseResult.PASSED -> stats.passed++
      AndroidTestCaseResult.FAILED -> stats.failed++
      AndroidTestCaseResult.SKIPPED -> stats.skipped++
      AndroidTestCaseResult.IN_PROGRESS -> stats.running++
      AndroidTestCaseResult.CANCELLED -> stats.cancelled++
      else -> {}
    }
    return stats
  }
}

/**
 * A row for displaying aggregated test results. Each row has test results for a device.
 */
private class AggregationRow(override val packageName: String = "",
                             override val className: String = "") : AndroidTestResults, DefaultMutableTreeNode() {
  override val methodName: String = ""
  override fun getTestCaseResult(device: AndroidDevice): AndroidTestCaseResult? = null
  override fun getTestResultSummary(): AndroidTestCaseResult = getResultStats().getSummaryResult()
  override fun getTestResultSummaryText(): String {
    val stats = getResultStats()
    return "${stats.passed + stats.skipped}/${stats.total}"
  }
  override fun getResultStats(): AndroidTestResultStats {
    return children?.fold(AndroidTestResultStats()) { acc, result ->
      (result as? AndroidTestResults)?.getResultStats()?.plus(acc) ?: acc
    }?:AndroidTestResultStats()
  }
  override fun getResultStats(device: AndroidDevice): AndroidTestResultStats {
    return children?.fold(AndroidTestResultStats()) { acc, result ->
      (result as? AndroidTestResults)?.getResultStats(device)?.plus(acc) ?: acc
    }?:AndroidTestResultStats()
  }
  override fun getLogcat(device: AndroidDevice): String = ""
  override fun getErrorStackTrace(device: AndroidDevice): String = ""
  override fun getBenchmark(device: AndroidDevice): String = ""
  override fun getRetentionSnapshot(device: AndroidDevice): File? = null
}
