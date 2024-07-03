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
package com.android.tools.idea.appinspection.inspectors.network.view.details

import com.android.tools.adtui.LegendComponent
import com.android.tools.adtui.LegendConfig
import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.TabularLayout.SizingRule
import com.android.tools.adtui.TabularLayout.SizingRule.Type.FIXED
import com.android.tools.adtui.TabularLayout.SizingRule.Type.PROPORTIONAL
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.formatter.NumberFormatter
import com.android.tools.adtui.model.legend.FixedLegend
import com.android.tools.adtui.model.legend.LegendComponentModel
import com.android.tools.idea.appinspection.inspectors.network.model.connections.ConnectionData
import com.android.tools.idea.appinspection.inspectors.network.view.ConnectionsStateChart
import com.android.tools.idea.appinspection.inspectors.network.view.NetworkState
import com.android.tools.idea.appinspection.inspectors.network.view.details.DataComponentFactory.ConnectionType
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.PlatformColors
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.TimeUnit
import java.util.function.LongFunction
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.JTextArea
import javax.swing.SwingConstants

/**
 * Tab which shows a bunch of useful, high level information for a network request.
 *
 * This tab will be the first one shown to the user when they first select a request.
 */
internal class OverviewTabContent : TabContent() {
  private val layout = TabularLayout("*").setVGap(PAGE_VGAP)
  private val contentPanel = JPanel(layout)
  private val overviewScroll: JBScrollPane = createVerticalScrollPane(contentPanel)
  override val title = "Overview"

