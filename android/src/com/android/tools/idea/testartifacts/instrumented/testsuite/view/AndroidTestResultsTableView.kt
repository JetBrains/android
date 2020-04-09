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
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.google.common.annotations.VisibleForTesting
import com.intellij.icons.AllIcons
import com.intellij.openapi.progress.util.ColorProgressBar
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import java.awt.Component
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionEvent
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

/**
 * A table to display Android test results. Test results are grouped by device and test case. The column is a device name
 * and the row is a test case.
 */
class AndroidTestResultsTableView(listener: AndroidTestResultsTableListener) {
  private val myModel = AndroidTestResultsTableModel()
  private val myTableView = AndroidTestResultsTableViewComponent(myModel, listener)
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
    myModel.addTestResultsRow(device, testCase)
    refreshTable()
  }

  /**
   * Refreshes and redraws the table.
   */
  @UiThread
  fun refreshTable() {
    myTableView.updateColumnSizes()
    myModel.fireTableDataChanged()
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
  fun getModelForTesting(): ListTableModel<out AndroidTestResults> = myModel

  /**
   * Returns an internal view class for testing.
   */
  @VisibleForTesting
  fun getTableViewForTesting(): TableView<out AndroidTestResults> = myTableView
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
   */
  fun onAndroidTestResultsRowSelected(selectedResults: AndroidTestResults)
}

/**
 * An internal swing view component implementing AndroidTestResults table view.
 */
private class AndroidTestResultsTableViewComponent(model: AndroidTestResultsTableModel,
                                                   private val listener: AndroidTestResultsTableListener)
  : TableView<AndroidTestResultsRow>(model) {
  init {
    selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
  }

  override fun valueChanged(event: ListSelectionEvent) {
    super.valueChanged(event)
    selectedObject?.let {
      listener.onAndroidTestResultsRowSelected(it)
      clearSelection()
    }
  }
}

/**
 * A view model class of [AndroidTestResultsTableViewComponent].
 */
private class AndroidTestResultsTableModel :
  ListTableModel<AndroidTestResultsRow>(TestNameColumn(), TestStatusColumn()) {

  /**
   * A map of test results rows. The key is [AndroidTestCase.id] and the value is [AndroidTestResultsRow].
   * Note that [AndroidTestResultsRow] has test results for every devices.
   */
  private val myTestResultsRows = mutableMapOf<String, AndroidTestResultsRow>()

  /**
   * Creates and adds a new column for a given device.
   */
  fun addDeviceColumn(device: AndroidDevice) {
    columnInfos += AndroidTestResultsColumn(device)
  }

  /**
   * Creates and adds a new row for a pair of given [device] and [testCase]. If the row for the [testCase.id] has existed already,
   * it adds the [testCase] to that row.
   */
  fun addTestResultsRow(device: AndroidDevice, testCase: AndroidTestCase) {
    val row = myTestResultsRows.getOrPut(testCase.id) {
      AndroidTestResultsRow(testCase.name).also { addRow(it) }
    }
    row.addTestCase(device, testCase)
    fireTableDataChanged()
  }
}

/**
 * A column for displaying a test name.
 */
private class TestNameColumn : ColumnInfo<AndroidTestResultsRow, AndroidTestResultsRow>("Tests") {
  override fun valueOf(item: AndroidTestResultsRow): AndroidTestResultsRow = item
  override fun getCustomizedRenderer(o: AndroidTestResultsRow?, renderer: TableCellRenderer?): TableCellRenderer {
    return TestNameColumnCellRenderer
  }
}

private object TestNameColumnCellRenderer : DefaultTableCellRenderer() {
  override fun getTableCellRendererComponent(table: JTable?,
                                             value: Any?,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    val results = value as? AndroidTestResultsRow ?: return this

    super.getTableCellRendererComponent(table, results.testCaseName, isSelected, hasFocus, row, column)
    icon = when(value.getTestResultSummary()) {
      AndroidTestCaseResult.PASSED -> AllIcons.RunConfigurations.TestPassed
      AndroidTestCaseResult.SKIPPED -> AllIcons.RunConfigurations.TestSkipped
      AndroidTestCaseResult.FAILED -> AllIcons.RunConfigurations.TestFailed
      else -> null
    }
    this.iconTextGap
    return this
  }
}

