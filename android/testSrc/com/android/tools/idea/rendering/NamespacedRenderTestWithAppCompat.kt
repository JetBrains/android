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

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import junit.framework.TestCase

class NamespacedRenderTestWithAppCompat : AndroidGradleTestCase() {

  override fun setUp() {
    super.setUp()
    loadProject(TestProjectPaths.NAMESPACES_WITH_APPCOMPAT)
    val result = generateSources()
    TestCase.assertTrue(result.isBuildSuccessful)
    RenderTestUtil.beforeRenderTestCase()
  }

  override fun tearDown() {
    try {
      RenderTestUtil.afterRenderTestCase()
    } finally {
      super.tearDown()
    }
  }

  fun testActivityMain() {
    val layout = project.baseDir.findFileByRelativePath("app/src/main/res/layout/activity_main.xml")!!
    RenderTestUtil.checkRendering(myAndroidFacet, layout, getTestDataPath() + "/layouts/namespaced_with_appcompat/activity_main.png")
  }
}
