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
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.google.common.util.concurrent.Futures
import com.intellij.mock.MockVirtualFile
import com.intellij.testFramework.DisposableRule
import com.intellij.ui.components.JBList
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.UIUtil
import org.junit.Rule
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class DrawableResourceCellRendererTest {

  @Rule
  @JvmField
  val rule = DisposableRule()

  @Test
  fun getListCellRendererComponent() {
    val jList = JBList<DesignAssetSet>().apply {
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
    val renderer = DrawableResourceCellRenderer(rule.disposable, { _, _ -> Futures.immediateFuture(image) }) {
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
    val jList = JBList<DesignAssetSet>().apply {
      fixedCellHeight = 0
      fixedCellWidth = 0
    }
    @Suppress("UndesirableClassUsage")
    val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB).apply {
      with(createGraphics()) {
        color = Color(0x012345)
        fillRect(0, 0, 100, 100)
      }
    }
    val latch = CountDownLatch(1)
    val renderer = DrawableResourceCellRenderer(rule.disposable, { _, _ -> Futures.immediateFuture(image) }) {
      jList.paintImmediately(jList.bounds)
      latch.countDown()
    }
    val designAssetSet = DesignAssetSet("name", listOf(DesignAsset(MockVirtualFile("file.png"), emptyList(), ResourceType.DRAWABLE)))
    renderer.getListCellRendererComponent(jList, designAssetSet, 0, false, false) as JComponent
    latch.await(10, TimeUnit.MILLISECONDS)
    val component = renderer.getListCellRendererComponent(jList, designAssetSet, 0, false, false) as JComponent
    val icon = UIUtil.findComponentsOfType(component, JLabel::class.java).first().icon as ImageIcon
    val result = ImageUtil.toBufferedImage(icon.image)
    assertNotEquals(0xff012345.toInt(), result.getRGB(0, 0))
  }

  @Test
  fun nullImage() {
    val jList = JBList<DesignAssetSet>().apply {
      fixedCellHeight = 100
      fixedCellWidth = 100
    }
    val latch = CountDownLatch(1)
    val renderer = DrawableResourceCellRenderer(rule.disposable, { _, _ -> Futures.immediateFuture(null) }) {
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