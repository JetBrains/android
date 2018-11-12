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
package com.android.tools.idea.resourceExplorer.plugin

import com.android.resources.ResourceType
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.viewmodel.ProjectResourcesBrowserViewModel
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.mock.MockVirtualFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.xml.XmlFile
import org.intellij.lang.annotations.Language
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Rule
import org.junit.Test
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.util.concurrent.TimeUnit


private val BACKGROUND_COLOR = arrayOf(0xFA, 0xD1, 0x3A, 0xFF)
private const val BACKGROUND_COLOR_HEX_STRING = "FAD13A"

class LayoutRendererTest {

  @get:Rule
  val rule = AndroidProjectRule.withSdk()

  @Test
  fun renderLayout() {
    val psiFile = createLayoutFile()
    val facet = rule.module.androidFacet!!
    val layoutRenderer = LayoutRenderer(facet, ::createRenderTaskForTest)
    val configuration = ConfigurationManager.getOrCreateInstance(facet).getConfiguration(psiFile.virtualFile)
    val layoutRender = layoutRenderer.getLayoutRender(psiFile, configuration)
    val image = layoutRender.get(5, TimeUnit.SECONDS)!!

    // Check that we get the correct background color.
    val intArray = IntArray(4)
    assertThat(image.raster.getPixel(100, 100, intArray)).asList().containsExactly(*BACKGROUND_COLOR)

    // Test the size
    assertThat(image.width).isEqualTo(768)
    assertThat(image.height).isEqualTo(1024)

    // Check that the returned image is the same, which means that the image was cached
    assertThat(layoutRenderer.getLayoutRender(psiFile, configuration).get(5, TimeUnit.SECONDS)).isSameAs(image)
  }

  @Test
  fun integrationWithProjectResourcesBrowserViewModel() {
    val androidFacet = rule.module.androidFacet!!
    val layoutRenderer = LayoutRenderer(androidFacet, ::createRenderTaskForTest)
    LayoutRenderer.setInstance(androidFacet, layoutRenderer)
    val designAsset = DesignAsset(createLayoutFile().virtualFile, emptyList(), ResourceType.LAYOUT)

    val image = ProjectResourcesBrowserViewModel(androidFacet)
      .getPreview(Dimension(), designAsset).get(5, TimeUnit.SECONDS)!! as BufferedImage

    // Check that we get the correct background color.
    val intArray = IntArray(4)
    assertThat(image.raster.getPixel(100, 100, intArray)).asList().containsExactly(*BACKGROUND_COLOR)

    // Test the size
    assertThat(image.width).isEqualTo(768)
    assertThat(image.height).isEqualTo(1024)
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

fun createRenderTaskForTest(facet: AndroidFacet, xmlFile: XmlFile, configuration: Configuration) =
  RenderService.getInstance(facet.module.project).taskBuilder(facet, configuration)
    .withPsiFile(xmlFile)
    .withDownscaleFactor(DOWNSCALE_FACTOR)
    .withMaxRenderSize(MAX_RENDER_WIDTH, MAX_RENDER_HEIGHT)
    .disableDecorations()
    .disableSecurityManager()
    .build()
