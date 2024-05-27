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

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.test.testutils.TestUtils
import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.properties.PropertySection
import com.android.tools.idea.layoutinspector.properties.ViewNodeAndResourceLookup
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.ui.FileOpenCaptureRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class LambdaParameterItemTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val fileOpenCaptureRule = FileOpenCaptureRule(projectRule)
  private val popupRule = JBPopupRule()

  @get:Rule
  val ruleChain =
    RuleChain.outerRule(projectRule)
      .around(popupRule)
      .around(fileOpenCaptureRule)
      .around(EdtRule())!!

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
    item.link.actionPerformed(mockEvent())
    fileOpenCaptureRule.checkEditor(
      "MyCompose.kt",
      17,
      "modifier = Modifier.padding(20.dp).clickable(onClick = { selectColumn() }),",
    )
    assertThat(popupRule.fakePopupFactory.balloonCount).isEqualTo(0)
  }

  @Test
  fun testLambdaLookupOfUnknownLocation() {
    val item = createParameterItem("MyCompose.kt", 10, 20)

    val disposable = Disposer.newDisposable()
    Disposer.register(projectRule.testRootDisposable, disposable)
    ApplicationManager.getApplication()
      .replaceService(FileDocumentManager::class.java, mock(), disposable)

    item.link.actionPerformed(mockEvent())
    waitForCondition(10, TimeUnit.SECONDS) { popupRule.fakePopupFactory.balloonCount > 0 }
    val balloon = popupRule.fakePopupFactory.getNextBalloon()
    assertThat(balloon.htmlContent).isEqualTo("Could not determine exact source location")

    // FileOpenCaptureRule is using FileDocumentManager:
    Disposer.dispose(disposable)
    fileOpenCaptureRule.checkEditor("MyCompose.kt", 1, "package com.example")
  }

  @Test
  fun testLookupDoesNotExist() {
    val item = createParameterItem("NotExist.kt", 10, 12)
    item.link.actionPerformed(mockEvent())
    waitForCondition(10, TimeUnit.SECONDS) { popupRule.fakePopupFactory.balloonCount > 0 }
    val balloon = popupRule.fakePopupFactory.getNextBalloon()
    assertThat(balloon.htmlContent).isEqualTo("Could not determine source location")
    fileOpenCaptureRule.checkNoNavigation()
  }

  private fun createParameterItem(
    fileName: String,
    startLineNumber: Int,
    endLineNumber: Int,
  ): LambdaParameterItem {
    val lookup =
      object : ViewNodeAndResourceLookup {
        override val resourceLookup = ResourceLookup(projectRule.project)

        override val scope = AndroidCoroutineScope(projectRule.testRootDisposable)

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
      lookup,
    )
  }

  private fun mockEvent(): AnActionEvent {
    val event: AnActionEvent = mock()
    val context = DataContext { dataId ->
      when (dataId) {
        PlatformCoreDataKeys.CONTEXT_COMPONENT.name -> JPanel()
        else -> null
      }
    }
    whenever(event.dataContext).thenReturn(context)
    return event
  }
}
