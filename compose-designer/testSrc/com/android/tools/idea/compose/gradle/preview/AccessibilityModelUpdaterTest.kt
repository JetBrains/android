/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.compose.gradle.preview

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.gradle.activateAndWaitForRender
import com.android.tools.idea.compose.preview.ComposePreviewRepresentation
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SimpleComposeAppPaths
import com.android.tools.idea.compose.preview.util.previewElement
import com.android.tools.idea.compose.preview.waitForAllRefreshesToFinish
import com.android.tools.idea.compose.preview.waitForSmartMode
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.UiCheckInstance
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.android.tools.idea.uibuilder.model.w
import com.android.tools.idea.uibuilder.model.y
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AccessibilityModelUpdaterTest {
  @get:Rule val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)

  private val project: Project
    get() = projectRule.project

  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  private lateinit var psiMainFile: PsiFile
  private lateinit var composePreviewRepresentation: ComposePreviewRepresentation
  private lateinit var previewView: TestComposePreviewView
  private lateinit var fakeUi: FakeUi

  @Before
  fun setUp() = runBlocking {
    psiMainFile = getPsiFile(SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path)
    previewView = TestComposePreviewView(fixture.testRootDisposable, project)
    composePreviewRepresentation = createComposePreviewRepresentation(psiMainFile, previewView)

    withContext(AndroidDispatchers.uiThread) {
      fakeUi =
        FakeUi(
          JPanel().apply {
            layout = BorderLayout()
            size = Dimension(1000, 800)
            add(previewView, BorderLayout.CENTER)
          },
          1.0,
          true,
        )
      fakeUi.root.validate()
    }

    waitForSmartMode(project)

    composePreviewRepresentation.activateAndWaitForRender(fakeUi)
    composePreviewRepresentation.waitForAnyPreviewToBeAvailable()
  }

  private fun getPsiFile(path: String): PsiFile {
    val vFile = project.guessProjectDir()!!.findFileByRelativePath(path)!!
    return runReadAction { PsiManager.getInstance(project).findFile(vFile)!! }
  }

  private fun createComposePreviewRepresentation(
    psiFile: PsiFile,
    view: TestComposePreviewView,
  ): ComposePreviewRepresentation {
    val previewRepresentation =
      ComposePreviewRepresentation(psiFile, PreferredVisibility.SPLIT) { _, _, _, _, _, _ -> view }
    Disposer.register(fixture.testRootDisposable, previewRepresentation)

    return previewRepresentation
  }

  @Test
  fun testNlComponentTreeCreation() {
    val twoElementsPreviewModel =
      previewView.mainSurface.models.first { it.modelDisplayName.value == "TwoElementsPreview" }

    val uiCheckElement = twoElementsPreviewModel.dataContext.previewElement()!!

    runBlocking {
      waitForAllRefreshesToFinish(30.seconds)
      val onRefreshCompletable = previewView.getOnRefreshCompletable()
      composePreviewRepresentation.setMode(
        PreviewMode.UiCheck(
          UiCheckInstance(uiCheckElement, isWearPreview = false),
          atfChecksEnabled = true,
        )
      )
      onRefreshCompletable.join()
    }

    val twoElementsPreviewRoot =
      previewView.mainSurface.models
        .first { it.modelDisplayName.value == "Medium Phone - TwoElementsPreview" }
        .treeReader
        .components[0]

    assertNotEquals(-1, twoElementsPreviewRoot.accessibilityId)

    var children = twoElementsPreviewRoot.children
    assertThat(children.size).isGreaterThan(0)
    assertNotEquals(-1, children[0].accessibilityId)
    assertEquals(306, children[0].w)
    assertEquals(189, children[0].y)

    children = children[0].children
    assertEquals(2, children.size)

    val textViewComponent = children[0]
    assertEquals(0, textViewComponent.childCount)
    assertNotEquals(-1, textViewComponent.accessibilityId)
    assertEquals(141, textViewComponent.w)
    assertEquals(189, textViewComponent.y)
    val textViewNavigatable = textViewComponent.navigatable as OpenFileDescriptor
    assertEquals(1158, textViewNavigatable.offset)
    assertEquals("MainActivity.kt", textViewNavigatable.file.name)

    children = children[1].children
    assertEquals(2, children.size)

    val buttonTextViewComponent = children[0]
    assertEquals(0, buttonTextViewComponent.childCount)
    assertNotEquals(-1, buttonTextViewComponent.accessibilityId)
    assertEquals(222, buttonTextViewComponent.w)
    assertEquals(285, buttonTextViewComponent.y)
    val buttonTextViewNavigatable = buttonTextViewComponent.navigatable as OpenFileDescriptor
    assertEquals(1225, buttonTextViewNavigatable.offset)
    assertEquals("MainActivity.kt", buttonTextViewNavigatable.file.name)

    val buttonComponent = children[1]
    assertEquals(0, buttonComponent.childCount)
    assertNotEquals(-1, buttonComponent.accessibilityId)
    assertEquals(306, buttonComponent.w)
    assertEquals(262, buttonComponent.y)
    val buttonNavigatable = buttonComponent.navigatable as OpenFileDescriptor
    assertEquals(1225, buttonNavigatable.offset)
    assertEquals("MainActivity.kt", buttonNavigatable.file.name)
  }
}
