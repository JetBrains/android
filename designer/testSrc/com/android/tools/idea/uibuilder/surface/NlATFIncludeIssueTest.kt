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
package com.android.tools.idea.uibuilder.surface

import com.android.SdkConstants
import com.android.tools.idea.common.error.IssueSource
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.lint.detector.api.Category
import com.intellij.lang.annotation.HighlightSeverity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@RunWith(JUnit4::class)
class NlATFIncludeIssueTest: LayoutTestCase() {

  @Mock
  lateinit var mockSurface: NlDesignSurface

  public override fun setUp() {
    super.setUp()
    MockitoAnnotations.openMocks(this)
  }

  @Test
  fun createIssue() {
    val source: NlComponent = NlScannerLayoutParserTest.createComponentWithInclude()
    val atfIssue = NlATFIncludeIssue(source, mockSurface)

    assertEquals(HighlightSeverity.WARNING, atfIssue.severity)
    assertNotEquals(IssueSource.NONE, atfIssue.source)
    assertEquals(Category.A11Y.name, atfIssue.category)
    assertEquals(2, atfIssue.fixes.count())
  }

  @Test
  fun ignoreIssue() {
    val model = model("linear.xml",
                      component(SdkConstants.LINEAR_LAYOUT)
                        .withBounds(0, 0, 1000, 1000)
                        .id("@id/linear")
                        .matchParentWidth()
                        .matchParentHeight()
                        .children(
                          component(SdkConstants.VIEW_INCLUDE)
                        )).build()

    val source: NlComponent = model.components[0].getChild(0)!!
    val atfIssue = NlATFIncludeIssue(source, mockSurface)

    atfIssue.fixes.filter { it.buttonText == "Ignore" }.forEach {
      it.runnable.run()

      assertEquals(SdkConstants.ATTR_IGNORE_A11Y_LINTS, source.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_IGNORE))
    }
  }

  @Test
  fun goto() {
    val model = model("linear.xml",
                      component(SdkConstants.LINEAR_LAYOUT)
                        .withBounds(0, 0, 1000, 1000)
                        .id("@id/linear")
                        .matchParentWidth()
                        .matchParentHeight()
                        .children(
                          component(SdkConstants.VIEW_INCLUDE)
                        )).build()

    val source: NlComponent = model.components[0].getChild(0)!!
    val atfIssue = NlATFIncludeIssue(source, mockSurface)

    var openLayoutFixFound = false
    atfIssue.fixes.filter { it.buttonText == "Open the layout" }.forEach {
      openLayoutFixFound = true
    }
    assertTrue(openLayoutFixFound)
  }
}