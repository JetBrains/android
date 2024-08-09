/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.insights.ui

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.CancellableTimeoutException
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.NoDevicesSelectedException
import com.android.tools.idea.insights.NoOperatingSystemsSelectedException
import com.android.tools.idea.insights.NoTypesSelectedException
import com.android.tools.idea.insights.NoVersionsSelectedException
import com.android.tools.idea.insights.RevertibleException
import com.android.tools.idea.insights.analytics.IssueSelectionSource
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.observable.util.addMouseListener
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.table.TableView
import com.intellij.util.containers.Convertor
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.TimerUtil
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.event.MouseListener
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.UIManager
import javax.swing.event.ListSelectionListener
import javax.swing.table.JTableHeader
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly

class AppInsightsIssuesTableView(
  model: AppInsightsIssuesTableModel,
  private val controller: AppInsightsProjectLevelController,
  private val renderer: AppInsightsTableCellRenderer,
  tableMouseListener: MouseListener?,
) : Disposable {
  val component: JComponent
  private val speedSearch: TableSpeedSearch
  private val tableHeader: JTableHeader
  private val changeListener: ListSelectionListener
  private val loadingPanel: JBLoadingPanel
  val table: IssuesTableView

  private val scope = AndroidCoroutineScope(this, AndroidDispatchers.uiThread)

  init {
    loadingPanel = JBLoadingPanel(TabularLayout("*", "Fit,*"), this)
    table = IssuesTableView(model)

    tableMouseListener?.let { table.addMouseListener(tableMouseListener) }

    speedSearch =
      TableSpeedSearch(
        table,
        Convertor { if (it is AppInsightsIssue) convertToSearchText(it) else it.toString() },
      )
    tableHeader = table.tableHeader
    tableHeader.reorderingAllowed = false
    loadingPanel.add(table.tableHeader, TabularLayout.Constraint(0, 0))
    loadingPanel.add(
      ScrollPaneFactory.createScrollPane(table, true),
      TabularLayout.Constraint(1, 0),
    )
    changeListener = ListSelectionListener {
      if (!it.valueIsAdjusting) {
        controller.selectIssue(table.selection.firstOrNull(), IssueSelectionSource.LIST)
      }
    }
    table.selectionModel.addListSelectionListener(changeListener)
    loadingPanel.setLoadingText("Loading")
    loadingPanel.startLoading()
    loadingPanel.minimumSize = Dimension(JBUIScale.scale(300), 0)
    component = loadingPanel

    scope.launch {
      controller.state
        .map { it.issues }
        .distinctUntilChanged()
        .collect { issues ->
          when (issues) {
            is LoadingState.Loading -> {
              loadingPanel.startLoading()
              model.items = emptyList()
            }
            is LoadingState.Ready -> {
              if (issues.value.value.items.isEmpty()) {
                table.tableEmptyText.apply {
                  clear()
                  appendText("No issues", EMPTY_STATE_TITLE_FORMAT)
                  appendSecondaryText(
                    "It can take 24-48 hours for crashes to appear",
                    EMPTY_STATE_TEXT_FORMAT,
                    null,
                  )
                }
              }
              if (model.items != issues.value.value.items) {
                suppressListener(table) {
                  model.items = issues.value.value.items
                  table.updateColumnSizes()
                }
              }
              if (table.selectedObject != issues.value.value.selected) {
                LOGGER.info(
                  "Changed selection from issue ${table.selectedObject?.issueDetails?.id} to ${issues.value.value.selected?.issueDetails?.id}"
                )
                // when selection changes it fires multiple events:
                // 1. clear current selection fires selected item as null
                // 2. new selection is set as requested
                // This causes multiple round trips and updates the model to null, then to the
                // actual
                // value.
                // To avoid this we suppress the change listener since as the model change was the
                // source of this event,
                // and we don't need to fire an issue change event.
                suppressListener(table) {
                  table.selection = listOfNotNull(issues.value.value.selected)
                }
                table.scrollRectToVisible(table.getCellRect(table.selectedRow, 0, true))
              } else {
                LOGGER.info("Issue not changed: ${table.selectedObject?.issueDetails?.id}.")
              }
              loadingPanel.stopLoading()
            }
            is LoadingState.NetworkFailure -> {
              table.tableEmptyText.apply {
                clear()
                appendText("Request failed", EMPTY_STATE_TITLE_FORMAT)
                appendLine("You can ", EMPTY_STATE_TEXT_FORMAT, null)
                appendText("retry", EMPTY_STATE_LINK_FORMAT) { controller.refresh() }
                appendText(" the request", EMPTY_STATE_TEXT_FORMAT)
                appendText(" or, if you currently don't", EMPTY_STATE_TEXT_FORMAT)
                appendLine("have a network connection, enter ", EMPTY_STATE_TEXT_FORMAT, null)
                appendText("Offline Mode", EMPTY_STATE_LINK_FORMAT) {
                  controller.enterOfflineMode()
                }
                appendText(".", EMPTY_STATE_TEXT_FORMAT)
              }
              model.items = emptyList()
              loadingPanel.stopLoading()
            }
            is LoadingState.Failure -> {
              when (issues.cause) {
                is NoTypesSelectedException -> {
                  table.tableEmptyText.apply {
                    clear()
                    appendText("No types selected", EMPTY_STATE_TITLE_FORMAT)
                    appendSecondaryText(
                      "No event types are selected. Enable a type above to see issues.",
                      EMPTY_STATE_TEXT_FORMAT,
                      null,
                    )
                  }
                }
                is NoVersionsSelectedException -> {
                  table.tableEmptyText.apply {
                    clear()
                    appendText("No versions selected", EMPTY_STATE_TITLE_FORMAT)
                    appendSecondaryText(
                      "No versions are selected. Enable a version above to see issues.",
                      EMPTY_STATE_TEXT_FORMAT,
                      null,
                    )
                  }
                }
                is NoDevicesSelectedException -> {
                  table.tableEmptyText.apply {
                    clear()
                    appendText("No devices selected", EMPTY_STATE_TITLE_FORMAT)
                    appendSecondaryText(
                      "No devices are selected. Enable a device above to see issues.",
                      EMPTY_STATE_TEXT_FORMAT,
                      null,
                    )
                  }
                }
                is NoOperatingSystemsSelectedException -> {
                  table.tableEmptyText.apply {
                    clear()
                    appendText("No operating systems selected", EMPTY_STATE_TITLE_FORMAT)
                    appendSecondaryText(
                      "No operating systems are selected. Enable an operating system above to see issues.",
                      EMPTY_STATE_TEXT_FORMAT,
                      null,
                    )
                  }
                }
                is RevertibleException -> {
                  handleRevertibleException(issues)
                }
                else -> {
                  table.tableEmptyText.apply {
                    clear()
                    appendText(
                      issues.message ?: "An unknown failure occurred",
                      EMPTY_STATE_TITLE_FORMAT,
                    )
                  }
                }
              }
              model.items = emptyList()
              loadingPanel.stopLoading()
            }
          }
        }
      LOGGER.info("Collection terminated on table view.")
    }
  }

  private inline fun suppressListener(table: TableView<*>, block: () -> Unit) {
    table.selectionModel.removeListSelectionListener(changeListener)
    try {
      block()
    } finally {
      table.selectionModel.addListSelectionListener(changeListener)
    }
  }

  fun setHeaderHeight(height: Int) {
    tableHeader.preferredSize = Dimension(0, height)
  }

  override fun dispose() = Unit

  inner class IssuesTableView(model: AppInsightsIssuesTableModel) :
    TableView<AppInsightsIssue>(model) {
    val tableEmptyText =
      AppInsightsStatusText(this) { (isEmpty && !loadingPanel.isLoading).also { updateTimer(it) } }

    // This is required to force the spinner icon to animate in the status text.
    private val repaintTimer =
      TimerUtil.createNamedTimer("AppInsightsIssuesTableView", 10) { repaint() }

    init {
      selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
      setShowGrid(false)
      isFocusable = true
      background = primaryContentBackground
    }

    private fun updateTimer(isEmptyTextVisible: Boolean) {
      if (isEmptyTextVisible && tableEmptyText.component.icon is AnimatedIcon) {
        repaintTimer.restart()
      } else {
        repaintTimer.stop()
      }
    }

    override fun getEmptyText() = tableEmptyText

    override fun createDefaultTableHeader(): JTableHeader {
      return object : JBTableHeader() {
        override fun updateUI() {
          super.updateUI()
          background = UIManager.getColor("Panel.background")
          font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
        }
      }
    }

    override fun updateUI() {
      super.updateUI()
      renderer.updateRenderer()
      tableEmptyText?.setFont(StartupUiUtil.labelFont)
    }

    @TestOnly fun isLoading() = loadingPanel.isLoading
  }

  /** Returns true if loading should be stopped. */
  private fun handleRevertibleException(failure: LoadingState.Failure) {
    val cause = failure.cause as RevertibleException
    when (val revertibleCause = cause.cause) {
      is CancellableTimeoutException -> {
        table.tableEmptyText.apply {
          clear()
          appendLine(
            AnimatedIcon.Default(),
            "Fetching issues is taking longer than expected.",
            EMPTY_STATE_TITLE_FORMAT,
            null,
          )
          appendSecondaryText("You can wait, ", EMPTY_STATE_TEXT_FORMAT, null)
          appendSecondaryText("retry", EMPTY_STATE_LINK_FORMAT) { controller.refresh() }
          appendSecondaryText(" or ", EMPTY_STATE_TEXT_FORMAT, null)
          appendSecondaryText("enter offline mode", EMPTY_STATE_LINK_FORMAT) {
            controller.enterOfflineMode()
          }
          appendSecondaryText(" to see cached data.", EMPTY_STATE_TEXT_FORMAT, null)
        }
      }
      else -> {
        table.tableEmptyText.apply {
          clear()
          appendText(
            failure.message ?: revertibleCause?.message ?: "An unknown failure occurred",
            EMPTY_STATE_TITLE_FORMAT,
          )
          if (cause.snapshot != null) {
            appendSecondaryText("Go Back", EMPTY_STATE_LINK_FORMAT) {
              controller.revertToSnapshot(cause.snapshot as AppInsightsState)
            }
          }
        }
      }
    }
  }

  companion object {
    val LOGGER = Logger.getInstance(AppInsightsIssuesTableView::class.java)
  }
}
