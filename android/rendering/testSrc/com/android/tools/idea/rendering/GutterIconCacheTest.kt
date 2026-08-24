/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.rendering

import com.android.tools.idea.io.TestFileUtils
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import icons.StudioIcons.Common.ANDROID_HEAD
import icons.StudioIcons.Common.WARNING
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.Icon
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class GutterIconCacheTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val facet by lazy { checkNotNull(projectRule.module.androidFacet) }

  private lateinit var cache: GutterIconCache
  private lateinit var sampleSvgPath: Path
  private lateinit var sampleSvgFile: VirtualFile
  private var icon: Icon? = null
  private var highDpiDisplay = false

  @Before
  fun setUp() {
    cache = GutterIconCache(projectRule.project, ::highDpiDisplay) { _, _, _ -> icon }
    val basePath = checkNotNull(projectRule.project.basePath) { "Need non-null base path!" }
    sampleSvgPath = FileSystems.getDefault().getPath(basePath, "HeyImAFile.xml")
    sampleSvgFile = TestFileUtils.writeFileAndRefreshVfs(sampleSvgPath, "whose contents are immaterial")
  }

  @Test
  fun cacheEmptyToStart() {
    assertThat(cache.getIconIfCached(sampleSvgFile)).isNull()
  }

  @Test
  fun cachedNullValue() {
    assertThat(cache.getIcon(sampleSvgFile, null, facet)).isNull()

    icon = ANDROID_HEAD

    assertThat(cache.getIconIfCached(sampleSvgFile)).isNull()
    // Should return cached value.
    assertThat(cache.getIcon(sampleSvgFile, null, facet)).isNull()
  }

  @Test
  fun cachedNonNullValue() {
    icon = ANDROID_HEAD

    assertThat(cache.getIcon(sampleSvgFile, null, facet)).isEqualTo(ANDROID_HEAD)

    icon = null

    assertThat(cache.getIconIfCached(sampleSvgFile)).isEqualTo(ANDROID_HEAD)
    // Should return cached value.
    assertThat(cache.getIcon(sampleSvgFile, null, facet)).isEqualTo(ANDROID_HEAD)
  }

  @Test
  fun cachedValueIgnoredOnFileChange() {
    icon = ANDROID_HEAD

    assertThat(cache.getIcon(sampleSvgFile, null, facet)).isEqualTo(ANDROID_HEAD)

    // "Modify" Document by rewriting its contents
    val document = runReadActionBlocking { checkNotNull(FileDocumentManager.getInstance().getDocument(sampleSvgFile)) }
    with(ApplicationManager.getApplication()) { invokeAndWait { runWriteAction { document.setText(document.text) } } }

    assertThat(cache.getIconIfCached(sampleSvgFile)).isNull()

    icon = WARNING
    assertThat(cache.getIcon(sampleSvgFile, null, facet)).isEqualTo(WARNING)
  }

  @Test
  fun cacheClearedOnHiDpiChange() {
    icon = ANDROID_HEAD

    assertThat(cache.getIcon(sampleSvgFile, null, facet)).isEqualTo(ANDROID_HEAD)

    highDpiDisplay = true

    assertThat(cache.getIconIfCached(sampleSvgFile)).isNull()

    icon = WARNING
    assertThat(cache.getIcon(sampleSvgFile, null, facet)).isEqualTo(WARNING)

    highDpiDisplay = false

    assertThat(cache.getIconIfCached(sampleSvgFile)).isNull()

    icon = ANDROID_HEAD
    assertThat(cache.getIcon(sampleSvgFile, null, facet)).isEqualTo(ANDROID_HEAD)
  }

  @Test
  fun concurrentRequestsForSameFileAreDeduplicated() {
    val renderCount = AtomicInteger(0)
    val latch = CountDownLatch(1)
    cache =
      GutterIconCache(projectRule.project, ::highDpiDisplay) { _, _, _ ->
        renderCount.incrementAndGet()
        latch.await(5, TimeUnit.SECONDS)
        ANDROID_HEAD
      }

    val numThreads = 5
    val executor = Executors.newFixedThreadPool(numThreads)
    try {
      val futures = (1..numThreads).map { CompletableFuture.supplyAsync({ cache.getIcon(sampleSvgFile, null, facet) }, executor) }
      Thread.sleep(200)
      latch.countDown()
      futures.forEach { assertThat(it.get(5, TimeUnit.SECONDS)).isEqualTo(ANDROID_HEAD) }
      assertThat(renderCount.get()).isEqualTo(1)
    } finally {
      executor.shutdownNow()
    }
  }

  @Test
  fun concurrentRequestsAreThrottled() {
    val activeRenders = AtomicInteger(0)
    val maxObservedActive = AtomicInteger(0)
    val startLatch = CountDownLatch(1)

    cache =
      GutterIconCache(projectRule.project, ::highDpiDisplay) { _, _, _ ->
        val active = activeRenders.incrementAndGet()
        maxObservedActive.updateAndGet { max -> maxOf(max, active) }
        startLatch.await(5, TimeUnit.SECONDS)
        activeRenders.decrementAndGet()
        ANDROID_HEAD
      }

    val numFiles = 5
    val files =
      (1..numFiles).map { i ->
        val path = FileSystems.getDefault().getPath(projectRule.project.basePath!!, "File$i.xml")
        TestFileUtils.writeFileAndRefreshVfs(path, "content $i")
      }

    val executor = Executors.newFixedThreadPool(numFiles)
    try {
      val futures = files.map { file -> CompletableFuture.supplyAsync({ cache.getIcon(file, null, facet) }, executor) }
      Thread.sleep(200)
      startLatch.countDown()
      futures.forEach { assertThat(it.get(5, TimeUnit.SECONDS)).isEqualTo(ANDROID_HEAD) }
      assertThat(maxObservedActive.get()).isAtMost(2)
    } finally {
      executor.shutdownNow()
    }
  }

  @Test
  fun testDpiChangeCancelsPendingRendersAndDoesNotPolluteCache() {
    val renderLatch = CountDownLatch(1)
    val renderStartedLatch = CountDownLatch(1)

    cache =
      GutterIconCache(projectRule.project, ::highDpiDisplay) { _, _, _ ->
        renderStartedLatch.countDown()
        renderLatch.await(5, TimeUnit.SECONDS)
        ANDROID_HEAD
      }

    val executor = Executors.newSingleThreadExecutor()
    try {
      val future = CompletableFuture.supplyAsync({ cache.getIcon(sampleSvgFile, null, facet) }, executor)

      // Wait for render to actually start
      renderStartedLatch.await(5, TimeUnit.SECONDS)

      // Change DPI while render is in flight
      highDpiDisplay = true

      // Release the render task
      renderLatch.countDown()

      // The future should throw or be completed
      try {
        future.get(5, TimeUnit.SECONDS)
      } catch (_: Exception) {
        // Expected since the future is cancelled
      }

      // Verify that cache remains empty after DPI changed and render completed
      assertThat(cache.getIconIfCached(sampleSvgFile)).isNull()
    } finally {
      executor.shutdownNow()
    }
  }

  @Test
  fun testRenderFailureIsCachedAndDoesNotSpam() {
    val renderCount = AtomicInteger(0)
    cache =
      GutterIconCache(projectRule.project, ::highDpiDisplay) { _, _, _ ->
        renderCount.incrementAndGet()
        throw RuntimeException("Boom!")
      }

    // First attempt throws and should return null
    assertThat(cache.getIcon(sampleSvgFile, null, facet)).isNull()
    assertThat(renderCount.get()).isEqualTo(1)

    // Second attempt should return cached null without calling renderer again
    assertThat(cache.getIcon(sampleSvgFile, null, facet)).isNull()
    assertThat(renderCount.get()).isEqualTo(1)
  }
}
