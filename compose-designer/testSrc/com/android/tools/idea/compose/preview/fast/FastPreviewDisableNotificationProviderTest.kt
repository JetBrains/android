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
package com.android.tools.idea.compose.preview.fast

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.editors.literals.FastPreviewApplicationConfiguration
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiFile
import com.intellij.ui.HyperlinkLabel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel

class FastPreviewDisableNotificationProviderTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val fastPreviewFlagRule = FastPreviewRule()

  private lateinit var notificationProvider: FastPreviewDisableNotificationProvider
  private lateinit var file: PsiFile
  private lateinit var fileEditor: FileEditor
  private lateinit var fastPreviewManager: FastPreviewManager

  private fun createFakeUi(content: JPanel?) = content?.let {
    FakeUi(JPanel().apply {
      layout = BorderLayout()
      size = Dimension(500, 50)
      add(it, BorderLayout.CENTER)
    }, 1.0, true).apply {
      root.validate()
    }
  }

  @Before
  fun setUp() {
    FastPreviewApplicationConfiguration.getInstance().isEnabled = true
    notificationProvider = FastPreviewDisableNotificationProvider()
    file = projectRule.fixture.addFileToProject("test.kt", """
      fun empty() {}
    """.trimIndent())
    projectRule.fixture.configureFromExistingVirtualFile(file.virtualFile)
    fileEditor = FileEditorManager.getInstance(projectRule.project).selectedEditor!!
    fastPreviewManager = FastPreviewManager.getInstance(projectRule.project)
  }

  @After
  fun tearDown() {
    FastPreviewApplicationConfiguration.getInstance().resetDefault()
  }

  @Test
  fun `disable reason triggers notification`() {
    assertTrue(fastPreviewManager.isEnabled)
    // No notification when preview is enable
    assertNull(createFakeUi(notificationProvider.createNotificationPanel(file.virtualFile, fileEditor, projectRule.project)))

    // Notification panel when preview is disabled with a reason
    fastPreviewManager.disable(DisableReason("Title", "Description"))
    assertFalse(fastPreviewManager.isEnabled)
    val fakeUi = createFakeUi(notificationProvider.createNotificationPanel(file.virtualFile, fileEditor, projectRule.project))
    assertNotNull(fakeUi)
  }

  @Test
  fun `re-enable action re-enables Fast Preview`() {
    fastPreviewManager.disable(DisableReason("Title", "Description"))
    val fakeUi = createFakeUi(notificationProvider.createNotificationPanel(file.virtualFile, fileEditor, projectRule.project))!!
    fakeUi.findComponent<HyperlinkLabel> {
      it.text == "Re-enable"
    }!!.doClick()
    assertTrue(fastPreviewManager.isEnabled)
    assertTrue(fastPreviewManager.allowAutoDisable)
  }

  @Test
  fun `click auto disable action`() {
    fastPreviewManager.disable(DisableReason("Title", "Description"))
    val fakeUi = createFakeUi(notificationProvider.createNotificationPanel(file.virtualFile, fileEditor, projectRule.project))!!
    fakeUi.findComponent<HyperlinkLabel> {
      it.text == "Do not disable automatically"
    }!!.doClick()
    assertTrue(fastPreviewManager.isEnabled)
    assertFalse(fastPreviewManager.allowAutoDisable)
  }

  @Test
  fun `do not display user disable reason`() {
    fastPreviewManager.disable(ManualDisabledReason)
    assertNull(notificationProvider.createNotificationPanel(file.virtualFile, fileEditor, projectRule.project))
  }
}