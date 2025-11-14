/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.ui

import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.adtui.Zoomable
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.idea.layoutinspector.ui.toolbar.NewFloatingToolbarProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Point
import javax.swing.JLayeredPane
import javax.swing.JPanel

private const val MARGIN = 50

private const val FLOATING_TOOLBAR_MARGIN = 14

/** See [ZoomType.ACTUAL] */
private const val ACTUAL_ZOOM_PERCENT = 100
private const val MAX_ZOOM_PERCENT = 100
private const val MIN_ZOOM_PERCENT = 10
private const val ZOOM_STEP_PERCENT = 10

/** A container panel that adds zoom controls to the [contentPanel] */
class ZoomableContainer(
  private val disposable: Disposable,
  private val contentPanel: JPanel,
  private val getZoomPercent: () -> Int,
  private val setZoomPercent: (Int) -> Unit,
) : BorderLayoutPanel(), Zoomable, UiDataProvider {

  override val scale
    get() = getZoomPercent() / 100.0

  override val screenScalingFactor = 1.0

  private val scrollPane = JBScrollPane(contentPanel)
  private val layeredPane = JLayeredPane()
  private val floatingToolbarProvider =
    NewFloatingToolbarProvider(disposable = disposable, component = this)

  init {
    scrollPane.border = JBUI.Borders.empty()
    contentPanel.border = JBUI.Borders.empty(MARGIN)

    layeredPane.setLayer(scrollPane, JLayeredPane.DEFAULT_LAYER)
    layeredPane.setLayer(floatingToolbarProvider.floatingToolbar, JLayeredPane.PALETTE_LAYER)
    layeredPane.layout = LayeredPaneLayout()

    layeredPane.add(floatingToolbarProvider.floatingToolbar)
    layeredPane.add(scrollPane, BorderLayout.CENTER)

    addToCenter(layeredPane)
  }

  override fun zoom(type: ZoomType): Boolean {
    val oldZoom = getZoomPercent()
    val newZoomRaw =
      when (type) {
        ZoomType.FIT -> getZoomToFit()
        ZoomType.ACTUAL -> ACTUAL_ZOOM_PERCENT
        ZoomType.IN -> oldZoom + ZOOM_STEP_PERCENT
        ZoomType.OUT -> oldZoom - ZOOM_STEP_PERCENT
      }
    val newZoom = newZoomRaw.coerceIn(MIN_ZOOM_PERCENT, MAX_ZOOM_PERCENT)
    return if (newZoom != oldZoom) {
      floatingToolbarProvider.zoomChanged()
      setZoomPercent(newZoom)
      contentPanel.revalidate()
      true
    } else {
      false
    }
  }

  override fun canZoomIn() = getZoomPercent() < MAX_ZOOM_PERCENT

  override fun canZoomOut() = getZoomPercent() > MIN_ZOOM_PERCENT

  override fun canZoomToFit() = getZoomPercent() != getZoomToFit()

  override fun canZoomToActual() = getZoomPercent() != ACTUAL_ZOOM_PERCENT

  /**
   * Return the zoom to fit percentage, this is the amount of zoom required so that [contentPanel]
   * entirely fits in the viewport and scrollbars are not needed
   */
  private fun getZoomToFit(): Int {
    val viewport = scrollPane.viewport

    val horizontalMargin = JBUI.scale(MARGIN) * 2
    val verticalMargin = JBUI.scale(MARGIN) * 2

    // Calculate available space for the ACTUAL content (Viewport - Margins)
    val availableWidth = viewport.width - horizontalMargin
    val availableHeight = viewport.height - verticalMargin

    if (availableWidth <= 0 || availableHeight <= 0) {
      return ACTUAL_ZOOM_PERCENT
    }

    val currentPreferredSize = contentPanel.preferredSize

    // We subtract the known margin to get the pure scaled content size
    val currentScaledWidth = currentPreferredSize.width - horizontalMargin
    val currentScaledHeight = currentPreferredSize.height - verticalMargin

    if (currentScaledWidth <= 0 || currentScaledHeight <= 0) {
      return ACTUAL_ZOOM_PERCENT
    }

    // Divide by current scale to get original unscaled dimensions
    val unscaledWidth = currentScaledWidth / scale
    val unscaledHeight = currentScaledHeight / scale

    val scaleX = availableWidth / unscaledWidth
    val scaleY = availableHeight / unscaledHeight

    val fitScale = minOf(scaleX, scaleY)

    // Calculate the exact raw percentage (e.g. 112)
    val rawZoom = (fitScale * 100).toInt()

    // Round to the nearest increment of ZOOM_STEP_PERCENT. To make sure that the zoom value is
    // always one of the zoom steps.
    return ((rawZoom + ZOOM_STEP_PERCENT / 2) / ZOOM_STEP_PERCENT) * ZOOM_STEP_PERCENT
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[ZOOMABLE_KEY] = this
  }

  /** The layout used for the [layeredPane] */
  private inner class LayeredPaneLayout : BorderLayout() {
    override fun layoutContainer(target: Container?) {
      super.layoutContainer(target)

      scrollPane.size = layeredPane.size
      positionToolbar()
    }

    /** Position the floating toolbar with some margin to the panel bottom right corner. */
    private fun positionToolbar() {
      val floatingToolbar = floatingToolbarProvider.floatingToolbar
      floatingToolbar.size = floatingToolbar.preferredSize
      val x = layeredPane.width - floatingToolbar.width - JBUI.scale(FLOATING_TOOLBAR_MARGIN)
      val y = layeredPane.height - floatingToolbar.height - JBUI.scale(FLOATING_TOOLBAR_MARGIN)
      floatingToolbar.location = Point(x, y)
    }
  }
}
