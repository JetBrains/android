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

import com.android.flags.junit.FlagRule
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.gradle.activateAndWaitForRender
import com.android.tools.idea.compose.preview.ComposePreviewRepresentation
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SimpleComposeAppPaths
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.android.tools.idea.uibuilder.model.w
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AccessibilityModelUpdaterTest {
  @get:Rule val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)
  @get:Rule val atfForComposeFlag = FlagRule(StudioFlags.NELE_ATF_FOR_COMPOSE, true)

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
          true
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
    view: TestComposePreviewView
  ): ComposePreviewRepresentation {
    val previewRepresentation =
      ComposePreviewRepresentation(psiFile, PreferredVisibility.SPLIT) { _, _, _, _, _, _ -> view }
    Disposer.register(fixture.testRootDisposable, previewRepresentation)
    return previewRepresentation
  }

  /** Suspendable version of [DumbService.waitForSmartMode]. */
  private suspend fun waitForSmartMode(project: Project) {
    val dumbService = DumbService.getInstance(project)
    while (dumbService.isDumb) delay(500)
  }

  @Test
  fun testNlComponentTreeCreation() {
    val twoElementsPreviewRoot =
      previewView.mainSurface.models
        .first { it.modelDisplayName == "TwoElementsPreview" }
        .components[0]
    assertNotEquals(-1, twoElementsPreviewRoot.accessibilityId)

    var children = twoElementsPreviewRoot.children
    assertEquals(1, children.size)
    assertNotEquals(-1, children[0].accessibilityId)
    assertEquals(323, children[0].w)

    children = children[0].children
    assertEquals(2, children.size)

    val textViewComponent = children[0]
    assertEquals(0, textViewComponent.childCount)
    assertNotEquals(-1, textViewComponent.accessibilityId)
    assertEquals(148, textViewComponent.w)

    children = children[1].children
    assertEquals(2, children.size)

    val buttonTextViewComponent = children[0]
    assertEquals(0, buttonTextViewComponent.childCount)
    assertNotEquals(-1, buttonTextViewComponent.accessibilityId)
    assertEquals(235, buttonTextViewComponent.w)

    val buttonComponent = children[1]
    assertEquals(0, buttonComponent.childCount)
    assertNotEquals(-1, buttonComponent.accessibilityId)
    assertEquals(323, buttonComponent.w)
  }
}
