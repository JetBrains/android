/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.avdmanager

import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenSize
import com.android.sdklib.devices.Device
import com.android.tools.idea.deviceManager.avdmanager.AvdScreenData.Companion.getScreenDensity
import com.android.tools.idea.deviceManager.avdmanager.AvdScreenData.Companion.getScreenRatio
import com.android.tools.idea.observable.InvalidationListener
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BasicStroke
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.Stroke
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.RoundRectangle2D
import java.text.DecimalFormat
import javax.swing.Icon
import javax.swing.JPanel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A preview component for displaying information about a device definition.
 * This panel displays the dimensions of the device (both physical and in pixels) and some information about the screen size and shape.
 */
class DeviceDefinitionPreview(val deviceData: AvdDeviceData) : JPanel(), DeviceDefinitionList.DeviceCategorySelectionListener {
  var maxOutlineWidth = 0.0
  var minOutlineWidthIn = 0.0
  private val repaintListener = InvalidationListener { repaint() }

  private fun addListeners() {
    deviceData.apply {
      supportsLandscape().addWeakListener(repaintListener)
      supportsPortrait().addWeakListener(repaintListener)
      name().addWeakListener(repaintListener)
      screenResolutionWidth().addWeakListener(repaintListener)
      screenResolutionHeight().addWeakListener(repaintListener)
      deviceType().addWeakListener(repaintListener)
      diagonalScreenSize().addWeakListener(repaintListener)
      isScreenRound.addWeakListener(repaintListener)
      screenDpi().addWeakListener(repaintListener)
    }
  }

  init {
    addListeners()
  }

