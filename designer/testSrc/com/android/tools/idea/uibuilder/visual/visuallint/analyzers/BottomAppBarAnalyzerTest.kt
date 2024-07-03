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
import com.android.tools.idea.rendering.RenderTestUtil
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.getRoot
import com.android.tools.idea.uibuilder.model.viewInfo
import com.google.common.collect.ImmutableList

class BottomAppBarAnalyzerTest : LayoutTestCase() {

  fun testBottomAppBarPhone() {
    val model =
      model(
          "phone_appbar_layout.xml",
          component(AndroidXConstants.CONSTRAINT_LAYOUT.newName())
            .withBounds(0, 0, 200, 200)
            .withMockView()
            .children(
              component("com.google.android.material.bottomappbar.BottomAppBar")
                .matchParentWidth()
                .height("40dp")
                .withMockView()
            ),
        )
        .build()
    val renderResult = getRenderResultWithRootViews(ImmutableList.of(model.getRoot().viewInfo!!))
    val issues = BottomAppBarAnalyzer.findIssues(renderResult, model)
    assertEquals(0, issues.size)
  }

  fun testBottomAppBarTablet() {
    val model =
      model(
          "tablet_appbar_layout.xml",
          component(AndroidXConstants.CONSTRAINT_LAYOUT.newName())
            .withBounds(0, 0, 200, 200)
            .withMockView()
            .children(
              component("com.google.android.material.bottomappbar.BottomAppBar")
                .matchParentWidth()
                .height("40dp")
                .withMockView()
            ),
        )
        .build()
    val tabletConfig = RenderTestUtil.getConfiguration(myModule, model.virtualFile, "Nexus 9")
    model.setConfiguration(tabletConfig)
    val renderResult = getRenderResultWithRootViews(ImmutableList.of(model.getRoot().viewInfo!!))
    val issues = BottomAppBarAnalyzer.findIssues(renderResult, model)
    assertEquals(1, issues.size)
    assertEquals("Bottom app bars are only recommended for compact screens", issues[0].message)
  }
}
