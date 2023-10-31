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

import com.android.testutils.ImageDiffUtil
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.getPluginsResourcesDirectory
import com.android.tools.idea.ui.resourcemanager.pathToVirtualFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.registerServiceInstance
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Dimension
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SVGDesignAssetRendererTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    ApplicationManager.getApplication().registerServiceInstance(SVGAssetRenderer::class.java, SVGAssetRenderer())
  }

  @Test
  fun isFileSupported() {
    val viewer = svgResourceViewer()
    val svgFile = projectRule.fixture.tempDirFixture.createFile("svg.svg")
    val otherFile = projectRule.fixture.tempDirFixture.createFile("png.png")
    assertTrue { viewer.isFileSupported(svgFile) }
    assertFalse { viewer.isFileSupported(otherFile) }
  }

  @Test
  fun getImage() {
    val viewer = svgResourceViewer()
    val path = getPluginsResourcesDirectory() + "/svg-sample.svg"
    val file = pathToVirtualFile(path)
    val image = viewer.getImage(file, projectRule.module, Dimension(50, 50)).get()
    ImageDiffUtil.assertImageSimilar(
      "svg-sample",
      ImageIO.read(File(getPluginsResourcesDirectory() + "/svg-sample-50.png")),
      image!!,
      5.0
    )
  }

  private fun svgResourceViewer(): SVGAssetRenderer {
    val viewer = DesignAssetRendererManager.getInstance().getViewer(SVGAssetRenderer::class.java)
    assertNotNull(viewer)
    return viewer!!
  }
}