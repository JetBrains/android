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
package com.google.services.firebase.insights.ui

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.idea.flags.StudioFlags
import com.google.services.firebase.insights.AppInsightsModuleController
import com.google.services.firebase.insights.LoadingState
import com.google.services.firebase.insights.datamodel.CancellableTimeoutException
import com.google.services.firebase.insights.datamodel.Issue
import com.google.services.firebase.insights.datamodel.NoDevicesSelectedException
import com.google.services.firebase.insights.datamodel.NoOperatingSystemsSelectedException
import com.google.services.firebase.insights.datamodel.NoTypesSelectedException
import com.google.services.firebase.insights.datamodel.NoVersionsSelectedException
import com.google.services.firebase.insights.datamodel.RevertibleException
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent.AppQualityInsightsCrashOpenDetails.CrashOpenSource.LIST
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.table.TableView
import com.intellij.util.containers.Convertor
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.UIManager
import javax.swing.event.ListSelectionListener
import javax.swing.table.JTableHeader
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly

class AppInsightsIssuesTableView(
  model: AppInsightsIssuesTableModel,
  moduleController: AppInsightsModuleController,
) : Disposable {
  val component: JComponent
  private val speedSearch: TableSpeedSearch
  private val tableHeader: JTableHeader
  private val changeListener: ListSelectionListener
  private val loadingPanel: JBLoadingPanel
  private val table: IssuesTableView

  init {
    val containerPanel = JPanel(TabularLayout("*", "Fit,*"))
    loadingPanel = JBLoadingPanel(BorderLayout(), this)
    table = IssuesTableView(model)
    speedSearch =
      TableSpeedSearch(
        table,
        Convertor {
          if (it is Issue) AppInsightsIssuesTableCellRenderer.convertToSearchText(it)
          else it.toString()
        }
      )
    tableHeader = table.tableHeader
    tableHeader.reorderingAllowed = false
    containerPanel.add(table.tableHeader, TabularLayout.Constraint(0, 0))
    containerPanel.add(
      ScrollPaneFactory.createScrollPane(table, true),
      TabularLayout.Constraint(1, 0)
    )
    changeListener = ListSelectionListener {
      if (!it.valueIsAdjusting) {
        moduleController.selectIssue(table.selection.firstOrNull(), LIST)
      }
    }
    table.selectionModel.addListSelectionListener(changeListener)
    loadingPanel.add(containerPanel)
    loadingPanel.setLoadingText("Loading")
    loadingPanel.startLoading()
    loadingPanel.minimumSize = Dimension(JBUIScale.scale(90), 0)
    component = loadingPanel

    moduleController.coroutineScope.launch {
      moduleController.state.map { it.issues }.distinctUntilChanged().collect { issues ->
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
                  "You don't have any issues yet. Keep up the good work!",
                  EMPTY_STATE_TEXT_FORMAT,
                  null
                )
              }
            }
            if (model.items != issues.value.value.items) {
              suppressListener(table) { model.items = issues.value.value.items }
            }
            if (table.selectedObject != issues.value.value.selected) {
              Logger.getInstance(AppInsightsIssuesTableView::class.java)
                .info(
                  "Changed selection from issue ${table.selectedObject?.issueDetails?.id} to ${issues.value.value.selected?.issueDetails?.id}"
                )
              // when selection changes it fires multiple events:
              // 1. clear current selection fires selected item as null
              // 2. new selection is set as requested
              // This causes multiple round trips and updates the model to null, then to the actual
              // value.
              // To avoid this we suppress the change listener since as the model change was the
              // source of this event,
              // and we don't need to fire an issue change event.
              suppressListener(table) {
                table.selection = listOfNotNull(issues.value.value.selected)
              }
              table.scrollRectToVisible(table.getCellRect(table.selectedRow, 0, true))
            } else {
              Logger.getInstance(AppInsightsIssuesTableView::class.java)
                .info("Issue not changed: ${table.selectedObject?.issueDetails?.id}.")
            }
            loadingPanel.stopLoading()
          }
          is LoadingState.NetworkFailure -> {
            table.tableEmptyText.apply {
              clear()
              appendText("Request failed", EMPTY_STATE_TITLE_FORMAT)
              appendLine("You can ", EMPTY_STATE_TEXT_FORMAT, null)
              appendText(
                "retry",
                EMPTY_STATE_LINK_FORMAT,
              ) { moduleController.refresh() }
              appendText(
                " the request",
                EMPTY_STATE_TEXT_FORMAT,
              )
              if (StudioFlags.OFFLINE_MODE_SUPPORT_ENABLED.get()) {
                appendText(" or, if you currently don't", EMPTY_STATE_TEXT_FORMAT)
                appendLine("have a network connection, enter ", EMPTY_STATE_TEXT_FORMAT, null)
                appendText(
                  "Offline Mode",
                  EMPTY_STATE_LINK_FORMAT,
                ) { moduleController.enterOfflineMode() }
              }
              appendText(".", EMPTY_STATE_TEXT_FORMAT)
            }
            model.items = emptyList()
            loadingPanel.stopLoading()
          }
          is LoadingState.Failure -> {
            when (val cause = issues.cause) {
              is NoTypesSelectedException -> {
                table.tableEmptyText.apply {
                  clear()
                  appendText("No types selected", EMPTY_STATE_TITLE_FORMAT)
                  appendSecondaryText(
                    "No event types are selected. Enable a type above to see issues.",
                    EMPTY_STATE_TEXT_FORMAT,
                    null
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
                    null
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
                    null
                  )
                }
              }
              is NoOperatingSystemsSelectedException -> {
                table.tableEmptyText.apply {
                  clear()
                  appendText(
                    "No operating systems selected",
                    SimpleTextAttributes.REGULAR_ATTRIBUTES
                  )
                  appendSecondaryText(
                    "No operating systems are selected. Enable an operating system above to see issues.",
                    SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, null),
                    null
                  )
                }
              }
              is RevertibleException -> {
                when (val revertibleCause = cause.cause) {
                  is CancellableTimeoutException -> {
                    // TODO: Add loading spinner
                    table.tableEmptyText.apply {
                      clear()
                      appendText(
                        "Fetching issues is taking longer than expected.",
                        EMPTY_STATE_TITLE_FORMAT
                      )

                      if (StudioFlags.OFFLINE_MODE_SUPPORT_ENABLED.get()) {
                        appendSecondaryText("You can wait, ", EMPTY_STATE_TEXT_FORMAT, null)
                        appendSecondaryText("retry", EMPTY_STATE_LINK_FORMAT) {
                          moduleController.refresh()
                        }
                        appendSecondaryText(" or ", EMPTY_STATE_TEXT_FORMAT, null)
                        appendSecondaryText("enter offline mode", EMPTY_STATE_LINK_FORMAT) {
                          moduleController.enterOfflineMode()
                        }
                        appendSecondaryText(" to see cached data.", EMPTY_STATE_TEXT_FORMAT, null)
                      } else if (cause.snapshot != null) {
                        appendSecondaryText("You can wait or ", EMPTY_STATE_TEXT_FORMAT, null)
                        appendSecondaryText("cancel the request", EMPTY_STATE_LINK_FORMAT) {
                          moduleController.revertToSnapshot(cause.snapshot)
                        }
                      }
                    }
                  }
                  else -> {
                    table.tableEmptyText.apply {
                      clear()
                      appendText(
                        issues.message ?: revertibleCause?.message ?: "An unknown failure occurred",
                        EMPTY_STATE_TITLE_FORMAT
                      )
                      if (cause.snapshot != null) {
                        appendSecondaryText("Go Back", EMPTY_STATE_LINK_FORMAT) {
                          moduleController.revertToSnapshot(cause.snapshot)
                        }
                      }
                    }
                  }
                }
              }
              else -> {
                table.tableEmptyText.apply {
                  clear()
                  appendText(
                    issues.message ?: "An unknown failure occurred",
                    EMPTY_STATE_TITLE_FORMAT
                  )
                }
              }
            }
            model.items = emptyList()
            loadingPanel.stopLoading()
          }
        }
      }
      Logger.getInstance(AppInsightsIssuesTableView::class.java)
        .info("Collection terminated on table view.")
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

  inner class IssuesTableView(model: AppInsightsIssuesTableModel) : TableView<Issue>(model) {
    val tableEmptyText = AppInsightsStatusText(this) { isEmpty && !loadingPanel.isLoading }

    init {
      selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
      setShowGrid(false)
      isFocusable = true
      background = primaryContentBackground
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
      AppInsightsIssuesTableCellRenderer.updateUI()
      tableEmptyText?.setFont(StartupUiUtil.getLabelFont())
    }

    @TestOnly fun isLoading() = loadingPanel.isLoading
  }
}
