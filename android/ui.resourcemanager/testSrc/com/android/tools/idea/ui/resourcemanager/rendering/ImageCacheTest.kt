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
package com.android.tools.idea.ui.resourcemanager.rendering

import com.android.resources.ResourceType
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.google.common.truth.Truth.assertThat
import com.intellij.mock.MockVirtualFile
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import java.awt.image.ImageObserver
import java.awt.image.IndexColorModel
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class DebugFakeImage(private val identifier: String) : BufferedImage(10, 10, IndexColorModel.OPAQUE) {
  override fun getSource() = throw NotImplementedError()
  override fun getProperty(name: String?, observer: ImageObserver?) = throw NotImplementedError()
  override fun getGraphics() = throw NotImplementedError()
  override fun toString() = identifier
}

private fun fakeAsset() = DesignAsset(MockVirtualFile(""), listOf(), ResourceType.DRAWABLE, "fake")

class ImageCacheTest {
  private val imageA = DebugFakeImage("Image A")
  private val imageB = DebugFakeImage("Image B")

  private val placeholder = DebugFakeImage("Placeholder")

  @get:Rule
  val imageCacheRule = ImageCacheRule()

  @Test
  fun storeImage() {
    val helper = imageCacheRule.imageCache
    val key = fakeAsset()
    val latch = CountDownLatch(1)
    val res = helper.computeAndGet(key, placeholder, false) {
      CompletableFuture.completedFuture(imageA).also { latch.countDown() }
    }
    assertThat(res).isEqualTo(placeholder)
    latch.await(1, TimeUnit.SECONDS)
    assertThat(helper.computeAndGet(key, placeholder, false) { CompletableFuture.completedFuture(imageB) }).isEqualTo(imageA)
  }

  /**
   * Test that the image is overridden when the computation is forced
   */
  @Test
  fun valueOverridden() {
    val helper = imageCacheRule.imageCache
    val key = fakeAsset()
    val latch = CountDownLatch(1)
    val latch2 = CountDownLatch(1)
    val res = helper.computeAndGet(key, placeholder, false) {
      CompletableFuture.completedFuture(imageA).also { latch.countDown() }
    }
    assertThat(res).isEqualTo(placeholder)

    // Checks that the previously cached image is returned and not the placeholder
    val res2 = helper.computeAndGet(key, placeholder, true) {
      CompletableFuture.completedFuture(imageB).whenComplete { t, u ->
        latch2.countDown()
      }
    }
    assertThat(res2).isEqualTo(imageA)

    assertTrue(latch2.await(1, TimeUnit.SECONDS), "Latch2 was not decremented.")
    val res3 = helper.computeAndGet(key, placeholder, true) {
      CompletableFuture.completedFuture(null)
    }
    assertThat(res3).isEqualTo(imageB)
  }

  @Test
  fun assetsOnSameFileWithDifferentNameDoNotCollide() {
    val asset1 = DesignAsset(MockVirtualFile("values/attrs.xml"), listOf(), ResourceType.DRAWABLE, "my_asset_1")
    val asset2 = DesignAsset(MockVirtualFile("values/attrs.xml"), listOf(), ResourceType.DRAWABLE, "my_asset_2")
    testNoCollision(asset1, asset2)
  }

  @Test
  fun assetsWithSameNameOfDifferentTypeDoNotCollide() {
    val asset1 = DesignAsset(MockVirtualFile("values.xml"), listOf(), ResourceType.COLOR, "my_asset")
    val asset2 = DesignAsset(MockVirtualFile("values.xml"), listOf(), ResourceType.DRAWABLE, "my_asset")
    testNoCollision(asset1, asset2)
  }

  @Test
  fun cacheIsInvalidatedWhenFileChanges() {
    val sourceFile = MockVirtualFile("values.xml")
    val asset = DesignAsset(sourceFile, listOf(), ResourceType.DRAWABLE, "my_asset")

    var computedImage: BufferedImage? = null
    run {
      val latch = CountDownLatch(1)
      computedImage = imageCacheRule.imageCache.computeAndGet(asset, placeholder, false) {
        CompletableFuture.completedFuture(imageA).whenComplete { _, _ ->
          latch.countDown()
        }
      }
      assertEquals(placeholder, computedImage)
      assertTrue(latch.await(1, TimeUnit.SECONDS))

      computedImage = imageCacheRule.imageCache.computeAndGet(asset, placeholder, false) {
        throw IllegalStateException("Image should not be computed, it is already cached")
      }
      assertEquals(imageA, computedImage)
    }
    assertNotNull(computedImage)

    // Modify file to ensure cache is re-calculated
    sourceFile.modificationStamp = 123L
    run {
      val latch = CountDownLatch(1)
      imageCacheRule.imageCache.computeAndGet(asset, placeholder, false) {
        CompletableFuture.completedFuture(imageB).whenComplete { _, _ ->
          latch.countDown()
        }
      }
      assertTrue(latch.await(1, TimeUnit.SECONDS))

      val newImage = imageCacheRule.imageCache.computeAndGet(asset, placeholder, false) {
        throw IllegalStateException("Image should not be computed, it is already cached")
      }
      assertNotEquals(computedImage, newImage)
    }
  }

  private fun testNoCollision(asset1: DesignAsset, asset2:DesignAsset) {
    val imageCache = imageCacheRule.imageCache
    val latch1 = CountDownLatch(1)
    val latch2 = CountDownLatch(1)

    var result1 = placeholder
    imageCache.computeAndGet(asset1, placeholder, false) {
      CompletableFuture.completedFuture(imageA).whenComplete { image, _ ->
        result1 = image
        latch1.countDown()
      }
    }
    assertTrue(latch1.await(1, TimeUnit.SECONDS))
    assertThat(result1).isEqualTo(imageA)

    var result2 = placeholder
    imageCache.computeAndGet(asset2, placeholder, false) {
      CompletableFuture.completedFuture(imageB).whenComplete { image, _ ->
        result2 = image
        latch2.countDown()
      }
    }

    assertTrue(latch2.await(1, TimeUnit.SECONDS), "'asset2' should lead to a different key, the latch should countdown.")
    assertThat(result2).isEqualTo(imageB)
  }
}