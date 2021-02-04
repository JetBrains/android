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
package com.android.tools.idea.layoutinspector.properties

import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.resource.SourceLocation
import com.android.tools.idea.layoutinspector.statistics.SessionStatistics
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.pom.Navigatable
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.UIUtil
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

@RunsInEdt
class LambdaPropertyItemTest {
  @get:Rule
  val ruleChain = RuleChain.outerRule(AndroidProjectRule.inMemory()).around(EdtRule())!!

  @Test
  fun testNavigate() {
    val lookup: ViewNodeAndResourceLookup = mock()
    val stats: SessionStatistics = mock()
    val selection: ComposeViewNode = mock()
    val resourceLookup: ResourceLookup = mock()
    val navigatable: Navigatable = mock()
    val location = SourceLocation("Text.kt:34", navigatable)
    `when`(lookup.resourceLookup).thenReturn(resourceLookup)
    `when`(lookup.stats).thenReturn(stats)
    `when`(lookup.selection).thenReturn(selection)
    `when`(resourceLookup.findLambdaLocation("com.example", "Text.kt", "f1$1", "", 34, 34)).thenReturn(location)
    `when`(navigatable.canNavigate()).thenReturn(true)
    val property = LambdaPropertyItem("onText", -2, "com.example", "Text.kt", "f1$1", "", 34, 34, lookup)
    val link = property.link
    assertThat(link.templateText).isEqualTo("Text.kt:34")

    link.actionPerformed(mock())
    UIUtil.dispatchAllInvocationEvents() // wait for invokeLater

    verify(navigatable).navigate(true)
    verify(stats).gotoSourceFromPropertyValue(eq(selection))
    assertThat(link.templateText).isEqualTo("Text.kt:34")
    assertThat(link.templatePresentation.isEnabled).isTrue()
  }

  @Test
  fun testNavigateToNowhere() {
    val lookup: ViewNodeAndResourceLookup = mock()
    val resourceLookup: ResourceLookup = mock()
    `when`(lookup.resourceLookup).thenReturn(resourceLookup)
    val property = LambdaPropertyItem("onText", -2, "com.example", "Text.kt", "f1$1", "", 34, 34, lookup)
    val link = property.link
    assertThat(link.templateText).isEqualTo("Text.kt:34")

    link.actionPerformed(mock())
    UIUtil.dispatchAllInvocationEvents() // wait for invokeLater

    assertThat(link.templateText).isEqualTo("Text.kt:unknown")
    assertThat(link.templatePresentation.isEnabled).isFalse()
  }
}
