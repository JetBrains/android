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

import com.android.tools.idea.rendering.RenderTestUtil.checkRendering
import com.android.tools.idea.rendering.RenderTestUtil.withRenderTask
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.project.Project
import com.intellij.testFramework.IndexingTestUtil.Companion.waitUntilIndexesAreReady
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NamespacedRenderTest {
  @get:Rule val projectRule = AndroidGradleProjectRule()

  @get:Rule val renderRule = RenderTestRule()

  private val project: Project
    get() = projectRule.project

  private val facet: AndroidFacet
    get() = projectRule.findGradleModule(":app")!!.androidFacet!!

  @Before
  fun setUp() {
    projectRule.loadProject(TestProjectPaths.NAMESPACES)
    waitUntilIndexesAreReady(project)
  }

  @Test
  fun testSimpleStrings() {
    checkRendering(
      facet,
      project.baseDir.findFileByRelativePath("app/src/main/res/layout/simple_strings.xml")!!,
      projectRule.resolveTestDataPath("/layouts/namespaced/simple_strings.png").path,
    )
  }

  @Test
  fun testAttrsFromLib() {
    withRenderTask(
      facet,
      project.baseDir.findFileByRelativePath("app/src/main/res/layout/attrs_from_lib.xml")!!,
      "@style/AttrsFromLib",
    ) {
      checkRendering(
        it,
        AndroidTestBase.getTestDataPath() + "/layouts/namespaced/attrs_from_lib.png",
      )
    }
  }

  @Test
  fun testParentFromLib() {
    withRenderTask(
      facet,
      project.baseDir.findFileByRelativePath("app/src/main/res/layout/parent_from_lib.xml")!!,
      "@style/ParentFromLib",
    ) {
      checkRendering(
        it,
        AndroidTestBase.getTestDataPath() + "/layouts/namespaced/parent_from_lib.png",
      )
    }
  }
}
