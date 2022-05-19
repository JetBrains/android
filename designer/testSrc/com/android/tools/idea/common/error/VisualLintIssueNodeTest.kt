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
package com.android.tools.idea.common.error

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue
import com.android.utils.HtmlBuilder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VisualLintIssueNodeTest {
  @JvmField
  @Rule
  val rule = AndroidProjectRule.inMemory().onEdt()

  @RunsInEdt
  @Test
  fun testHasNavigatable() {
    val model = NlModelBuilderUtil.model(
      rule.projectRule,
      "layout",
      "layout.xml",
      ComponentDescriptor(SdkConstants.FRAME_LAYOUT)
        .withBounds(0, 0, 1000, 1000)
        .matchParentWidth()
        .matchParentHeight()
        .children(ComponentDescriptor(SdkConstants.TEXT_VIEW)
                    .width("100dp")
                    .height("20dp")
                    .children(ComponentDescriptor(SdkConstants.TEXT_VIEW)
                                .width("100dp")
                                .height("20dp")
                                .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN, "-10dp"))
        )
    ).build()

    val issue = createTestVisualLintRenderIssue(VisualLintErrorType.BOUNDS, model.components.first().children)
    val node = VisualLintIssueNode(issue, CommonIssueTestParentNode(rule.projectRule.project))
    assertNotNull(node.getNavigatable())
  }

  @RunsInEdt
  @Test
  fun testNoNavigatableWhenSuppressed() {
    val errorType = VisualLintErrorType.BOUNDS
    val model = NlModelBuilderUtil.model(
      rule.projectRule,
      "layout",
      "layout.xml",
      ComponentDescriptor(SdkConstants.FRAME_LAYOUT)
        .withBounds(0, 0, 1000, 1000)
        .matchParentWidth()
        .matchParentHeight()
        .children(ComponentDescriptor(SdkConstants.TEXT_VIEW)
                    .width("100dp")
                    .height("20dp")
                    .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN, "-10dp")
                    .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_IGNORE, errorType.ignoredAttributeValue)
        )
    ).build()

    val issue = createTestVisualLintRenderIssue(errorType, model.components.first().children)
    val node = VisualLintIssueNode(issue, CommonIssueTestParentNode(rule.projectRule.project))
    assertNull(node.getNavigatable())
  }
}

private fun createTestVisualLintRenderIssue(type: VisualLintErrorType, components: MutableList<NlComponent>) : VisualLintRenderIssue {
  return VisualLintRenderIssue.builder().model(components.first().model)
    .summary("")
    .severity(HighlightSeverity.WARNING)
    .contentDescriptionProvider { HtmlBuilder() }
    .model(components.first().model)
    .components(components)
    .type(type)
    .build()
}
