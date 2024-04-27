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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.compose

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.test.testutils.TestUtils
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.properties.PropertySection
import com.android.tools.idea.layoutinspector.properties.ViewNodeAndResourceLookup
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.ui.FileOpenCaptureRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.BalloonBuilder
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.awt.RelativePoint
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.isNull
import org.mockito.Mockito.anyString
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import java.util.concurrent.Future

@RunsInEdt
class LambdaParameterItemTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val fileOpenCaptureRule = FileOpenCaptureRule(projectRule)

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(fileOpenCaptureRule).around(EdtRule())!!

  @Before
  fun before() {
    val fixture = projectRule.fixture
    fixture.testDataPath =
      TestUtils.resolveWorkspacePath("tools/adt/idea/layout-inspector/testData/compose").toString()
    fixture.copyFileToProject("java/com/example/MyCompose.kt")
  }

  @Test
  fun testLambdaLookup() {
    val item = createParameterItem("MyCompose.kt", 17, 17)
    val balloon = mockBalloonBuilder()
    lateinit var future: Future<*>
    item.futureCaptor = { future = it }
    item.link.actionPerformed(mockEvent())
    future.get()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    verifyNoInteractions(balloon)
    fileOpenCaptureRule.checkEditor(
      "MyCompose.kt",
      17,
      "modifier = Modifier.padding(20.dp).clickable(onClick = { selectColumn() }),"
    )
  }

  @Test
  fun testLambdaLookupOfUnknownLocation() {
    val item = createParameterItem("MyCompose.kt", 10, 20)
    val balloon = mockBalloonBuilder()
    projectRule.replaceService(FileDocumentManager::class.java, mock())
    lateinit var future: Future<*>
    item.futureCaptor = { future = it }
    item.link.actionPerformed(mockEvent())
    future.get()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    verify(balloon).show(any(RelativePoint::class.java), any())
    assertThat(capturedBalloonContent()).isEqualTo("Could not determine exact source location")
    fileOpenCaptureRule.checkEditor("MyCompose.kt", 1, "package com.example")
  }

  @Test
  fun testLookupDoesNotExist() {
    val item = createParameterItem("NotExist.kt", 10, 12)
    val balloon = mockBalloonBuilder()
    lateinit var future: Future<*>
    item.futureCaptor = { future = it }
    item.link.actionPerformed(mockEvent())
    future.get()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    verify(balloon).show(any(RelativePoint::class.java), any())
    assertThat(capturedBalloonContent()).isEqualTo("Could not determine source location")
    fileOpenCaptureRule.checkNoNavigation()
  }

  private fun createParameterItem(
    fileName: String,
    startLineNumber: Int,
    endLineNumber: Int
  ): LambdaParameterItem {
    val lookup =
      object : ViewNodeAndResourceLookup {
        override val resourceLookup = ResourceLookup(projectRule.project)

        override fun get(id: Long): ViewNode? = null

        override val selection: ViewNode? = null
      }
    return LambdaParameterItem(
      "modifier",
      PropertySection.PARAMETERS,
      -99,
      1,
      -1,
      "com.example",
      fileName,
      "1",
      "",
      startLineNumber,
      endLineNumber,
      lookup
    )
  }

  private fun mockBalloonBuilder(): Balloon {
    projectRule.replaceService(JBPopupFactory::class.java, mock())
    val factory = JBPopupFactory.getInstance()
    val builder: BalloonBuilder = mock()
    val balloon: Balloon = mock()
    whenever(factory.createHtmlTextBalloonBuilder(anyString(), any(), any(), isNull()))
      .thenReturn(builder)
    whenever(factory.guessBestPopupLocation(any(DataContext::class.java))).thenReturn(mock())
    whenever(builder.setBorderColor(any())).thenReturn(builder)
    whenever(builder.setBorderInsets(any())).thenReturn(builder)
    whenever(builder.setFadeoutTime(any())).thenReturn(builder)
    whenever(builder.createBalloon()).thenReturn(balloon)
    return balloon
  }

  private fun capturedBalloonContent(): String {
    val factory = JBPopupFactory.getInstance()
    val content = ArgumentCaptor.forClass(String::class.java)
    verify(factory).createHtmlTextBalloonBuilder(content.capture(), any(), any(), isNull())
    return content.value
  }

  private fun mockEvent(): AnActionEvent {
    val event: AnActionEvent = mock()
    val context: DataContext = mock()
    whenever(event.dataContext).thenReturn(context)
    return event
  }
}
