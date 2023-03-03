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
package com.android.tools.idea.ui.resourcemanager.plugin

import com.android.ide.common.resources.ResourceResolver
import com.android.resources.ResourceType
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.rendering.StudioRenderService
import com.android.tools.idea.rendering.taskBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.rendering.ImageCache
import com.android.tools.idea.ui.resourcemanager.explorer.ResourceExplorerListViewModel
import com.android.tools.idea.ui.resourcemanager.explorer.ResourceExplorerListViewModelImpl
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.FilterOptions
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.mock.MockVirtualFileSystem
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.xml.XmlFile
import com.intellij.util.ui.ImageUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.awt.Image
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.ImageIcon
import javax.swing.JLabel
import kotlin.test.assertTrue


private val BACKGROUND_COLOR = arrayOf(0xFA, 0xD1, 0x3A, 0xFF)
private const val BACKGROUND_COLOR_HEX_STRING = "FAD13A"

class LayoutRendererTest {

  @get:Rule
  val rule = AndroidProjectRule.withSdk()

  @Ignore("b/134190873")
  @Test
  fun renderLayout() {
    val psiFile = createLayoutFile()
    val facet = rule.module.androidFacet!!
    val layoutRenderer = LayoutRenderer(facet, ::createRenderTaskForTest, ImageFuturesManager())
    val configuration = ConfigurationManager.getOrCreateInstance(facet.module).getConfiguration(psiFile.virtualFile)
    val layoutRender = layoutRenderer.getLayoutRender(psiFile, configuration)
    val image = layoutRender.get(5, TimeUnit.SECONDS)!!

    // Check that we get the correct background color.
    val intArray = IntArray(4)
    assertThat(image.raster.getPixel(100, 100, intArray)).asList().containsExactly(*BACKGROUND_COLOR)

    // Test the size
    assertThat(image.width).isEqualTo(768)
    assertThat(image.height).isEqualTo(1024)
  }

  @Ignore("b/135927007")
  @Test
  fun integrationWithProjectResourcesBrowserViewModel() {
    val latch = CountDownLatch(1)
    val androidFacet = rule.module.androidFacet!!
    val layoutRenderer = LayoutRenderer(androidFacet, ::createRenderTaskForTest, ImageFuturesManager())
    LayoutRenderer.setInstance(androidFacet, layoutRenderer)
    val designAsset = DesignAsset(createLayoutFile().virtualFile, emptyList(), ResourceType.LAYOUT)
    lateinit var resourceExplorerListViewModel: ResourceExplorerListViewModel
    val disposable = Disposer.newDisposable("LayoutRendererTest")
    try {
      resourceExplorerListViewModel = ResourceExplorerListViewModelImpl(
        androidFacet,
        null,
        Mockito.mock(ResourceResolver::class.java),
        FilterOptions.createDefault(),
        ResourceType.DRAWABLE,
        ImageCache.createImageCache(disposable),
        ImageCache.createImageCache(disposable)
      )
      val previewProvider = resourceExplorerListViewModel.assetPreviewManager.getPreviewProvider(ResourceType.LAYOUT)
      val width = 150
      val height = 200
      (previewProvider.getIcon(designAsset, width, height, JLabel(), { latch.countDown() })
        as ImageIcon).image.toBufferedImage()

      assertTrue(latch.await(10, TimeUnit.SECONDS))
      val image = (previewProvider.getIcon(designAsset, width, height, JLabel(), { latch.countDown() })
        as ImageIcon).image.toBufferedImage()

      // Check that we get the correct background color.
      val intArray = IntArray(4)
      assertThat(image.raster.width).isEqualTo(width)
      assertThat(image.raster.height).isEqualTo(height)
      assertThat(image.raster.getPixel(100, 100, intArray)).asList().containsExactly(*BACKGROUND_COLOR)

      // Test the size
      assertThat(image.width).isEqualTo(width)
      assertThat(image.height).isEqualTo(height)
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  private fun createLayoutFile(): XmlFile {
    @Language("XML") val layoutXml = """<?xml version="1.0" encoding="utf-8" ?>
  <LinearLayout
      xmlns:android="http://schemas.android.com/apk/res/android"
      android:layout_width="match_parent"
      android:layout_height="match_parent"
      android:background="#$BACKGROUND_COLOR_HEX_STRING">
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Hello World!"/>
  </LinearLayout>
      """.trimMargin()
    val fileSystem = MockVirtualFileSystem()
    val layoutFile: VirtualFile = fileSystem.file("/layout/layout.xml", layoutXml).refreshAndFindFileByPath("/layout/layout.xml")!!
    val psiFile = AndroidPsiUtils.getPsiFileSafely(rule.project, layoutFile)!!
    return psiFile as XmlFile
  }
}

private fun createRenderTaskForTest(facet: AndroidFacet, xmlFile: XmlFile, configuration: Configuration) =
  StudioRenderService.getInstance(facet.module.project).taskBuilder(facet, configuration)
    .withPsiFile(xmlFile)
    .withQuality(QUALITY)
    .withMaxRenderSize(MAX_RENDER_WIDTH, MAX_RENDER_HEIGHT)
    .disableDecorations()
    .disableSecurityManager()
    .build()

private fun Image.toBufferedImage() = ImageUtil.toBufferedImage(this)