  override fun paintComponent(g: Graphics) {
    GraphicsUtil.setupAntialiasing(g)
    GraphicsUtil.setupAAPainting(g)
    super.paintComponent(g)
    val g2d = g as Graphics2D
    g2d.color = JBColor.background()
    g2d.fillRect(0, 0, width, height)
    g2d.color = JBColor.foreground()
    g2d.font = AvdWizardUtils.STANDARD_FONT
    if (deviceData.name().get() == DO_NOT_DISPLAY) {
      val metrics = g2d.fontMetrics
      g2d.drawString(NO_DEVICE_SELECTED, (width - metrics.stringWidth(NO_DEVICE_SELECTED)) / 2, (height - metrics.height) / 2)
      return
    }
    val isCircular = deviceData.isWear.get() && deviceData.isScreenRound.get()

    // TODO(qumeric): refactor to more local function like this
    fun paintDeviceName() {
      val metrics = g2d.getFontMetrics(AvdWizardUtils.TITLE_FONT)
      g2d.font = AvdWizardUtils.TITLE_FONT
      g2d.drawString(deviceData.name().get(), JBUI.scale(50), JBUI.scale(PADDING) + metrics.height / 2)
      g2d.drawLine(0, JBUI.scale(50), width, JBUI.scale(50))
    }

    getIcon(deviceData).paintIcon(this, g, JBUI.scale(PADDING) / 2, JBUI.scale(PADDING) / 2)

    paintDeviceName()

    // Paint the device outline with dimensions labelled
    val screenSize = scaledDimension
    val pixelScreenSize = deviceData.deviceScreenDimension
    if (screenSize.getHeight() <= 0) {
      screenSize.height = 1
    }
    if (screenSize.getWidth() <= 0) {
      screenSize.width = 1
    }
    val roundRect: RoundRectangle2D = RoundRectangle2D.Double(JBUI.scale(PADDING).toDouble(), JBUI.scale(100).toDouble(), screenSize.width.toDouble(),
                                                              screenSize.height.toDouble(), JBUI.scale(10).toDouble(),
                                                              JBUI.scale(10).toDouble())
    val normalStroke: Stroke = BasicStroke(JBUI.scale(DIMENSION_LINE_WIDTH).toFloat())
    g2d.stroke = normalStroke
    g2d.color = OUR_GRAY
    g2d.font = AvdWizardUtils.FIGURE_FONT
    var metrics = g2d.getFontMetrics(AvdWizardUtils.FIGURE_FONT)
    var stringHeight = metrics.height - metrics.descent
    if (deviceData.isFoldable.get()) {
      // Show the boundary of the folded region using dashed lines
      // Get the location and size of the preview of the folded region
      val displayFactor = screenSize.height / deviceData.screenResolutionHeight().get().toDouble()
      var foldedX = (deviceData.screenFoldedXOffset().get() * displayFactor + 0.5).toInt()
      var foldedY = (deviceData.screenFoldedYOffset().get() * displayFactor + 0.5).toInt()
      val foldedWidth = (deviceData.screenFoldedWidth().get() * displayFactor + 0.5).toInt()
      val foldedHeight = (deviceData.screenFoldedHeight().get() * displayFactor + 0.5).toInt()
      foldedX += JBUI.scale(PADDING)
      foldedY += JBUI.scale(100)
      g2d.stroke = BasicStroke(JBUI.scale(OUTLINE_LINE_WIDTH).toFloat(), BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, floatArrayOf(9f), 0f)
      // Show a side of the folded region if it does not coincide with the
      // corresponding side of the full region
      if (deviceData.screenFoldedXOffset().get() != 0) {
        // Show the left boundary
        g2d.drawLine(foldedX, foldedY, foldedX, foldedY + foldedHeight)
      }
      if (deviceData.screenFoldedYOffset().get() != 0) {
        // Show the top boundary
        g2d.drawLine(foldedX, foldedY, foldedX + foldedWidth, foldedY)
      }
      if (deviceData.screenFoldedXOffset().get() + deviceData.screenFoldedWidth().get()
        != deviceData.screenResolutionWidth().get()) {
        // Show the right boundary
        g2d.drawLine(foldedX + foldedWidth, foldedY, foldedX + foldedWidth, foldedY + foldedHeight)
      }
      if (deviceData.screenFoldedYOffset().get() + deviceData.screenFoldedHeight().get()
        != deviceData.screenResolutionHeight().get()) {
        // Show the bottom boundary
        g2d.drawLine(foldedX, foldedY + foldedHeight, foldedX + foldedWidth, foldedY + foldedHeight)
      }
      g2d.stroke = normalStroke
    }

    // Paint the width dimension
    val widthString = pixelScreenSize.width.toString() + "px"
    val widthLineY = JBUI.scale(95) - (metrics.height - metrics.descent) / 2
    g2d.drawLine(JBUI.scale(PADDING), widthLineY, round(JBUI.scale(PADDING) + screenSize.width.toDouble()), widthLineY)

    // Erase the part of the line that the text overlays
    g2d.color = JBColor.background()
    val widthStringWidth = metrics.stringWidth(widthString)
    val widthTextX = round(JBUI.scale(PADDING) + (screenSize.width - widthStringWidth) / 2.0)
    g2d.drawLine(widthTextX - JBUI.scale(FIGURE_PADDING), widthLineY, widthTextX + widthStringWidth + JBUI.scale(FIGURE_PADDING), widthLineY)


    // Paint the width text
    g2d.color = JBColor.foreground()
    g2d.drawString(widthString, widthTextX, JBUI.scale(95))

    // Paint the height dimension
    g2d.color = OUR_GRAY
    val heightString = pixelScreenSize.height.toString() + "px"
    val heightLineX = round(JBUI.scale(PADDING) + screenSize.width + JBUI.scale(15).toDouble())
    g2d.drawLine(heightLineX, JBUI.scale(100), heightLineX, round(JBUI.scale(100) + screenSize.height.toDouble()))

    // Erase the part of the line that the text overlays
    g2d.color = JBColor.background()
    val heightTextY = round(JBUI.scale(100) + (screenSize.height + stringHeight) / 2.0)
    g2d.drawLine(heightLineX, heightTextY + JBUI.scale(FIGURE_PADDING), heightLineX, heightTextY - stringHeight - JBUI.scale(FIGURE_PADDING))

    // Paint the height text
    g2d.color = JBColor.foreground()
    g2d.drawString(heightString, heightLineX - JBUI.scale(10), heightTextY)

    // Paint the diagonal dimension
    g2d.color = OUR_GRAY
    val diagString = FORMAT.format(deviceData.diagonalScreenSize().get())
    val diagTextX = round(JBUI.scale(PADDING) + (screenSize.width - metrics.stringWidth(diagString)) / 2.0)
    val diagTextY = round(JBUI.scale(100) + (screenSize.height + stringHeight) / 2.0)
    val chin = deviceData.screenChinSize().get().toDouble() * (screenSize.getWidth() / deviceData.deviceScreenDimension.getWidth())
    val diagLine: Line2D = Line2D.Double(
      JBUI.scale(PADDING).toDouble(),
      JBUI.scale(100) + screenSize.height + chin,
      (JBUI.scale(PADDING) + screenSize.width).toDouble(),
      JBUI.scale(100).toDouble()
    )
    if (isCircular) {
      // Move the endpoints of the line to within the circle. Each endpoint must move towards the center axis of the circle by
      // 0.5 * (l - l/sqrt(2)) where l is the diameter of the circle.
      val dist = 0.5 * (screenSize.width - screenSize.width / sqrt(2.0))
      diagLine.setLine(diagLine.x1 + dist, diagLine.y1 - dist, diagLine.x2 - dist, diagLine.y2 + dist)
    }
    g2d.draw(diagLine)

    // Erase the part of the line that the text overlays
    g2d.color = JBColor.background()
    var erasureRect = Rectangle(
      diagTextX - JBUI.scale(FIGURE_PADDING),
      diagTextY - stringHeight - JBUI.scale(FIGURE_PADDING),
      metrics.stringWidth(diagString) + JBUI.scale(FIGURE_PADDING) * 2,
      stringHeight + JBUI.scale(FIGURE_PADDING) * 2
    )
    g2d.fill(erasureRect)

    // Paint the diagonal text
    g2d.color = JBColor.foreground()
    g2d.drawString(diagString, diagTextX, diagTextY)

    // Finally, paint the outline
    g2d.stroke = BasicStroke(JBUI.scale(OUTLINE_LINE_WIDTH).toFloat())
    g2d.color = JBColor.foreground()
    if (isCircular) {
      val x = roundRect.x
      val y = roundRect.y
      val circle: Ellipse2D = Ellipse2D.Double(x, y, screenSize.width.toDouble(), screenSize.height + chin)
      g2d.draw(circle)
      if (chin > 0) {
        erasureRect = Rectangle(
          x.toInt(),
          (y + screenSize.height + JBUI.scale(OUTLINE_LINE_WIDTH) / 2.0 + 1).toInt(),
          screenSize.width,
          chin.toInt() + JBUI.scale(OUTLINE_LINE_WIDTH) / 2 + 1
        )
        g2d.color = JBColor.background()
        g2d.fill(erasureRect)
        g2d.color = JBColor.foreground()
        val halfChinWidth = sqrt(chin * (screenSize.width - chin)) - JBUI.scale(OUTLINE_LINE_WIDTH) / 2.0
        val chinX = (x + screenSize.width / 2 - halfChinWidth).toInt()
        g2d.drawLine(chinX, (y + screenSize.height).toInt(), (chinX + halfChinWidth * 2).toInt(), (y + screenSize.height).toInt())
      }
    }
    else {
      g2d.draw(roundRect)
    }

    // Paint the details. If it's a portrait phone, then paint to the right of the rect.
    // If it's a landscape tablet/tv, paint below.
    g2d.font = AvdWizardUtils.STANDARD_FONT
    metrics = g2d.getFontMetrics(AvdWizardUtils.STANDARD_FONT)
    stringHeight = metrics.height
    val infoSegmentX: Int
    var infoSegmentY: Int
    if (deviceData.defaultDeviceOrientation == ScreenOrientation.PORTRAIT) {
      infoSegmentX = round(JBUI.scale(PADDING) + screenSize.width + metrics.stringWidth(heightString) + JBUI.scale(PADDING).toDouble())
      infoSegmentY = JBUI.scale(100)
    }
    else {
      infoSegmentX = JBUI.scale(PADDING)
      infoSegmentY = round(JBUI.scale(100) + screenSize.height + JBUI.scale(PADDING).toDouble())
    }
    infoSegmentY += stringHeight
    val size = ScreenSize.getScreenSize(deviceData.diagonalScreenSize().get())
    g2d.drawString("Size:      " + size.resourceValue, infoSegmentX, infoSegmentY)
    infoSegmentY += stringHeight
    val ratio = getScreenRatio(deviceData.screenResolutionWidth().get(), deviceData.screenResolutionHeight().get())
    g2d.drawString("Ratio:    " + ratio.resourceValue, infoSegmentX, infoSegmentY)
    infoSegmentY += stringHeight
    var pixelDensity = deviceData.density().get()
    if (pixelDensity == Density.NODPI) {
      // We need to calculate the density
      pixelDensity = getScreenDensity(
        deviceData.deviceId().get(),
        deviceData.isTv.get(),
        deviceData.screenDpi().get(),
        deviceData.screenResolutionHeight().get()
      )
    }
    g2d.drawString("Density: ${pixelDensity.resourceValue}", infoSegmentX, infoSegmentY)
    if (deviceData.isFoldable.get()) {
      infoSegmentY += stringHeight
      g2d.drawString("Folded: ${deviceData.screenFoldedWidth().get()}x${deviceData.screenFoldedHeight()}", infoSegmentX, infoSegmentY)
    }
  }
  /**
   * @return A scaled dimension of the given device's screen that will fit within this component's bounds.
   */
  private val scaledDimension: Dimension
    get() {
      val pixelSize = deviceData.deviceScreenDimension
      val diagonalIn = deviceData.diagonalScreenSize().get()
      val sideRatio = pixelSize.getWidth() / pixelSize.getHeight()
      val heightIn = diagonalIn / sqrt(1 + sideRatio * sideRatio)
      val widthIn = sideRatio * heightIn
      val maxWidthIn = if (maxOutlineWidth == 0.0) widthIn else maxOutlineWidth
      val desiredMaxWidthPx = width * 0.40
      val desiredMinWidthPx = width * 0.10

      // This is the scaled width we want to use.
      var widthPixels = widthIn * desiredMaxWidthPx / maxWidthIn

      // However a search result can contain both very small devices (wear) and very large devices (TV).
      // When this is the case use this alternate scaling algorithm to avoid the wear devices to show up as a dot.
      if (minOutlineWidthIn * desiredMaxWidthPx / maxWidthIn < desiredMinWidthPx) {
        widthPixels = desiredMinWidthPx + (widthIn - minOutlineWidthIn) * (desiredMaxWidthPx - desiredMinWidthPx) / (maxWidthIn - minOutlineWidthIn)
      }
      val heightPixels = widthPixels / widthIn * heightIn
      return Dimension(widthPixels.toInt(), heightPixels.toInt())
    }