  override fun createComponent(): JComponent {
    contentPanel.border = JBUI.Borders.empty(PAGE_VGAP, HORIZONTAL_PADDING, 0, HORIZONTAL_PADDING)
    overviewScroll.verticalScrollBar.unitIncrement = SCROLL_UNIT
    overviewScroll.addComponentListener(
      object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent) {
          updateRowSizing()
        }
      }
    )
    return overviewScroll
  }

  override fun populateFor(data: ConnectionData?, dataComponentFactory: DataComponentFactory) {
    contentPanel.removeAll()
    if (data == null) {
      return
    }
    val payloadViewer = dataComponentFactory.createDataViewer(ConnectionType.RESPONSE, false)
    val responsePayloadComponent: JComponent? = payloadViewer?.component
    var row = 0
    if (responsePayloadComponent != null) {
      responsePayloadComponent.name = ID_RESPONSE_PAYLOAD_VIEWER
      contentPanel.add(responsePayloadComponent, TabularLayout.Constraint(row++, 0))
    }
    contentPanel.add(createFields(data), TabularLayout.Constraint(row, 0))
    updateRowSizing()
  }

  /**
   * This is a hyperlink which will break and wrap when it hits the right border of its container.
   */
  private class WrappedHyperlink(url: String) : JTextArea(url) {
    init {
      lineWrap = true
      isEditable = false
      background = UIUtil.getLabelBackground()
      foreground = PlatformColors.BLUE
      font = JBFont.label().asPlain()
      val mouseAdapter = getMouseAdapter(url)
      addMouseListener(mouseAdapter)
      addMouseMotionListener(mouseAdapter)
    }

    override fun setBackground(ignored: Color?) {
      // ignore the input color and explicitly set the color provided by UIUtil.getLabelBackground()
      super.setBackground(UIUtil.getLabelBackground())
    }

    override fun setFont(ignored: Font?) {
      // ignore the input font and explicitly set the label font provided by JBFont
      super.setFont(JBFont.label().asPlain())
    }

    private fun getMouseAdapter(url: String): MouseAdapter {
      return object : MouseAdapter() {
        override fun mouseEntered(e: MouseEvent) {
          mouseMoved(e)
        }

        override fun mouseExited(e: MouseEvent) {
          cursor = Cursor.getDefaultCursor()
        }

        override fun mouseClicked(e: MouseEvent) {
          if (isMouseOverText(e)) {
            BrowserUtil.browse(url)
          }
        }

        override fun mouseMoved(e: MouseEvent) {
          cursor =
            if (isMouseOverText(e)) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            else Cursor.getDefaultCursor()
        }

        private fun isMouseOverText(e: MouseEvent): Boolean {
          return viewToModel2D(e.point) < document.length
        }
      }
    }
  }

  private fun updateRowSizing() {
    // If there is a payload component, restrict its height to 40%.
    val sizingRule =
      when {
        contentPanel.components.size > 1 ->
          SizingRule(FIXED, (overviewScroll.viewport.height * 0.4f).toInt())
        else -> SizingRule(PROPORTIONAL, 1)
      }
    layout.setRowSizing(0, sizingRule)
    layout.layoutContainer(contentPanel)
  }

  companion object {
    private val TIME_FORMATTER: LongFunction<String> =
      LongFunction<String> { time: Long ->
        if (time >= 0) StringUtil.formatDuration(TimeUnit.MICROSECONDS.toMillis(time)) else "*"
      }
    @VisibleForTesting const val ID_REQUEST_TYPE = "REQUEST_TYPE"
    @VisibleForTesting const val ID_REQUEST_SIZE = "REQUEST_SIZE"
    @VisibleForTesting const val ID_RESPONSE_TYPE = "RESPONSE_TYPE"
    @VisibleForTesting const val ID_RESPONSE_SIZE = "RESPONSE_SIZE"
    @VisibleForTesting const val ID_URL = "URL"
    @VisibleForTesting const val ID_TIMING = "TIMING"
    @VisibleForTesting const val ID_INITIATING_THREAD = "INITIATING_THREAD"
    @VisibleForTesting const val ID_OTHER_THREADS = "OTHER_THREADS"
    @VisibleForTesting const val ID_RESPONSE_PAYLOAD_VIEWER = "RESPONSE_PAYLOAD_VIEWER"

    private fun createFields(data: ConnectionData): JComponent {
      val myFieldsPanel = JPanel(TabularLayout("Fit-,40px,*").setVGap(SECTION_VGAP))

      var row = 0
      myFieldsPanel.add(NoWrapBoldLabel("Request"), TabularLayout.Constraint(row, 0))
      myFieldsPanel.add(JLabel(data.name), TabularLayout.Constraint(row, 2))
      row++
      myFieldsPanel.add(NoWrapBoldLabel("Method"), TabularLayout.Constraint(row, 0))
      myFieldsPanel.add(JLabel(data.method), TabularLayout.Constraint(row, 2))

      row++
      myFieldsPanel.add(NoWrapBoldLabel("Status"), TabularLayout.Constraint(row, 0))
      val statusCode = JLabel(data.status)
      myFieldsPanel.add(statusCode, TabularLayout.Constraint(row, 2))

      val requestType = data.requestType
      if (requestType.isNotEmpty()) {
        row++
        myFieldsPanel.add(NoWrapBoldLabel("Request type"), TabularLayout.Constraint(row, 0))
        val contentTypeLabel = JLabel(requestType)
        contentTypeLabel.name = ID_REQUEST_TYPE
        myFieldsPanel.add(contentTypeLabel, TabularLayout.Constraint(row, 2))
      }

      val requestSize = data.requestPayload.size()
      if (requestSize > 0) {
        row++
        myFieldsPanel.add(NoWrapBoldLabel("Request size"), TabularLayout.Constraint(row, 0))
        val contentLengthLabel = JLabel(NumberFormatter.formatFileSize(requestSize.toLong()))
        contentLengthLabel.name = ID_REQUEST_SIZE
        myFieldsPanel.add(contentLengthLabel, TabularLayout.Constraint(row, 2))
      }

      val responseType = data.responseType
      if (responseType.isNotEmpty()) {
        row++
        myFieldsPanel.add(NoWrapBoldLabel("Response type"), TabularLayout.Constraint(row, 0))
        val contentTypeLabel = JLabel(responseType)
        contentTypeLabel.name = ID_RESPONSE_TYPE
        myFieldsPanel.add(contentTypeLabel, TabularLayout.Constraint(row, 2))
      }

      val responseSize = data.responsePayload.size()
      if (responseSize > 0) {
        row++
        myFieldsPanel.add(NoWrapBoldLabel("Response size"), TabularLayout.Constraint(row, 0))
        val contentLengthLabel = JLabel(NumberFormatter.formatFileSize(responseSize.toLong()))
        contentLengthLabel.name = ID_RESPONSE_SIZE
        myFieldsPanel.add(contentLengthLabel, TabularLayout.Constraint(row, 2))
      }

      row++
      myFieldsPanel.add(NoWrapBoldLabel("Initiating thread"), TabularLayout.Constraint(row, 0))
      val initiatingThreadLabel = JLabel(data.threads[0].name)
      initiatingThreadLabel.name = ID_INITIATING_THREAD
      myFieldsPanel.add(initiatingThreadLabel, TabularLayout.Constraint(row, 2))
      if (data.threads.size > 1) {
        val otherThreadsBuilder = StringBuilder()
        for (i in 1 until data.threads.size) {
          if (otherThreadsBuilder.isNotEmpty()) {
            otherThreadsBuilder.append(", ")
          }
          otherThreadsBuilder.append(data.threads[i].name)
        }
        row++
        myFieldsPanel.add(NoWrapBoldLabel("Other threads"), TabularLayout.Constraint(row, 0))
        val otherThreadsLabel = JLabel(otherThreadsBuilder.toString())
        otherThreadsLabel.name = ID_OTHER_THREADS
        myFieldsPanel.add(otherThreadsLabel, TabularLayout.Constraint(row, 2))
      }

      row++
      val urlLabel = NoWrapBoldLabel("URL")
      urlLabel.verticalAlignment = SwingConstants.TOP
      myFieldsPanel.add(urlLabel, TabularLayout.Constraint(row, 0))
      val hyperlink = WrappedHyperlink(data.url)
      hyperlink.name = ID_URL
      myFieldsPanel.add(hyperlink, TabularLayout.Constraint(row, 2))

      row++
      val separator = JSeparator()
      separator.minimumSize = separator.preferredSize
      val gap = PAGE_VGAP - SECTION_VGAP - separator.preferredSize.getHeight().toInt() / 2
      val separatorContainer = JPanel(VerticalFlowLayout(0, gap))
      separatorContainer.add(separator)
      myFieldsPanel.add(separatorContainer, TabularLayout.Constraint(row, 0, 1, 3))

      row++
      val timingLabel = NoWrapBoldLabel("Timing")
      timingLabel.verticalAlignment = SwingConstants.TOP
      myFieldsPanel.add(timingLabel, TabularLayout.Constraint(row, 0))
      val timingBar: JComponent = createTimingBar(data)
      timingBar.name = ID_TIMING
      myFieldsPanel.add(timingBar, TabularLayout.Constraint(row, 2))
      return myFieldsPanel
    }

    private fun createTimingBar(data: ConnectionData): JComponent {
      val panel = JPanel()
      panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
      val range =
        Range(
          data.requestStartTimeUs.toDouble(),
          (if (data.connectionEndTimeUs > 0) data.connectionEndTimeUs
            else data.requestStartTimeUs + 1)
            .toDouble(),
        )
      val connectionsChart = ConnectionsStateChart(data, range)
      connectionsChart.component.minimumSize = Dimension(0, JBUI.scale(28))
      connectionsChart.setHeightGap(0f)
      panel.add(connectionsChart.component)
      var sentTime: Long = -1
      var receivedTime: Long = -1
      if (data.responseStartTimeUs > 0) {
        sentTime = data.responseStartTimeUs - data.requestStartTimeUs
        receivedTime = data.responseCompleteTimeUs - data.responseStartTimeUs
      } else if (data.connectionEndTimeUs > 0) {
        sentTime = data.connectionEndTimeUs - data.requestStartTimeUs
        receivedTime = 0
      }
      val sentLegend = FixedLegend("Sent", TIME_FORMATTER.apply(sentTime))
      val receivedLegend = FixedLegend("Received", TIME_FORMATTER.apply(receivedTime))
      val legendModel = LegendComponentModel()
      legendModel.add(sentLegend)
      legendModel.add(receivedLegend)

      // TODO: Add waiting time in (currently hidden because it's always 0)
      val legend: LegendComponent =
        LegendComponent.Builder(legendModel)
          .setLeftPadding(0)
          .setVerticalPadding(JBUI.scale(8))
          .build()
      legend.configure(
        sentLegend,
        LegendConfig(
          LegendConfig.IconType.BOX,
          connectionsChart.colors.getColor(NetworkState.SENDING),
        ),
      )
      legend.configure(
        receivedLegend,
        LegendConfig(
          LegendConfig.IconType.BOX,
          connectionsChart.colors.getColor(NetworkState.RECEIVING),
        ),
      )
      panel.add(legend)
      return panel
    }
  }
}
