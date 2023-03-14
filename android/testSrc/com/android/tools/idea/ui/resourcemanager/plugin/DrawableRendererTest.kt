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
import com.android.tools.idea.res.getSourceAsVirtualFile
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.getPNGFile
import com.android.tools.idea.ui.resourcemanager.getPluginsResourcesDirectory
import com.android.tools.idea.ui.resourcemanager.getStateList
import com.android.tools.idea.ui.resourcemanager.getTestDataDirectory
import com.android.tools.idea.ui.resourcemanager.pathToVirtualFile
import com.intellij.configurationStore.runInAllowSaveMode
import com.intellij.util.ui.ImageUtil
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Dimension
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrawableRendererTest {

  @Suppress("MemberVisibilityCanBePrivate")
  @get:Rule
  val projectRule = AndroidProjectRule.withSdk()

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath = getTestDataDirectory()
  }

  @Test
  fun isFileSupported() {
    val viewer = DrawableAssetRenderer()
    val path = getPluginsResourcesDirectory() + "/vector_drawable.xml"
    val file = pathToVirtualFile(path)
    val otherFile = projectRule.fixture.tempDirFixture.createFile("png.png")
    assertTrue { viewer.isFileSupported(file) }
    assertFalse { viewer.isFileSupported(otherFile) }
  }

  @Test
  fun renderSelector() {
    val stateList = projectRule.getStateList()
    val virtualFile = stateList.getSourceAsVirtualFile()!!

    saveProjectOnDisk()
    val viewer = DrawableAssetRenderer()
    assertTrue { viewer.isFileSupported(virtualFile) }
    val image = checkNotNull(viewer.getImage(virtualFile, projectRule.module, Dimension(32, 32)).get(60, TimeUnit.SECONDS))
    ImageDiffUtil.assertImageSimilar(Path.of(getPluginsResourcesDirectory (), "golden.png"), ImageUtil.toBufferedImage(image), 0.05)
  }

  private fun saveProjectOnDisk() {
    runInAllowSaveMode { projectRule.project.save() }
  }
}