/**
 * A column for displaying an aggregated test result grouped by a test case ID.
 */
private class TestStatusColumn : ColumnInfo<AndroidTestResultsRow, AndroidTestResultsRow>("Status") {
  override fun valueOf(item: AndroidTestResultsRow): AndroidTestResultsRow = item
  override fun getWidth(table: JTable): Int = 80
  override fun getCustomizedRenderer(o: AndroidTestResultsRow?, renderer: TableCellRenderer?): TableCellRenderer {
    return TestStatusColumnCellRenderer
  }
}

private object TestStatusColumnCellRenderer : DefaultTableCellRenderer() {
  override fun getTableCellRendererComponent(table: JTable?,
                                             value: Any?,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    val results = value as? AndroidTestResultsRow ?: return this
    super.getTableCellRendererComponent(table, results.getTestResultSummaryText(), isSelected, hasFocus, row, column)
    horizontalAlignment = CENTER
    horizontalTextPosition = CENTER
    foreground = when(results.getTestResultSummary()) {
      AndroidTestCaseResult.PASSED -> ColorProgressBar.GREEN
      AndroidTestCaseResult.FAILED -> ColorProgressBar.RED_TEXT
      AndroidTestCaseResult.SKIPPED -> ColorProgressBar.GREEN
      else -> ColorProgressBar.RED_TEXT
    }
    return this
  }
}

/**
 * A column for displaying an individual test case result on a given [device].
 *
 * @param device shows an individual test case result in this column for a given [device]
 */
private class AndroidTestResultsColumn(private val device: AndroidDevice) :
  ColumnInfo<AndroidTestResultsRow, AndroidTestCaseResult?>(device.name) {
  override fun valueOf(item: AndroidTestResultsRow): AndroidTestCaseResult? {
    return item.getTestCaseResult(device)
  }
  override fun getWidth(table: JTable): Int = 120
  override fun getCustomizedRenderer(o: AndroidTestResultsRow?, renderer: TableCellRenderer?): TableCellRenderer {
    return AndroidTestResultsColumnCellRenderer
  }
}

private object AndroidTestResultsColumnCellRenderer : DefaultTableCellRenderer() {
  override fun getTableCellRendererComponent(table: JTable?,
                                             value: Any?,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column)
    horizontalAlignment = CENTER
    icon = when(value) {
      AndroidTestCaseResult.PASSED -> AllIcons.RunConfigurations.TestPassed
      AndroidTestCaseResult.SKIPPED -> AllIcons.RunConfigurations.TestSkipped
      AndroidTestCaseResult.FAILED -> AllIcons.RunConfigurations.TestFailed
      else -> null
    }
    return this
  }
}

/**
 * A row for displaying test results. Each row has test results for every device.
 */
private class AndroidTestResultsRow(override val testCaseName: String) : AndroidTestResults {
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
   * Returns a one liner test result summary string.
   */
  fun getTestResultSummaryText(): String {
    val (passed, failed, skipped) = getResultStats()
    return when {
      failed == 1 -> "Fail"
      failed > 0 -> "Fail ($failed)"
      skipped == myTestCases.size -> "Skip"
      passed + skipped == myTestCases.size -> "Pass"
      else -> ""
    }
  }

  /**
   * Returns an aggregated test result.
   */
  fun getTestResultSummary(): AndroidTestCaseResult {
    val (passed, failed, skipped) = getResultStats()
    return when {
      failed > 0 -> AndroidTestCaseResult.FAILED
      skipped == myTestCases.size -> AndroidTestCaseResult.SKIPPED
      else -> AndroidTestCaseResult.PASSED
    }
  }

  private fun getResultStats(): Triple<Int, Int, Int> {
    var passed = 0
    var failed = 0
    var skipped = 0
    myTestCases.values.forEach {
      when(it.result) {
        AndroidTestCaseResult.PASSED -> passed++
        AndroidTestCaseResult.FAILED -> failed++
        AndroidTestCaseResult.SKIPPED -> skipped++
      }
    }
    return Triple(passed, failed, skipped)
  }
}