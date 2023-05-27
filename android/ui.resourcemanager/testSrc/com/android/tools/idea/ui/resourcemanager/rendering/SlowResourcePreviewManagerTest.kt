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
package com.android.tools.idea.ui.resourcemanager.rendering

import com.android.resources.ResourceType
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.model.Asset
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.mock.MockVirtualFile
import com.intellij.util.ui.ImageUtil
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JLabel
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlowResourcePreviewManagerTest {

  @get:Rule
  var imageCacheRule = ImageCacheRule()

  @get:Rule
  var androidProjectRule = AndroidProjectRule.inMemory()

  val facet get() = androidProjectRule.fixture.module.androidFacet!!

  @Test
  fun getPlaceholderThenRealPreview() {
    val latch = CountDownLatch(1)
    val provider = SlowResourcePreviewManager(imageCacheRule.imageCache, TestSlowPreviewProvider)
    val designAsset = DesignAsset(MockVirtualFile("file.png"), emptyList(), ResourceType.DRAWABLE)

    val placeHolder = provider.getIcon(designAsset, 20, 20, JLabel(), { latch.countDown() }).image.let { ImageUtil.toBufferedImage(it) }
    // Placeholder is a red icon
    assertThat(placeHolder.getRGB(0, 0)).isEqualTo(0xffff0000.toInt())

    // Wait for callback
    assertTrue(latch.await(1, TimeUnit.SECONDS))

    // Get 'real' preview
    val preview = provider.getIcon(designAsset, 20, 20, JLabel(), {}).image.let { ImageUtil.toBufferedImage(it) }
    assertThat(preview.getRGB(0, 0)).isEqualTo(0xff012345.toInt())
  }

  @Test
  fun get0SizedListCellRendererComponent() {
    val latch = CountDownLatch(1)
    val provider = SlowResourcePreviewManager(imageCacheRule.imageCache, TestSlowPreviewProvider)
    val designAsset = DesignAsset(MockVirtualFile("file.png"), emptyList(), ResourceType.DRAWABLE)

    provider.getIcon(designAsset, 0, 0, JLabel(), { latch.countDown() })
    assertFalse("The size is 0, the refresh callback should not be called") { latch.await(1, TimeUnit.SECONDS) }

    val imageIcon = provider.getIcon(designAsset, 0, 0, JLabel(), {})
    val result = ImageUtil.toBufferedImage(imageIcon.image)
    // Check that when the thumbnail width is 0, nothing break and we don't display the image
    assertThat(result.getRGB(0, 0)).isNotEqualTo(0xff012345.toInt())
  }

  @Test
  fun nullImage() {
    val latch = CountDownLatch(1)
    val provider = SlowResourcePreviewManager(imageCacheRule.imageCache, TestSlowPreviewProvider)
    val designAsset = DesignAsset(MockVirtualFile("file.png"), emptyList(), ResourceType.DRAWABLE)

    provider.getIcon(designAsset, 0, 0, JLabel(), { latch.countDown() })
    assertFalse("The size is 0, the refresh callback should not be called") { latch.await(1, TimeUnit.SECONDS) }

    val imageIcon = provider.getIcon(designAsset, 0, 0, JLabel(), {})
    val result = ImageUtil.toBufferedImage(imageIcon.image)
    assertNotNull(result)
  }
}

private object TestSlowPreviewProvider : SlowResourcePreviewProvider {
  override val previewPlaceholder: BufferedImage = ImageUtil.createImage(80, 80, BufferedImage.TYPE_INT_ARGB).apply {
    with(createGraphics()) {
      this.color = Color(255, 0, 0, 255)
      fillRect(0, 0, 80, 80)
      dispose()
    }
  }

  override fun getSlowPreview(width: Int, height: Int, asset: Asset): BufferedImage? {
    return createTestImage()
  }
}