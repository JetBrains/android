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
package com.android.tools.idea.preview.views

import com.android.tools.adtui.instructions.HyperlinkInstruction
import com.android.tools.adtui.instructions.InstructionsPanel
import com.android.tools.adtui.instructions.NewRowInstruction
import com.android.tools.adtui.instructions.TextInstruction
import com.android.tools.adtui.stdui.UrlData
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.common.editor.ActionManager
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private fun InstructionsPanel.toDisplayText(): String =
  (0 until componentCount)
    .flatMap { getRenderInstructionsForComponent(it) }
    .mapNotNull {
      when (it) {
        is TextInstruction -> it.text
        is NewRowInstruction -> "\n"
        is HyperlinkInstruction -> "[${it.displayText}]"
        else -> null
      }
    }
    .joinToString("")

private fun JComponent?.isVisible() = this?.isShowing == true

class CommonNlDesignSurfacePreviewViewTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private val project: Project
    get() = projectRule.project
  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  private lateinit var fakeUi: FakeUi

  private lateinit var previewView: CommonNlDesignSurfacePreviewView

  @RunsInEdt
  @Before
  fun setUp() =
    runBlocking(uiThread) {
      val surfaceBuilder = NlDesignSurface.builder(project, fixture.testRootDisposable)

      previewView = CommonNlDesignSurfacePreviewView(project, surfaceBuilder, fixture.testRootDisposable)

      fakeUi =
        FakeUi(
          JPanel().apply {
            layout = BorderLayout()
            size = Dimension(1000, 800)
            add(previewView.component, BorderLayout.CENTER)
          },
          1.0,
          true
        )
      fakeUi.root.validate()
    }

  @Test
  fun testShowLoading() =
    runBlocking(workerThread) {
      withContext(uiThread) {
        previewView.showLoadingMessage("Loading foo")
        fakeUi.root.validate()
      }

      delay(2000) // Let the message appear (it takes 1s by default in WorkBench)

      withContext(uiThread) {
        Assert.assertTrue(fakeUi.findComponent<JLabel> { it.text == "Loading foo" }.isVisible())

        previewView.showContent()
        fakeUi.root.validate()
      }

      delay(1000) // Let the message fade away (500ms)

      withContext(uiThread) {
        Assert.assertFalse(fakeUi.findComponent<JLabel> { it.text == "Loading foo" }.isVisible())
      }
    }

  @Test
  fun testErrorMessage() =
    runBlocking(workerThread) {
      withContext(uiThread) {
        previewView.showErrorMessage(
          "error foo happened",
          UrlData("foo url text", "www.foo.bar"),
          null
        )
        fakeUi.root.validate()
      }

      delay(2000) // Let the message appear (it takes 1s by default in WorkBench)

      withContext(uiThread) {
        Assert.assertTrue(
          fakeUi
            .findComponent<InstructionsPanel> { panel ->
              panel.toDisplayText().let {
                it.contains("error foo happened") && it.contains("[foo url text]")
              }
            }
            .isVisible()
        )

        previewView.showContent()
        fakeUi.root.validate()

        Assert.assertFalse(
          fakeUi
            .findComponent<InstructionsPanel> { panel ->
              panel.toDisplayText().let {
                it.contains("error foo happened") && it.contains("[foo url text]")
              }
            }
            .isVisible()
        )
      }
    }

  @Test
  fun testToolbar() =
    runBlocking(uiThread) {
      previewView.updateToolbar()

      // TODO(b/239802877): perform checks against the toolbar
    }
}