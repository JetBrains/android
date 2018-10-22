/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.view

import com.android.resources.ResourceType
import com.android.tools.idea.resourceExplorer.ImageCache
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.google.common.truth.Truth
import com.google.common.util.concurrent.Futures
import com.intellij.mock.MockVirtualFile
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.UIUtil
import org.junit.Ignore
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DrawableResourceCellRendererTest {

  private val imageCache = ImageCache()

  @Ignore("b/117130787")
  @Test
  fun getListCellRendererComponent() {
    val jList = AssetListView(emptyList()).apply {
      fixedCellHeight = 100
      fixedCellWidth = 100
    }
    @Suppress("UndesirableClassUsage")
    val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB).apply {
      with(createGraphics()) {
        color = Color(0x012345)
        fillRect(0, 0, 100, 100)
      }
    }
    val latch = CountDownLatch(1)
    val renderer = DrawableResourceCellRenderer({ _, _ -> Futures.immediateFuture(image) }, imageCache) {
      jList.paintImmediately(jList.bounds)
      latch.countDown()
    }
    val designAssetSet = DesignAssetSet("name", listOf(DesignAsset(MockVirtualFile("file.png"), emptyList(), ResourceType.DRAWABLE)))
    renderer.getListCellRendererComponent(jList, designAssetSet, 0, false, false) as JComponent
    latch.await(10, TimeUnit.MILLISECONDS)
    val component = renderer.getListCellRendererComponent(jList, designAssetSet, 0, false, false) as JComponent
    val icon = UIUtil.findComponentsOfType(component, JLabel::class.java).first().icon as ImageIcon
    val result = ImageUtil.toBufferedImage(icon.image)
    assertEquals(0xff012345.toInt(), result.getRGB(0, 0))
  }

  @Test
  fun get0SizedListCellRendererComponent() {
    val jList = AssetListView(emptyList()).apply {
      thumbnailWidth = 0
      isGridMode = true
    }
    @Suppress("UndesirableClassUsage")
    val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB).apply {
      with(createGraphics()) {
        color = Color(0x012345)
        fillRect(0, 0, 100, 100)
      }
    }
    val latch = CountDownLatch(1)
    val renderer = DrawableResourceCellRenderer({ _, _ -> Futures.immediateFuture(image) }, imageCache) {
      jList.paintImmediately(jList.bounds)
      latch.countDown()
    }
    val designAssetSet = DesignAssetSet("name", listOf(DesignAsset(MockVirtualFile("file.png"), emptyList(), ResourceType.DRAWABLE)))
    renderer.getListCellRendererComponent(jList, designAssetSet, 0, false, false) as JComponent
    latch.await(10, TimeUnit.MILLISECONDS)
    val component = renderer.getListCellRendererComponent(jList, designAssetSet, 0, false, false) as JComponent
    val icon = UIUtil.findComponentsOfType(component, JLabel::class.java).first().icon as ImageIcon
    val result = ImageUtil.toBufferedImage(icon.image)

    // Check that when the thumbnail width is 0, nothing break and we don't display the image
    Truth.assertThat(result.getRGB(0, 0)).isNotEqualTo(0xff012345.toInt())
  }

  @Test
  fun nullImage() {
    val jList = AssetListView(emptyList()).apply {
      fixedCellHeight = 100
      fixedCellWidth = 100
    }
    val latch = CountDownLatch(1)
    val renderer = DrawableResourceCellRenderer({ _, _ -> Futures.immediateFuture(null) }, imageCache) {
      jList.paintImmediately(jList.bounds)
      latch.countDown()
    }
    val designAssetSet = DesignAssetSet("name", listOf(DesignAsset(MockVirtualFile("file.png"), emptyList(), ResourceType.DRAWABLE)))
    renderer.getListCellRendererComponent(jList, designAssetSet, 0, false, false) as JComponent
    latch.await(10, TimeUnit.MILLISECONDS)
    val component = renderer.getListCellRendererComponent(jList, designAssetSet, 0, false, false) as JComponent
    val icon = UIUtil.findComponentsOfType(component, JLabel::class.java).first().icon as ImageIcon
    assertNotNull(icon)
  }
}