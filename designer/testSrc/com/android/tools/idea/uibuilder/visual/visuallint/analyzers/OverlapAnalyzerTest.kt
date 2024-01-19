/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.SdkConstants
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.getRoot
import com.android.tools.idea.uibuilder.model.viewInfo
import com.google.common.collect.ImmutableList

class OverlapAnalyzerTest : LayoutTestCase() {

  fun testTextHiddenIndex() {
    // Text hidden because image is defined after text
    val model =
      model(
          "is_hidden.xml",
          component(AndroidXConstants.CONSTRAINT_LAYOUT.newName())
            .withBounds(0, 0, 200, 200)
            .withMockView()
            .children(
              component(SdkConstants.TEXT_VIEW)
                .withBounds(0, 0, 200, 200)
                .withAttribute(ANDROID_URI, ATTR_ID, "@id/text_view")
                .withMockView(android.widget.TextView::class.java),
              component(SdkConstants.IMAGE_VIEW)
                .withAttribute(ANDROID_URI, ATTR_ID, "@id/image_view")
                .withBounds(0, 0, 200, 200)
                .withMockView(),
            ),
        )
        .build()
    val renderResult = getRenderResultWithRootViews(ImmutableList.of(model.getRoot().viewInfo!!))
    val issues = OverlapAnalyzer.findIssues(renderResult, model)
    assertEquals(1, issues.size)
    assertEquals("text_view <TextView> is covered by image_view <ImageView>", issues[0].message)
  }

  fun testTextShownIndex() {
    // Text shown because image is defined before text
    val model =
      model(
          "is_hidden.xml",
          component(AndroidXConstants.CONSTRAINT_LAYOUT.newName())
            .withBounds(0, 0, 200, 200)
            .withMockView()
            .children(
              component(SdkConstants.IMAGE_VIEW).withBounds(0, 0, 200, 200).withMockView(),
              component(SdkConstants.TEXT_VIEW)
                .withBounds(0, 0, 200, 200)
                .withMockView(android.widget.TextView::class.java),
            ),
        )
        .build()
    val renderResult = getRenderResultWithRootViews(ImmutableList.of(model.getRoot().viewInfo!!))
    val issues = OverlapAnalyzer.findIssues(renderResult, model)
    assertEquals(0, issues.size)
  }

  fun testTextHiddenElevation() {
    // Text hidden because image has higher elevation than text, even tho text view is defined
    // later.
    val model =
      model(
          "is_hidden.xml",
          component(AndroidXConstants.CONSTRAINT_LAYOUT.newName())
            .withBounds(0, 0, 200, 200)
            .withMockView()
            .children(
              component(SdkConstants.IMAGE_VIEW)
                .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ELEVATION, "20dp")
                .withBounds(0, 0, 200, 200)
                .withMockView(),
              component(SdkConstants.TEXT_VIEW)
                .withBounds(0, 0, 200, 200)
                .withAttribute(ANDROID_URI, ATTR_ID, "@+id/text_view")
                .withMockView(android.widget.TextView::class.java),
            ),
        )
        .build()
    val renderResult = getRenderResultWithRootViews(ImmutableList.of(model.getRoot().viewInfo!!))
    val issues = OverlapAnalyzer.findIssues(renderResult, model)
    assertEquals(1, issues.size)
    assertEquals("text_view <TextView> is covered by ImageView", issues[0].message)
  }

  fun testTextShownElevation() {
    // Text shown because text has higher elevation
    val model =
      model(
          "is_hidden.xml",
          component(AndroidXConstants.CONSTRAINT_LAYOUT.newName())
            .withBounds(0, 0, 200, 200)
            .withMockView()
            .children(
              component(SdkConstants.TEXT_VIEW)
                .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ELEVATION, "25dp")
                .withBounds(0, 0, 200, 200)
                .withMockView(android.widget.TextView::class.java),
              component(SdkConstants.IMAGE_VIEW)
                .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ELEVATION, "20dp")
                .withBounds(0, 0, 200, 200)
                .withMockView(),
            ),
        )
        .build()
    val renderResult = getRenderResultWithRootViews(ImmutableList.of(model.getRoot().viewInfo!!))
    val issues = OverlapAnalyzer.findIssues(renderResult, model)
    assertEquals(0, issues.size)
  }

  fun testTextHidden60Percent() {
    // Text hidden because image is defined after text
    val model =
      model(
          "is_hidden.xml",
          component(AndroidXConstants.CONSTRAINT_LAYOUT.newName())
            .withBounds(0, 0, 200, 200)
            .withMockView()
            .children(
              component(SdkConstants.TEXT_VIEW)
                .withBounds(0, 0, 100, 100)
                .withMockView(android.widget.TextView::class.java),
              component(SdkConstants.IMAGE_VIEW).withBounds(0, 0, 60, 100).withMockView(),
            ),
        )
        .build()
    val renderResult = getRenderResultWithRootViews(ImmutableList.of(model.getRoot().viewInfo!!))
    val issues = OverlapAnalyzer.findIssues(renderResult, model)
    assertEquals(1, issues.size)
  }

  fun testTextHidden40Percent() {
    // Text hidden because image is defined after text
    val model =
      model(
          "is_hidden.xml",
          component(AndroidXConstants.CONSTRAINT_LAYOUT.newName())
            .withBounds(0, 0, 200, 200)
            .withMockView()
            .children(
              component(SdkConstants.TEXT_VIEW)
                .withBounds(0, 0, 100, 100)
                .withMockView(android.widget.TextView::class.java),
              component(SdkConstants.IMAGE_VIEW).withBounds(0, 0, 40, 100).withMockView(),
            ),
        )
        .build()
    val renderResult = getRenderResultWithRootViews(ImmutableList.of(model.getRoot().viewInfo!!))
    val issues = OverlapAnalyzer.findIssues(renderResult, model)
    assertEquals(0, issues.size)
  }

  fun testNoOverlap() {
    val model =
      model(
          "no_overlap.xml",
          component(AndroidXConstants.CONSTRAINT_LAYOUT.newName())
            .withBounds(0, 0, 200, 200)
            .withMockView()
            .children(
              component(SdkConstants.TEXT_VIEW)
                .withBounds(0, 0, 20, 20)
                .withMockView(android.widget.TextView::class.java),
              component(SdkConstants.IMAGE_VIEW).withBounds(160, 160, 30, 30).withMockView(),
            ),
        )
        .build()
    val renderResult = getRenderResultWithRootViews(ImmutableList.of(model.getRoot().viewInfo!!))
    val issues = OverlapAnalyzer.findIssues(renderResult, model)
    assertEquals(0, issues.size)
  }
}