  override fun onCategorySelectionChanged(category: String?, devices: List<Device>?) {
    if (devices == null) {
      maxOutlineWidth = 0.0
      minOutlineWidthIn = 0.0
      return
    }
    maxOutlineWidth = 0.0
    minOutlineWidthIn = Double.MAX_VALUE
    for (d in devices) {
      val pixelSize = d.getScreenSize(d.defaultState.orientation) ?: continue
      val diagonal = d.defaultHardware.screen.diagonalLength
      val sideRatio = pixelSize.getHeight() / pixelSize.getWidth()
      val widthIn = diagonal / sqrt(1 + sideRatio * sideRatio)
      maxOutlineWidth = max(maxOutlineWidth, widthIn)
      minOutlineWidthIn = min(minOutlineWidthIn, widthIn)
    }
  }

  companion object {
    /**
     * Constant string used to signal the panel not to preview a null device
     */
    const val DO_NOT_DISPLAY = "DO_NOT_DISPLAY"
    private const val FIGURE_PADDING = 3
    private val FORMAT = DecimalFormat(".##\"")
    const val DIMENSION_LINE_WIDTH = 1 // px
    const val OUTLINE_LINE_WIDTH = 5 // px
    private const val NO_DEVICE_SELECTED = "No Device Selected"
    private const val PADDING = 20
    private val OUR_GRAY = JBColor(Gray._192, Gray._96)

    /**
     * @return an icon representing the given device's form factor. Defaults to Mobile if the form factor can not be detected.
     */
    @JvmStatic
    fun getIcon(deviceData: AvdDeviceData?): Icon = when {
      deviceData == null -> StudioIcons.Avd.DEVICE_MOBILE_LARGE
      deviceData.isTv.get() -> StudioIcons.Avd.DEVICE_TV_LARGE
      deviceData.isWear.get() -> StudioIcons.Avd.DEVICE_WEAR_LARGE
      else -> StudioIcons.Avd.DEVICE_MOBILE_LARGE
    }

    private fun round(d: Double): Int = d.roundToInt()
  }
}