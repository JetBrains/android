/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.explorer

import com.android.resources.ResourceType
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.android.tools.idea.ui.resourcemanager.rendering.ImageCacheRule
import com.android.tools.idea.ui.resourcemanager.rendering.StubAssetPreviewManager
import com.intellij.mock.MockVirtualFile
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.UIUtil
import org.junit.Rule
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.test.assertEquals

class DesignAssetCellRendererTest {

  @get:Rule
  var imageCacheRule = ImageCacheRule()

  @get:Rule
  var androidProjectRule = AndroidProjectRule.inMemory()
  @Test
  fun getListCellRendererComponent() {
    val imageIcon = ImageIcon(BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB).apply {
      with(createGraphics()) {
        color = Color(0x012345)
        fillRect(0, 0, 100, 100)
      }
    })

    val assetPreviewManager = StubAssetPreviewManager(imageIcon)
    val jList = AssetListView(emptyList()).apply {
      fixedCellHeight = 100
      fixedCellWidth = 100
    }

    val renderer = DesignAssetCellRenderer(assetPreviewManager)
    val designAssetSet = ResourceAssetSet("name", listOf(DesignAsset(MockVirtualFile("file.png"), emptyList(), ResourceType.DRAWABLE)))

    val component = renderer.getListCellRendererComponent(jList, designAssetSet, 0, false, false) as JComponent
    val icon = UIUtil.findComponentsOfType(component, JLabel::class.java).first().icon as ImageIcon
    val result = ImageUtil.toBufferedImage(icon.image)
    assertEquals(0xff012345.toInt(), result.getRGB(0, 0))
  }
}