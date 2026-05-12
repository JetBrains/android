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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.android.tools.idea.whatsnew.assistant.v2.ui.composeutils.ImagePainterLoaderMarkdownRendererExtension.Companion.LocalContainerWidth
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.extensions.ImageRendererExtension
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension

/** A [MarkdownRendererExtension] that uses a [ImagePainterLoader] to render [org.jetbrains.jewel.markdown.InlineMarkdown.Image] */
@OptIn(ExperimentalJewelApi::class)
@Suppress("UnstableApiUsage")
internal class ImagePainterLoaderMarkdownRendererExtension(imageLoader: ImagePainterLoader, popupEffect: Boolean = false) :
  MarkdownRendererExtension {
  override val imageRendererExtension: ImageRendererExtension = ImageRendererExtensionImpl(imageLoader, popupEffect)

  private class ImageRendererExtensionImpl(private val imageLoader: ImagePainterLoader, private val popupEffect: Boolean) :
    ImageRendererExtension {
    @Composable
    override fun renderImageContent(image: InlineMarkdown.Image): InlineTextContent? {
      val density = LocalDensity.current
      val imagePainter = remember(image.source, density) { mutableStateOf(imageLoader.getAlreadyLoadedPainter(image.source, density)) }

      val painter = imagePainter.value
      return if (painter == null) {
        LaunchedEffect(image.source, density) {
          withContext(Dispatchers.IO) {
            try {
              imagePainter.value = imageLoader.loadPainter(image.source, density)
            } catch (e: Exception) {
              Logger.getInstance("WhatsNewPanel").warn("Failed to fetch image: ${image.source}", e)
            }
          }
        }
        null
      } else {
        val (width, height) = computeImageSize(painter)
        InlineTextContent(Placeholder(width = width, height = height, placeholderVerticalAlign = PlaceholderVerticalAlign.Top)) {
          // Apply popup effect
          val modifier =
            Modifier.thenIf(popupEffect) {
              val isHovered = LocalCardHovered.current
              val scale by animateFloatAsState(if (isHovered) 1.15f else 1f)
              Modifier.clip(RoundedCornerShape(8.dp)).scale(scale)
            }

          Image(painter = painter, contentDescription = image.alt, modifier = modifier, contentScale = ContentScale.Fit)
        }
      }
    }

    /**
     * Computes the appropriate placeholder size for the image, maintaining its intrinsic aspect ratio. The image width is bounded by the
     * available container width (provided via [LocalContainerWidth]).
     */
    @Composable
    private fun computeImageSize(painter: Painter): Pair<TextUnit, TextUnit> {
      val density = LocalDensity.current
      val containerWidthDp = LocalContainerWidth.current
      val intrinsicSize = painter.intrinsicSize
      val (width, height) =
        if (intrinsicSize.isUnspecified) {
          val w = if (containerWidthDp.isSpecified) containerWidthDp else 500.dp
          with(density) { w.toSp() to (w / 2f).toSp() }
        } else {
          with(density) {
            val imageWidthDp = intrinsicSize.width.toDp()
            val ratio = intrinsicSize.width / intrinsicSize.height

            val finalWidthDp = if (containerWidthDp.isSpecified) minOf(imageWidthDp, containerWidthDp) else imageWidthDp
            val finalHeightDp = finalWidthDp / ratio

            finalWidthDp.toSp() to finalHeightDp.toSp()
          }
        }

      return Pair(width, height)
    }
  }

  companion object {
    val LocalContainerWidth = staticCompositionLocalOf { Dp.Unspecified }
    val LocalCardHovered = staticCompositionLocalOf { false }
  }
}
