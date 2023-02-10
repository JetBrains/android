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
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.legend.FixedLegend
import com.android.tools.adtui.model.legend.LegendComponentModel
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData.Companion.getUrlName
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.NO_STATUS_CODE
import com.android.tools.idea.appinspection.inspectors.network.view.ConnectionsStateChart
import com.android.tools.idea.appinspection.inspectors.network.view.NetworkState
import com.android.tools.inspectors.common.ui.dataviewer.ImageDataViewer
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
import java.awt.image.BufferedImage
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
 *
 * This tab will be the first one shown to the user when they first select a request.
 */
class OverviewTabContent : TabContent() {
  private lateinit var contentPanel: JPanel
  override val title = "Overview"

  override fun createComponent(): JComponent {
    val layout: TabularLayout = TabularLayout("*").setVGap(PAGE_VGAP)
    contentPanel = JPanel(layout)
    contentPanel.border = JBUI.Borders.empty(PAGE_VGAP, HORIZONTAL_PADDING, 0, HORIZONTAL_PADDING)
    val overviewScroll: JBScrollPane = createVerticalScrollPane(contentPanel)
    overviewScroll.verticalScrollBar.unitIncrement = SCROLL_UNIT
    overviewScroll.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        layout.setRowSizing(
          0,
          TabularLayout.SizingRule(TabularLayout.SizingRule.Type.FIXED, (overviewScroll.viewport.height * 0.4f).toInt())
        )
        layout.layoutContainer(contentPanel)
      }
    })
    return overviewScroll
  }

  override fun populateFor(data: HttpData?, httpDataComponentFactory: HttpDataComponentFactory) {
    contentPanel.removeAll()
    if (data == null) {
      return
    }
    val payloadViewer = httpDataComponentFactory.createDataViewer(HttpDataComponentFactory.ConnectionType.RESPONSE)
    val responsePayloadComponent: JComponent = payloadViewer.component
    responsePayloadComponent.name = ID_RESPONSE_PAYLOAD_VIEWER
    contentPanel.add(responsePayloadComponent, TabularLayout.Constraint(0, 0))
    val image = if (payloadViewer is ImageDataViewer) payloadViewer.image else null
    contentPanel.add(createFields(data, image), TabularLayout.Constraint(1, 0))
  }

  @VisibleForTesting
  fun findResponsePayloadViewer(): JComponent? {
    return findComponentWithUniqueName(contentPanel, ID_RESPONSE_PAYLOAD_VIEWER)
  }

  @VisibleForTesting
  fun findContentTypeValue(): JLabel? {
    return findComponentWithUniqueName(contentPanel, ID_CONTENT_TYPE) as JLabel?
  }

  @VisibleForTesting
  fun findSizeValue(): JLabel? {
    return findComponentWithUniqueName(contentPanel, ID_SIZE) as JLabel?
  }

  @VisibleForTesting
  fun findUrlValue(): JTextArea? {
    return findComponentWithUniqueName(contentPanel, ID_URL) as JTextArea?
  }

  @VisibleForTesting
  fun findTimingBar(): JComponent? {
    return findComponentWithUniqueName(contentPanel, ID_TIMING)
  }

  @VisibleForTesting
  fun findInitiatingThreadValue(): JLabel? {
    return findComponentWithUniqueName(contentPanel, ID_INITIATING_THREAD) as JLabel?
  }

  @VisibleForTesting
  fun findOtherThreadsValue(): JLabel? {
    return findComponentWithUniqueName(contentPanel, ID_OTHER_THREADS) as JLabel?
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
          cursor = if (isMouseOverText(e)) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
        }

        private fun isMouseOverText(e: MouseEvent): Boolean {
          return viewToModel2D(e.point) < document.length
        }
      }
    }
  }

  companion object {
    private val TIME_FORMATTER: LongFunction<String> = LongFunction<String> { time: Long ->
      if (time >= 0) StringUtil.formatDuration(
        TimeUnit.MICROSECONDS.toMillis(time)
      )
      else "*"
    }
    private const val ID_CONTENT_TYPE = "CONTENT_TYPE"
    private const val ID_SIZE = "SIZE"
    private const val ID_URL = "URL"
    private const val ID_TIMING = "TIMING"
    private const val ID_INITIATING_THREAD = "INITIATING_THREAD"
    private const val ID_OTHER_THREADS = "OTHER_THREADS"
    private const val ID_RESPONSE_PAYLOAD_VIEWER = "RESPONSE_PAYLOAD_VIEWER"

    private fun createFields(httpData: HttpData, image: BufferedImage?): JComponent {
      val myFieldsPanel = JPanel(TabularLayout("Fit-,40px,*").setVGap(SECTION_VGAP))

      var row = 0
      myFieldsPanel.add(NoWrapBoldLabel("Request"), TabularLayout.Constraint(row, 0))
      myFieldsPanel.add(JLabel(getUrlName(httpData.url)), TabularLayout.Constraint(row, 2))
      row++
      myFieldsPanel.add(NoWrapBoldLabel("Method"), TabularLayout.Constraint(row, 0))
      myFieldsPanel.add(JLabel(httpData.method), TabularLayout.Constraint(row, 2))

      val responseHeader = httpData.responseHeader
      if (responseHeader.statusCode != NO_STATUS_CODE) {
        row++
        myFieldsPanel.add(NoWrapBoldLabel("Status"), TabularLayout.Constraint(row, 0))
        val statusCode = JLabel(java.lang.String.valueOf(responseHeader.statusCode))
        myFieldsPanel.add(statusCode, TabularLayout.Constraint(row, 2))
      }

      if (image != null) {
        row++
        myFieldsPanel.add(NoWrapBoldLabel("Dimension"), TabularLayout.Constraint(row, 0))
        val dimension = JLabel(String.format("%d x %d", image.width, image.height))
        myFieldsPanel.add(dimension, TabularLayout.Constraint(row, 2))
      }

      if (!responseHeader.contentType.isEmpty) {
        row++
        myFieldsPanel.add(NoWrapBoldLabel("Content type"), TabularLayout.Constraint(row, 0))
        val contentTypeLabel = JLabel(responseHeader.contentType.mimeType)
        contentTypeLabel.name = ID_CONTENT_TYPE
        myFieldsPanel.add(contentTypeLabel, TabularLayout.Constraint(row, 2))
      }

      val contentLength = responseHeader.contentLength
      if (contentLength != -1) {
        try {
          row++
          myFieldsPanel.add(NoWrapBoldLabel("Size"), TabularLayout.Constraint(row, 0))
          val contentLengthLabel = JLabel(StringUtil.formatFileSize(contentLength.toLong()))
          contentLengthLabel.name = ID_SIZE
          myFieldsPanel.add(contentLengthLabel, TabularLayout.Constraint(row, 2))
        }
        catch (ignored: NumberFormatException) {
        }
      }

      row++
      myFieldsPanel.add(NoWrapBoldLabel("Initiating thread"), TabularLayout.Constraint(row, 0))
      val initiatingThreadLabel = JLabel(httpData.javaThreads[0].name)
      initiatingThreadLabel.name = ID_INITIATING_THREAD
      myFieldsPanel.add(initiatingThreadLabel, TabularLayout.Constraint(row, 2))
      if (httpData.javaThreads.size > 1) {
        val otherThreadsBuilder = StringBuilder()
        for (i in 1 until httpData.javaThreads.size) {
          if (otherThreadsBuilder.isNotEmpty()) {
            otherThreadsBuilder.append(", ")
          }
          otherThreadsBuilder.append(httpData.javaThreads[i].name)
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
      val hyperlink = WrappedHyperlink(httpData.url)
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
      val timingBar: JComponent = createTimingBar(httpData)
      timingBar.name = ID_TIMING
      myFieldsPanel.add(timingBar, TabularLayout.Constraint(row, 2))
      return myFieldsPanel
    }

    private fun createTimingBar(httpData: HttpData): JComponent {
      val panel = JPanel()
      panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
      val range = Range(
        httpData.requestStartTimeUs.toDouble(),
        (if (httpData.connectionEndTimeUs > 0) httpData.connectionEndTimeUs else httpData.requestStartTimeUs + 1).toDouble()
      )
      val connectionsChart = ConnectionsStateChart(httpData, range)
      connectionsChart.component.minimumSize = Dimension(0, JBUI.scale(28))
      connectionsChart.setHeightGap(0f)
      panel.add(connectionsChart.component)
      var sentTime: Long = -1
      var receivedTime: Long = -1
      if (httpData.responseStartTimeUs > 0) {
        sentTime = httpData.responseStartTimeUs - httpData.requestStartTimeUs
        receivedTime = httpData.responseCompleteTimeUs - httpData.responseStartTimeUs
      }
      else if (httpData.connectionEndTimeUs > 0) {
        sentTime = httpData.connectionEndTimeUs - httpData.requestStartTimeUs
        receivedTime = 0
      }
      val sentLegend = FixedLegend("Sent", TIME_FORMATTER.apply(sentTime))
      val receivedLegend = FixedLegend("Received", TIME_FORMATTER.apply(receivedTime))
      val legendModel = LegendComponentModel()
      legendModel.add(sentLegend)
      legendModel.add(receivedLegend)

      // TODO: Add waiting time in (currently hidden because it's always 0)
      val legend: LegendComponent = LegendComponent.Builder(legendModel).setLeftPadding(0).setVerticalPadding(JBUI.scale(8)).build()
      legend.configure(
        sentLegend,
        LegendConfig(LegendConfig.IconType.BOX, connectionsChart.colors.getColor(NetworkState.SENDING))
      )
      legend.configure(
        receivedLegend,
        LegendConfig(LegendConfig.IconType.BOX, connectionsChart.colors.getColor(NetworkState.RECEIVING))
      )
      panel.add(legend)
      return panel
    }
  }
}