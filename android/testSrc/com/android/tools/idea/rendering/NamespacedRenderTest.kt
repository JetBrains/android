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
package com.android.tools.idea.rendering

import com.android.tools.adtui.imagediff.ImageDiffUtil
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

class NamespacedRenderTest : AndroidGradleTestCase() {
  override fun setUp() {
    super.setUp()
    RenderTestUtil.beforeRenderTestCase()
    loadProject(TestProjectPaths.NAMESPACES)
  }

  override fun tearDown() {
    try {
      RenderTestUtil.afterRenderTestCase()
    } finally {
      super.tearDown()
    }
  }

  fun testSimpleStrings() {
    val layout = project.baseDir.findFileByRelativePath("app/src/main/res/layout/simple_strings.xml")!!
    val configuration = RenderTestUtil.getConfiguration(myModules.appModule, layout)
    assertRenderSimilar(layout, configuration, "/layouts/namespaced/simple_strings.png")
  }

  fun testAttrsFromLib() {
    val layout = project.baseDir.findFileByRelativePath("app/src/main/res/layout/attrs_from_lib.xml")!!
    val configuration = RenderTestUtil.getConfiguration(myModules.appModule, layout)
    configuration.setTheme("@style/AttrsFromLib")
    assertRenderSimilar(layout, configuration, "/layouts/namespaced/attrs_from_lib.png")
  }

  fun testParentFromLib() {
    val layout = project.baseDir.findFileByRelativePath("app/src/main/res/layout/parent_from_lib.xml")!!
    val configuration = RenderTestUtil.getConfiguration(myModules.appModule, layout)
    configuration.setTheme("@style/ParentFromLib")
    assertRenderSimilar(layout, configuration, "/layouts/namespaced/parent_from_lib.png")
  }

  private fun assertRenderSimilar(
    layout: VirtualFile,
    configuration: Configuration,
    goldenImage: String
  ) {
    val task = RenderTestUtil.createRenderTask(myModules.appModule, layout, configuration)
    val result = task.render().get().renderedImage.copy

    ImageDiffUtil.assertImageSimilar(
      File(getTestDataPath() + goldenImage),
      result,
      0.5
    )
  }
}
