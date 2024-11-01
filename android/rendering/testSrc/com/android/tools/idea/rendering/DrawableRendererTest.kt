/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.testutils.ImageDiffUtil.assertImageSimilar
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.Test
import java.awt.Color
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.util.concurrent.TimeUnit

class DrawableRendererTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withSdk()

  /**
   * This exercises the same path as used by the Resource Manager to render previews.
   * Regression test for b/364904755.
   */
  @Test
  fun testRenderDrawable() {
    @Language("xml") val drawableContent = """
      <?xml version="1.0" encoding="utf-8"?>
      <shape xmlns:android="http://schemas.android.com/apk/res/android"
          android:shape="rectangle"
          android:tint="#FF0000">
      </shape>
      """.trimIndent()
    val file: VirtualFile = LightVirtualFile("test_resource.xml", drawableContent)
    val drawableRenderer = DrawableRenderer(
      projectRule.module.androidFacet!!,
      file
    )
    Disposer.register(projectRule.testRootDisposable, drawableRenderer)
    val image = drawableRenderer.renderDrawable(drawableContent, Dimension(100, 100)).get(60, TimeUnit.SECONDS)
    val goldenImage = BufferedImage(image.width, image.height, image.type).also {
      val g = it.graphics
      g.color = Color.RED
      g.fillRect(0, 0, it.width, it.height)
    }
    assertImageSimilar("drawable", goldenImage, image, .0, 0)
  }
}