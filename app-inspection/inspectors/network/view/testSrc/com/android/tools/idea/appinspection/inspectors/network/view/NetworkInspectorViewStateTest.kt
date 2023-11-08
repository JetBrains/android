package com.android.tools.idea.appinspection.inspectors.network.view

import com.android.tools.adtui.table.ConfigColumnTableAspect.ColumnInfo
import com.android.tools.idea.appinspection.inspectors.network.view.connectionsview.ConnectionColumn
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Tests for [NetworkInspectorViewState] */
class NetworkInspectorViewStateTest {
  @Test
  fun upgrade_columnsAdded() {
    val state = NetworkInspectorViewState()

    state.columns =
      mutableListOf(
        ColumnInfo("Name"),
        ColumnInfo("Type"),
        ColumnInfo("Size"),
        ColumnInfo("Status"),
        ColumnInfo("Time"),
        ColumnInfo("Timeline"),
      )

    val newState = state.state

    assertThat(newState.columns.map { it.name })
      .containsExactly(
        "URL",
        "Name",
        "Path",
        "Host",
        "Method",
        "Transport",
        "Scheme",
        "Type",
        "Size",
        "Status",
        "Time",
        "Timeline",
        "Request Size",
        "Request Headers",
        "Response Headers",
        "Request Time",
        "Response Time"
      )
  }

  @Test
  fun upgrade_columnsRemoved() {
    val state = NetworkInspectorViewState()

    val list =
      ConnectionColumn.values().map { ColumnInfo(it.displayString, 0.0, true) } +
        ColumnInfo("Unknown")
    state.columns = list.toMutableList()
    val newState = state.state

    assertThat(newState.columns.find { it.name == "Unknown" }).isNull()
    assertThat(newState.columns.map { it.name })
      .containsExactly(
        "URL",
        "Name",
        "Path",
        "Host",
        "Method",
        "Transport",
        "Scheme",
        "Type",
        "Size",
        "Status",
        "Time",
        "Timeline",
        "Request Size",
        "Request Headers",
        "Response Headers",
        "Request Time",
        "Response Time"
      )
  }

  @Test
  fun upgrade_columnsRenamed() {
    val state = NetworkInspectorViewState()

    val list = ConnectionColumn.values().map { ColumnInfo(it.displayString, 0.0, true) }
    list[0].name = "Old Name"
    state.columns = list.toMutableList()
    val newState = state.state

    assertThat(newState.columns.find { it.name == "Unknown" }).isNull()
    assertThat(newState.columns.map { it.name })
      .containsExactly(
        "URL",
        "Name",
        "Path",
        "Host",
        "Method",
        "Transport",
        "Scheme",
        "Type",
        "Size",
        "Status",
        "Time",
        "Timeline",
        "Request Size",
        "Request Headers",
        "Response Headers",
        "Request Time",
        "Response Time"
      )
  }
}
