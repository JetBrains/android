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
package com.android.tools.idea.whatsnew.assistant.v2.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tools.adtui.compose.IntUiPaletteDefaults
import com.android.tools.adtui.compose.rememberColor
import com.android.tools.idea.whatsnew.assistant.v2.model.Card
import com.android.tools.idea.whatsnew.assistant.v2.model.WhatsNewData
import com.android.tools.idea.whatsnew.assistant.v2.model.getAllImageSources
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.HttpRequests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.markdown.Markdown
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.painter.rememberResourcePainterProvider
import org.jetbrains.skia.Image

@Suppress("UnstableApiUsage")
@OptIn(ExperimentalJewelApi::class)
@Composable
fun WhatsNewPanel(whatsNewData: WhatsNewData) {
  val imageCache = remember { mutableStateMapOf<String, RemoteImage>() }

  // Fetch all images
  LaunchedEffect(whatsNewData) {
    whatsNewData.getAllImageSources().distinct().forEach { url ->
      launch {
        runCatching {
            withContext(Dispatchers.IO) {
              val bytes = HttpRequests.request(url).readBytes(null)
              Image.makeFromEncoded(bytes).toComposeImageBitmap()
            }
          }
          .onSuccess { imageCache[url] = RemoteImage(true, it) }
          .onFailure {
            imageCache[url] = RemoteImage(false)
            thisLogger().warn("Failed to fetch image: $it")
          }
      }
    }
  }

  val state = rememberLazyGridState()
  val tallestCardByRow = remember { mutableStateMapOf<Int, Int>() }

  // Use derived state for properties depending on layoutInfo to avoid recomposing on every scroll offset change
  val visibleItemsInfo by remember { derivedStateOf { state.layoutInfo.visibleItemsInfo } }

  // Clear rows that are no longer visible
  val visibleRows = visibleItemsInfo.map { it.row }.toSet()
  val rowsToRemove = tallestCardByRow.keys.filter { it !in visibleRows }
  rowsToRemove.forEach { tallestCardByRow.remove(it) }

  // For visible rows, clamp all cards to the same height as the tallest card
  // TODO: This means when the viewport size changes, if the cards become wider, then extra whitespace is left at the end because it
  // calculates based on current size. Scrolling a previously oversized card into view also causes some jankiness with a skip in the
  // scrolling. Calculating during onSizeChanged doesn't work well, because it makes the height flicker when resizing. Realistically, the
  // card blurbs will be kept shorter, so the whitespace and jank won't be as noticeable.
  visibleItemsInfo.forEach {
    if (tallestCardByRow[it.row] == null) {
      tallestCardByRow[it.row] = 0
    }

    val currentTallest = tallestCardByRow[it.row]!!
    if (it.size.height > currentTallest) {
      tallestCardByRow[it.row] = it.size.height
    }
  }

  // TODO: add metrics for scrolling and maybe clicking links
  LazyVerticalGrid(
    columns = GridCells.Adaptive(250.dp),
    modifier = Modifier.padding(14.dp).fillMaxSize(),
    state = state,
    verticalArrangement = Arrangement.spacedBy(19.dp),
    horizontalArrangement = Arrangement.spacedBy(19.dp),
  ) {
    item(span = { GridItemSpan(maxLineSpan) }) {
      Row {
        val productIcon by
          rememberResourcePainterProvider("stable/product-icon.svg", WhatsNewData::class.java).getPainter()
        Image(painter = productIcon, contentDescription = null) // TODO: get this from the XML
        Spacer(Modifier.width(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Text(whatsNewData.label ?: "", fontWeight = FontWeight.Bold, fontSize = 18.sp)
          Markdown(markdown = whatsNewData.description ?: "")
        }
      }
    }

    items(whatsNewData.cards.size) { i ->
      val card = whatsNewData.cards[i]

      // Determine the row for this item.
      val itemInfo = visibleItemsInfo.find { it.index == i + 1 } // +1 because of the header item
      val row = itemInfo?.row ?: -1
      val rowHeight = if (row >= 0) tallestCardByRow[row] ?: 0 else 0

      FeatureCard(card = card, imageCache = imageCache, minHeightPx = rowHeight)
    }

    item(span = { GridItemSpan(maxLineSpan) }) { Text(whatsNewData.footerText ?: "", style = TextStyle(fontStyle = FontStyle.Italic)) }
  }
}

@Suppress("UnstableApiUsage")
@OptIn(ExperimentalJewelApi::class)
@Composable
private fun FeatureCard(card: Card, imageCache: Map<String, RemoteImage>, minHeightPx: Int) {
  val density = LocalDensity.current
  val minHeightDp = with(density) { minHeightPx.toDp() }
  var hovered by remember { mutableStateOf(false) }

  // Drop shadow when hovered
  val elevation by animateDpAsState(targetValue = if (hovered) 8.dp else 0.dp, animationSpec = tween(durationMillis = 200))

  Column(
    modifier =
      Modifier.shadow(elevation, RoundedCornerShape(10.dp))
        .clip(RoundedCornerShape(10.dp))
        .background(JewelTheme.globalColors.panelBackground)
        .border(width = 1.dp, shape = RoundedCornerShape(10.dp), color = cardBorderColor())
        .fillMaxHeight()
        .then(if (minHeightPx > 0) Modifier.defaultMinSize(minHeight = minHeightDp) else Modifier)
        .onHover({ hovered = it })
  ) {
    if (card.image != null && card.image.source != null) {
      LoadingImage(card.image, imageCache, hovered)
    }
    Column(Modifier.padding(16.dp)) {
      Text(card.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
      Spacer(Modifier.height(8.dp))
      Markdown(markdown = card.description, onUrlClick = { link -> BrowserUtil.browse(link) }, modifier = Modifier.fillMaxHeight())
    }
  }
}

@OptIn(ExperimentalJewelApi::class)
@Composable
private fun LoadingImage(
  image: com.android.tools.idea.whatsnew.assistant.v2.model.Image,
  imageCache: Map<String, RemoteImage>,
  hovered: Boolean,
) {
  val remoteImage = imageCache[image.source]

  // Zoom slightly when hovered
  val scale by animateFloatAsState(targetValue = if (hovered) 1.15f else 1.0f, animationSpec = tween(durationMillis = 400))

  Column(
    modifier =
      Modifier.height(160.dp).fillMaxWidth().background(cardBorderColor()).clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    if (remoteImage == null) {
      Text("Loading image...")
    } else if (remoteImage.fetchedSuccess) {
      // TODO: support gifs?
      Image(
        bitmap = remoteImage.fetchedImage!!,
        contentDescription = image.description,
        contentScale = ContentScale.Crop,
        alignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().scale(scale),
      )
    } else {
      // TODO: allow fallback to bundled image if one with the same filename exists
      Text("Failed to load image.")
    }
  }
}

@Composable
private fun cardBorderColor(): Color =
  rememberColor(
    key = "WhatsNewPanel.background",
    darkFallbackKey = "ColorPalette.Gray3",
    darkDefault = Color(0xFF3C3F41.toInt()),
    lightFallbackKey = "ColorPalette.Gray12",
    lightDefault = Color(IntUiPaletteDefaults.Light.Gray12), // TODO
  )

private data class RemoteImage(val fetchedSuccess: Boolean, val fetchedImage: ImageBitmap? = null)
