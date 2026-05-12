/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.whatsnew.assistant.v2.ui.composeutils

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.android.annotations.concurrency.WorkerThread
import com.intellij.util.io.HttpRequests
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.compose.resources.decodeToSvgPainter
import org.jetbrains.skia.Image

/**
 * An interface for loading images in the "What's New" panel.
 *
 * This loader uses a two-step mechanism to separate the synchronous UI rendering phase from the asynchronous image loading phase. This
 * prevents the UI thread from blocking while images are fetched from the network.
 */
internal interface ImagePainterLoader {

  /**
   * Retrieves a [Painter] if it has already been loaded and cached.
   *
   * This method is intended to be called synchronously from the UI thread (usually during a Compose recomposition) to check if an image is
   * immediately available without blocking.
   *
   * @param imageSource The URI of the image.
   * @param density The screen density used for scaling.
   * @return The cached [Painter] if available, or `null` if the image hasn't been loaded yet.
   */
  @UiThread fun getAlreadyLoadedPainter(imageSource: String, density: Density): Painter?

  /**
   * Loads a [Painter] from the given source, blocking until the operation is complete.
   *
   * This method performs the actual network I/O and image decoding. It is expected to be called from a background worker thread. Once
   * loaded, the resulting [Painter] should ideally be cached so that subsequent calls to [getAlreadyLoadedPainter] return the decoded image
   * instantly.
   *
   * @param imageSource The URI of the image.
   * @param density The screen density used for scaling.
   * @return The fully loaded and decoded [Painter].
   */
  @WorkerThread fun loadPainter(imageSource: String, density: Density): Painter
}

internal class DefaultImagePainterLoader @AnyThread constructor() : ImagePainterLoader {
  private val cache = ConcurrentHashMap<Pair<String, Density>, CacheEntry>()

  @UiThread
  override fun getAlreadyLoadedPainter(imageSource: String, density: Density): Painter? {
    return cache[Pair(imageSource, density)]?.alreadyLoadedPainter
  }

  @WorkerThread
  override fun loadPainter(imageSource: String, density: Density): Painter {
    val entry =
      cache.computeIfAbsent(Pair(imageSource, density)) {
        // We assume cache entry constructor is very lightweight
        CacheEntry(imageSource, density)
      }

    // Get painter of hashtable so that we can load painters concurrently is needed
    return entry.loadPainterIfNeeded()
  }

  private class CacheEntry(private val imageSource: String, private val density: Density) {
    private val painter = lazy(LazyThreadSafetyMode.SYNCHRONIZED) { createPainter() }

    @get:UiThread
    val alreadyLoadedPainter: Painter?
      get() = if (painter.isInitialized()) painter.value else null

    @WorkerThread
    fun loadPainterIfNeeded(): Painter {
      return painter.value
    }

    @WorkerThread
    private fun createPainter(): Painter {
      @Suppress("HttpUrlsUsage") val isNetworkUrl = imageSource.startsWith("http://") || imageSource.startsWith("https://")
      require(isNetworkUrl) { "Only network URLs are supported, but got: $imageSource" }

      val bytes = HttpRequests.request(imageSource).readBytes(null)

      return if (imageSource.endsWith(".svg", ignoreCase = true)) {
        bytes.decodeToSvgPainter(density)
      } else {
        val skiaImage = Image.makeFromEncoded(bytes)
        BitmapPainter(skiaImage.toComposeImageBitmap())
      }
    }
  }
}
