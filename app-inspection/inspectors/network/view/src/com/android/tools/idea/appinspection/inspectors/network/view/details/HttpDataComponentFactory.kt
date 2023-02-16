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

import android.annotation.SuppressLint
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.event.NestedScrollPaneMouseWheelListener
import com.android.tools.adtui.stdui.ContentType
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData
import com.android.tools.idea.appinspection.inspectors.network.view.UiComponentsProvider
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.inspectors.common.ui.dataviewer.DataViewer
import com.google.common.annotations.VisibleForTesting
import com.intellij.util.ui.JBUI
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager2
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Locale
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.border.Border
import kotlin.math.max
import kotlin.math.min

/**
 * A factory which wraps a target [HttpData] and can create useful, shared UI components for
 * displaying aspects of it.
 */
@SuppressLint("AvoidByLazy")
class HttpDataComponentFactory(
  private val httpData: HttpData,
  private val componentsProvider: UiComponentsProvider
) {

  private val requestRawComponent by lazy {
    createRawDataComponent(
      getPayload(ConnectionType.REQUEST),
      getContentType(ConnectionType.REQUEST),
      componentsProvider
    )
  }
  private val requestPrettyComponent by lazy {
    createParsedDataComponent(
      getPayload(ConnectionType.REQUEST),
      getContentType(ConnectionType.REQUEST),
      componentsProvider
    )
  }
  private val responseRawComponent by lazy {
    createRawDataComponent(
      getPayload(ConnectionType.RESPONSE),
      getContentType(ConnectionType.RESPONSE),
      componentsProvider
    )
  }
  private val responsePrettyComponent by lazy {
    createParsedDataComponent(
      getPayload(ConnectionType.RESPONSE),
      getContentType(ConnectionType.RESPONSE),
      componentsProvider
    )
  }

  private fun getRawDataComponent(type: ConnectionType) =
    when (type) {
      ConnectionType.REQUEST -> requestRawComponent
      ConnectionType.RESPONSE -> responseRawComponent
    }

  private fun getPrettyComponent(type: ConnectionType) =
    when (type) {
      ConnectionType.REQUEST -> requestPrettyComponent
      ConnectionType.RESPONSE -> responsePrettyComponent
    }

  private fun getContentType(type: ConnectionType) =
    when (type) {
      ConnectionType.REQUEST -> httpData.requestHeader.contentType
      ConnectionType.RESPONSE -> httpData.responseHeader.contentType
    }

  private fun getPayload(type: ConnectionType) =
    when (type) {
      ConnectionType.REQUEST -> httpData.requestPayload
      ConnectionType.RESPONSE -> httpData.responsePayload
    }

  private fun getHeader(type: ConnectionType) =
    when (type) {
      ConnectionType.REQUEST -> httpData.requestHeader
      ConnectionType.RESPONSE -> httpData.responseHeader
    }

  private fun getMimeTypeString(type: ConnectionType) =
    when (type) {
      ConnectionType.REQUEST -> getHeader(type).contentType.mimeType
      ConnectionType.RESPONSE -> getHeader(type).contentType.mimeType
    }

  /**
   * Creates a component which displays the current [HttpData]'s headers as a list of key/value
   * pairs.
   */
  fun createHeaderComponent(type: ConnectionType): JComponent {
    return createStyledMapComponent(getHeader(type).fields)
  }

  /**
   * Returns a title which should be shown above the body component created by
   * [.createBodyComponent].
   */
  private fun getBodyTitle(type: ConnectionType): String {
    val header = getHeader(type)
    val contentType = header.contentType
    return if (contentType.isEmpty) {
      "Body"
    } else "Body ( ${getDisplayName(contentType)} )"
  }

  /**
   * Returns a payload component which can display the underlying data of the current [HttpData]'s
   * payload. If the payload is empty, this will return a label to indicate that the target payload
   * is not set. If the payload is not empty and is supported for parsing, this will return a
   * component containing both the raw data view and the parsed view.
   */
  fun createBodyComponent(type: ConnectionType): JComponent {
    val payload = getPayload(type)
    if (payload.isEmpty) {
      return createHideablePanel(getBodyTitle(type), JLabel("Not available"), null)
    }
    val rawDataComponent = getRawDataComponent(type)
    var bodyComponent = rawDataComponent
    var northEastComponent: JComponent? = null
    getPrettyComponent(type)?.let { parsedDataComponent ->
      val cardLayout = CardLayout()
      val payloadPanel = JPanel(cardLayout)
      val cardViewParsed = "View Parsed"
      val cardViewSource = "View Source"
      parsedDataComponent.name = cardViewParsed
      rawDataComponent.name = cardViewSource
      payloadPanel.add(parsedDataComponent, cardViewParsed)
      payloadPanel.add(rawDataComponent, cardViewSource)
      bodyComponent = payloadPanel
      val toggleLabel = JLabel(cardViewSource)
      northEastComponent = toggleLabel
      val toggleHoverColor =
        AdtUiUtils.overlayColor(toggleLabel.background.rgb, toggleLabel.foreground.rgb, 0.9f)
      val toggleDefaultColor: Color =
        AdtUiUtils.overlayColor(toggleLabel.background.rgb, toggleHoverColor.rgb, 0.6f)
      toggleLabel.foreground = toggleDefaultColor
      toggleLabel.border = JBUI.Borders.empty(0, 10, 0, 5)
      toggleLabel.addMouseListener(
        object : MouseAdapter() {
          override fun mouseClicked(e: MouseEvent) {
            toggleLabel.text =
              if (cardViewSource == toggleLabel.text) cardViewParsed else cardViewSource
            cardLayout.next(payloadPanel)
          }

          override fun mouseEntered(e: MouseEvent) {
            toggleLabel.foreground = toggleHoverColor
          }

          override fun mouseExited(e: MouseEvent) {
            toggleLabel.foreground = toggleDefaultColor
          }
        }
      )
    }
    bodyComponent.name = type.bodyComponentId
    return createHideablePanel(getBodyTitle(type), bodyComponent, northEastComponent)
  }

  fun createDataViewer(type: ConnectionType, formatted: Boolean): DataViewer {
    return componentsProvider.createDataViewer(
      getPayload(type).toByteArray(),
      ContentType.fromMimeType(getMimeTypeString(type)),
      DataViewer.Style.PRETTY,
      formatted
    )
  }

  enum class ConnectionType {
    REQUEST,
    RESPONSE;

    val bodyComponentId: String
      get() = if (this == REQUEST) "REQUEST_PAYLOAD_COMPONENT" else "RESPONSE_PAYLOAD_COMPONENT"
  }

  @VisibleForTesting
  class CompressedVerticalLayout : LayoutManager2 {
    private val minSize = Dimension(INVALID_SIZE, INVALID_SIZE)
    private val preferredSize = Dimension(INVALID_SIZE, INVALID_SIZE)
    private val maxSize = Dimension(INVALID_SIZE, INVALID_SIZE)
    private var needsLayout = true

    override fun minimumLayoutSize(parent: Container): Dimension {
      // This layout manager doesn't respect minimum size.
      return getSize(parent, { c -> c.minimumSize }, minSize)
    }

    override fun preferredLayoutSize(parent: Container): Dimension {
      val parentWidth = parent.width
      return getSize(
        parent,
        { c: Component ->
          // Always resize prior to getting the preferred size, since we allow our components to be
          // as large as possible.
          c.setBounds(
            0,
            0,
            parentWidth,
            Short.MAX_VALUE.toInt()
          ) // Short.MAX_VALUE since that's what Swing uses (not Integer.MAX_VALUE).
          c.preferredSize
        },
        preferredSize
      )
    }

    override fun maximumLayoutSize(parent: Container): Dimension {
      return getSize(parent, { c: Component -> c.maximumSize }, maxSize)
    }

    override fun layoutContainer(parent: Container) {
      if (!needsLayout) {
        return
      }
      val componentCount = parent.componentCount
      val totalWidth = parent.width
      val totalHeight = parent.height
      for (i in 0 until componentCount) {
        val c = parent.getComponent(i)
        if (c.isMaximumSizeSet) {
          val maxDim = c.maximumSize
          val width = min(totalWidth, maxDim.width)
          c.setBounds(0, 0, width, maxDim.height)
        }
        val preferredDim = c.preferredSize
        val width = min(totalWidth, preferredDim.width)
        val height = min(totalHeight, preferredDim.height)
        c.setBounds(0, 0, width, height)
      }
      needsLayout = false
    }

    override fun getLayoutAlignmentX(target: Container): Float {
      return 0f
    }

    override fun getLayoutAlignmentY(target: Container): Float {
      return 0.0f
    }

    override fun invalidateLayout(target: Container) {
      invalidateLayout()
    }

    override fun addLayoutComponent(comp: Component, constraints: Any?) {
      invalidateLayout()
    }

    override fun addLayoutComponent(name: String, comp: Component) {
      invalidateLayout()
    }

    override fun removeLayoutComponent(comp: Component) {
      invalidateLayout()
    }

    private fun invalidateLayout() {
      minSize.setSize(INVALID_SIZE, INVALID_SIZE)
      preferredSize.setSize(INVALID_SIZE, INVALID_SIZE)
      maxSize.setSize(INVALID_SIZE, INVALID_SIZE)
      needsLayout = true
    }

    companion object {
      private const val INVALID_SIZE = -1
      private fun getSize(
        parent: Container,
        componentSizeGetter: Function<Component, Dimension>,
        resultDimension: Dimension
      ): Dimension {
        if (resultDimension.width != INVALID_SIZE && resultDimension.height != INVALID_SIZE) {
          return resultDimension
        }
        var w = 0
        var h = 0
        for (c in parent.components) {
          val d = componentSizeGetter.apply(c)
          w = max(w, d.width)
          h += d.height
        }
        resultDimension.setSize(w, h)
        return resultDimension
      }
    }
  }

  companion object {
    private const val ID_PAYLOAD_VIEWER = "PAYLOAD_VIEWER"
    private val PAYLOAD_BORDER: Border = JBUI.Borders.emptyTop(6)

    /**
     * Search for the payload [DataViewer] inside a component returned by [.createBodyComponent]. If
     * this returns `null`, that means no payload viewer was created for it, e.g. the http data
     * instance didn't have a payload and a "No data found" label was returned instead.
     */
    @VisibleForTesting
    fun findPayloadViewer(body: JComponent?): JComponent? {
      return if (body == null) {
        null
      } else findComponentWithUniqueName(body, ID_PAYLOAD_VIEWER)
    }

    /**
     * Creates the raw data view of given payload.
     *
     * Assumes the payload is not empty.
     */
    private fun createRawDataComponent(
      payload: ByteString,
      contentType: HttpData.ContentType,
      componentsProvider: UiComponentsProvider
    ): JComponent {
      val contentTypeFromMime = ContentType.fromMimeType(contentType.mimeType)
      val viewer =
        componentsProvider.createDataViewer(
          payload.toByteArray(),
          contentTypeFromMime,
          DataViewer.Style.RAW,
          false
        )
      val viewerComponent = viewer.component
      viewerComponent.name = ID_PAYLOAD_VIEWER
      viewerComponent.border = PAYLOAD_BORDER
      val compressedVerticalPanel = JPanel(CompressedVerticalLayout())
      compressedVerticalPanel.add(viewerComponent)
      return compressedVerticalPanel
    }

    /**
     * Creates the parsed data view of given payload, or returns null if the payload is not
     * applicable for parsing.
     *
     * Assumes the payload is not empty.
     */
    private fun createParsedDataComponent(
      payload: ByteString,
      contentType: HttpData.ContentType,
      componentsProvider: UiComponentsProvider
    ): JComponent? {
      if (contentType.isFormData) {
        val contentToParse = payload.toStringUtf8()
        val parsedContent =
          contentToParse
            .trim { it <= ' ' }
            .split('&')
            .associate { s ->
              val splits = s.split('=', limit = 2)
              if (splits.size > 1) {
                splits[0] to splits[1]
              } else {
                splits[0] to ""
              }
            }
        return createStyledMapComponent(parsedContent)
      }
      val contentTypeFromMime = ContentType.fromMimeType(contentType.mimeType)
      val viewer: DataViewer =
        componentsProvider.createDataViewer(
          payload.toByteArray(),
          contentTypeFromMime,
          DataViewer.Style.PRETTY,
          true
        )

      // Just because we request a "pretty" viewer doesn't mean we'll actually get one. If we
      // didn't,
      // that means formatting support is not provided, so return null as a way to indicate this
      // failure to the code that called us.
      if (viewer.style == DataViewer.Style.PRETTY) {
        val viewerComponent = viewer.component
        viewerComponent.border = PAYLOAD_BORDER

        // Slight hack: Currently, the viewer component embeds a scroll pane, which we want to
        // disable here. A straightforward way to do this is to iterate through all scroll panes
        // (there should only ever be one, but this code should still be harmless even if in the
        // future there are 0 or several).
        TreeWalker(viewerComponent).descendants().filterIsInstance<JScrollPane>().forEach {
          scroller: JScrollPane ->
          NestedScrollPaneMouseWheelListener.installOn(scroller)
        }
        return viewerComponent
      }
      return null
    }

    /**
     * Returns a user visible display name that represents the target `contentType`, with the first
     * letter capitalized.
     */
    @VisibleForTesting
    fun getDisplayName(contentType: HttpData.ContentType): String {
      val mimeType = contentType.mimeType.trim { it <= ' ' }
      if (mimeType.isEmpty()) {
        return mimeType
      }
      if (contentType.isFormData) {
        return "Form Data"
      }
      val typeAndSubType = mimeType.split('/', limit = 2)
      val showSubType =
        typeAndSubType.size > 1 &&
          (typeAndSubType[0] == "text" || typeAndSubType[0] == "application")
      val name = if (showSubType) typeAndSubType[1] else typeAndSubType[0]
      return if (name.isEmpty() || showSubType) {
        name.toUpperCase(Locale.getDefault())
      } else name.substring(0, 1).toUpperCase(Locale.getDefault()) + name.substring(1)
    }
  }
}
