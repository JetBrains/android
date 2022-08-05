/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual.visuallint.analyzers

import com.android.AndroidXConstants
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.getRoot
import com.android.tools.idea.uibuilder.model.viewInfo
import com.google.common.collect.ImmutableList

class BottomNavAnalyzerTest : LayoutTestCase() {

  fun testBottomNavSmallWidth() {
    val model =
      model("small_width_layout.xml",
            component(AndroidXConstants.CONSTRAINT_LAYOUT.newName())
              .withBounds(0, 0, 800, 1300)
              .withMockView()
              .children(
                component("com.google.android.material.bottomnavigation.BottomNavigationView")
                  .withBounds(0, 0, 800, 100)
                  .withMockView()
              )
      ).build()
    val renderResult = getRenderResultWithRootViews(ImmutableList.of(model.getRoot().viewInfo!!))
    val issues = BottomNavAnalyzer.findIssues(renderResult, model)
    assertEquals(0, issues.size)
  }

  fun testBottomNavLargeWidth() {
    val model =
      model("large_width_layout.xml",
            component(AndroidXConstants.CONSTRAINT_LAYOUT.newName())
              .withBounds(0, 0, 2000, 1300)
              .withMockView()
              .children(
                component("com.google.android.material.bottomnavigation.BottomNavigationView")
                  .withBounds(0, 0, 2000, 100)
                  .withMockView()
              )
      ).build()
    val renderResult = getRenderResultWithRootViews(ImmutableList.of(model.getRoot().viewInfo!!))
    val issues = BottomNavAnalyzer.findIssues(renderResult, model)
    assertEquals(1, issues.size)
    assertEquals("Bottom navigation bar is not recommended for breakpoints over 600dp", issues[0].message)
  }
}