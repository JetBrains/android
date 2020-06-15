/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.model

import com.android.testutils.TestUtils
import com.android.tools.idea.SIMPLE_DESIGN_PROJECT_PATH
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.intellij.openapi.application.ReadAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ClassResolutionUtilsTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath = TestUtils.getWorkspaceFile("tools/adt/idea/designer/testData").path
  }

  @Test
  fun checkFindClassesForViewTag() {
    projectRule.fixture.testDataPath = TestUtils.getWorkspaceFile("tools/adt/idea/designer/testData").path
    projectRule.load(SIMPLE_DESIGN_PROJECT_PATH)
    projectRule.requestSyncAndWait()

    ReadAction.run<Throwable> {
      val textViewClass = findClassForViewTag(projectRule.project, "TextView")
      assertNotNull(textViewClass)
      assertEquals(textViewClass, findClassesForViewTag(projectRule.project, "TextView").single())
      assertEquals(textViewClass, findClassesForViewTag(projectRule.project, "android.widget.TextView").single())

      val customViewClass = findClassForViewTag(projectRule.project, "google.simpleapplication.CustomView")
      assertNotNull(customViewClass)
      assertEquals(customViewClass, findClassesForViewTag(projectRule.project, "google.simpleapplication.CustomView").single())
    }
  }
}