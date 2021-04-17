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

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.resource.SourceLocation
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.BalloonBuilder
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.pom.Navigatable
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.UIUtil
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyString
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

@RunsInEdt
class LambdaPropertyItemTest {
  private val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  @Test
  fun testNavigate() {
    val navigatable: Navigatable = mock()
    val location = SourceLocation("Text.kt:34", navigatable)
    `when`(navigatable.canNavigate()).thenReturn(true)
    val property = createProperty(location)
    val link = property.link
    val selection = property.lookup.selection
    val balloon = mockBalloonBuilder()
    val stats: SessionStatistics = mock()
    val inspector = LayoutInspector(mock(), model {}, stats = stats)

    assertThat(link.templateText).isEqualTo("Text.kt:34")

    link.actionPerformed(event(inspector))
    UIUtil.dispatchAllInvocationEvents() // wait for invokeLater

    verify(navigatable).navigate(true)
    verify(inspector.stats).gotoSourceFromPropertyValue(eq(selection))
    verifyNoInteractions(balloon)
    assertThat(link.templateText).isEqualTo("Text.kt:34")
    assertThat(link.templatePresentation.isEnabled).isTrue()
  }

  @Test
  fun testNavigateToNowhere() {
    val location = SourceLocation("Text.kt:unknown", null)
    val property = createProperty(location)
    val link = property.link
    val balloon = mockBalloonBuilder()
    val stats: SessionStatistics = mock()
    val inspector = LayoutInspector(mock(), model {}, stats = stats)
    link.actionPerformed(event(inspector))
    UIUtil.dispatchAllInvocationEvents() // wait for invokeLater

    assertThat(link.templateText).isEqualTo("Text.kt:unknown")
    verifyNoInteractions(inspector.stats)
    verify(balloon).show(any(RelativePoint::class.java), any())
  }

  @Test
  fun testNavigateToFile() {
    val navigatable: Navigatable = mock()
    val location = SourceLocation("Text.kt:unknown", navigatable)
    `when`(navigatable.canNavigate()).thenReturn(true)
    val property = createProperty(location)
    val link = property.link
    val selection = property.lookup.selection

    assertThat(link.templateText).isEqualTo("Text.kt:34")

    val balloon = mockBalloonBuilder()
    val stats: SessionStatistics = mock()
    val inspector = LayoutInspector(mock(), model {}, stats = stats)
    link.actionPerformed(event(inspector))
    UIUtil.dispatchAllInvocationEvents() // wait for invokeLater

    verify(navigatable).navigate(true)
    verify(inspector.stats).gotoSourceFromPropertyValue(eq(selection))
    assertThat(link.templateText).isEqualTo("Text.kt:unknown")
    verify(balloon).show(any(RelativePoint::class.java), any())
  }

  private fun mockBalloonBuilder(): Balloon {
    projectRule.replaceService(JBPopupFactory::class.java, mock())
    val factory = JBPopupFactory.getInstance()
    val builder: BalloonBuilder = mock()
    val balloon: Balloon = mock()
    `when`(factory.createHtmlTextBalloonBuilder(anyString(), any(), any(), isNull())).thenReturn(builder)
    `when`(factory.guessBestPopupLocation(any(DataContext::class.java))).thenReturn(mock())
    `when`(builder.setBorderColor(any())).thenReturn(builder)
    `when`(builder.setBorderInsets(any())).thenReturn(builder)
    `when`(builder.setFadeoutTime(any())).thenReturn(builder)
    `when`(builder.createBalloon()).thenReturn(balloon)
    return balloon
  }

  private fun createProperty(location: SourceLocation): LambdaPropertyItem {
    val lookup: ViewNodeAndResourceLookup = mock()
    val selection: ComposeViewNode = mock()
    val resourceLookup: ResourceLookup = mock()
    `when`(lookup.resourceLookup).thenReturn(resourceLookup)
    `when`(lookup.selection).thenReturn(selection)
    `when`(resourceLookup.findLambdaLocation("com.example", "Text.kt", "f1$1", "", 34, 34)).thenReturn(location)
    return LambdaPropertyItem("onText", -2, "com.example", "Text.kt", "f1$1", "", 34, 34, lookup)
  }

  private fun event(inspector: LayoutInspector): AnActionEvent {
    val event: AnActionEvent = mock()
    `when`(event.dataContext).thenReturn(mock())
    `when`(event.getData(eq(LAYOUT_INSPECTOR_DATA_KEY))).thenReturn(inspector)
    return event
  }
}
