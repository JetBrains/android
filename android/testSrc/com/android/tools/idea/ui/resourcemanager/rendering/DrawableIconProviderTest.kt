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

import com.android.ide.common.resources.ResourceResolver
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ResourceType
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.ui.resourcemanager.ImageCacheRule
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.mock.MockVirtualFile
import com.intellij.util.ui.ImageUtil
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse

class DrawableIconProviderTest {

  @get:Rule
  var imageCacheRule = ImageCacheRule()

  @get:Rule
  var androidProjectRule = AndroidProjectRule.inMemory()

  val facet get() = androidProjectRule.module.androidFacet!!

  @Test
  fun get0SizedListCellRendererComponent() {
    val latch = CountDownLatch(1)
    @Suppress("UndesirableClassUsage")
    val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB).apply {
      with(createGraphics()) {
        color = Color(0x012345)
        fillRect(0, 0, 100, 100)
      }
    }

    val provider = DrawableIconProvider(facet, createResourceResolver(facet),
                                        imageCacheRule.imageCache) { _, _ -> CompletableFuture.completedFuture(image) }
    val designAsset = DesignAsset(MockVirtualFile("file.png"), emptyList(), ResourceType.DRAWABLE)
    provider.getIcon(designAsset, 0, 0, { latch.countDown() })
    assertFalse("The size is 0, the refresh callback should not be called") { latch.await(1, TimeUnit.SECONDS) }
    val imageIcon = provider.getIcon(designAsset, 0, 0, {})
    val result = ImageUtil.toBufferedImage(imageIcon.image)
    // Check that when the thumbnail width is 0, nothing break and we don't display the image
    assertThat(result.getRGB(0, 0)).isNotEqualTo(0xff012345.toInt())
  }

  @Test
  fun nullImage() {
    val latch = CountDownLatch(1)
    val provider = DrawableIconProvider(facet, createResourceResolver(facet),
                                        imageCacheRule.imageCache) { _, _ -> CompletableFuture.completedFuture(null) }
    val designAsset = DesignAsset(MockVirtualFile("file.png"), emptyList(), ResourceType.DRAWABLE)
    provider.getIcon(designAsset, 0, 0, { latch.countDown() })
    assertFalse("The size is 0, the refresh callback should not be called") { latch.await(1, TimeUnit.SECONDS) }
    val imageIcon = provider.getIcon(designAsset, 0, 0, {})
    val result = ImageUtil.toBufferedImage(imageIcon.image)
    assertNotNull(result)
  }
}

private fun createResourceResolver(androidFacet: AndroidFacet): ResourceResolver {
  val configurationManager = ConfigurationManager.getOrCreateInstance(androidFacet)
  val manifest = MergedManifestManager.getSnapshot(androidFacet)
  val theme = manifest.manifestTheme ?: manifest.getDefaultTheme(null, null, null)
  return configurationManager.resolverCache.getResourceResolver(null, theme, FolderConfiguration.createDefault())
}