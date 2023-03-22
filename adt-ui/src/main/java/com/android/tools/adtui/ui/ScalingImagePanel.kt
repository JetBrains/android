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
package com.android.tools.adtui.ui

import com.android.annotations.concurrency.UiThread
import com.android.tools.adtui.model.Stopwatch
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.concurrency.catching
import com.android.tools.idea.concurrency.transform
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.components.JBPanel
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.JBHiDPIScaledImage
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.EdtExecutorService
import java.awt.Graphics
import java.awt.Image
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * A [JBPanel] that shows an [Image] as background, scaled to fit preserving the aspect ratio.
 * Allows the [Image] to be regenerated through a [ScaledImageProvider] when the panel size
 * changes. Supports HiDPI if [image] is a [JBHiDPIScaledImage].
 */
@UiThread
open class ScalingImagePanel : JBPanel<ImagePanel>(true), Disposable {
  private val LOG = logger<ScalingImagePanel>()
  private val stopwatch = Stopwatch()
  private val edtExecutor = FutureCallbackExecutor(EdtExecutorService.getInstance())
  private val boundedExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("ScalingImagePanel", 1)
  private val taskExecutor = FutureCallbackExecutor(boundedExecutor)
  private val resizedListener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent?) {
      super.componentResized(e)
      repaintAsync()
    }
  }
  private var previousFuture: Future<Unit>? = null

  /**
   * The [Image] to draw
   */
  var image: Image? = null
    set(value) {
      field = value
      repaint()
    }

  /**
   * Where the image should be "grayed out" (as inactive) or not
   */
  var active: Boolean = true
    set(value) {
      field = value
      repaint()
    }

  /**
   * The [ScaledImageProvider] to recompute the [image] when the component size changes
   */
  var scaledImageProvider: ScaledImageProvider? = null
    set(value) {
      field = value
      if (value == null) {
        removeComponentListener(resizedListener)
        repaint()
      }
      else {
        addComponentListener(resizedListener)
        image = value.initialImage
        repaintAsync()
      }
    }

  override fun dispose() {
    removeComponentListener(resizedListener)
    previousFuture?.cancel(false)
    boundedExecutor.shutdownNow()
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    paintPanelImage(g, image, active, false)
  }

  private fun repaintAsync() {
    scaledImageProvider?.let { provider ->
      val width = this.width.toDouble()
      val height = this.height.toDouble()
      val ctx = ScaleContext.create(this)
      val newFuture = taskExecutor.executeAsync {
        // Empty body so that potential future cancellation affect the actual
        // heavy processing call (in the transform below)
      }.transform(taskExecutor) {
        // Note: This block will never execute if there are multiple repaintAsync
        // request (see future cancellation below)
        stopwatch.start()
        val result = provider.createScaledImage(ctx, width, height)
        stopwatch.stop()
        LOG.debug("createScaleImage(scaleContext=${ctx}, w=${width}, h=${height}) is done " +
                  "in ${TimeUnit.NANOSECONDS.toMillis(stopwatch.totalRunningTimeNs)} msec")
        result
      }.transform(edtExecutor) { newImage ->
        // Update image on EDT thread (this will repaint)
        LOG.debug("Updating panel image (scaleContext=${ctx}, userWidth=${userWidth(newImage)}, pixelWidth=${pixelWidth(newImage)})")
        if (provider == scaledImageProvider) {
          image = newImage
        }
      }.catching(edtExecutor, Exception::class.java) { e ->
        LOG.warn("Error loading scaled image", e)
      }

      // Cancel previous block of work so that we don't waste CPU cycles scaling
      // an image that will never be displayed (we only care about the last one)
      previousFuture?.cancel(false)
      previousFuture = newFuture
    }
  }

  private fun userWidth(img: Image): Int {
    if (img is JBHiDPIScaledImage) {
      return img.getUserWidth(null)
    }
    else {
      return img.getWidth(null)
    }
  }

  private fun pixelWidth(img: Image): Int {
    if (img is JBHiDPIScaledImage) {
      return img.getRealWidth()
    }
    else {
      return img.getWidth(null)
    }
  }
}
