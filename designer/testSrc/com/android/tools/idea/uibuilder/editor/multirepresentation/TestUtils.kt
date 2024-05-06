/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.editor.multirepresentation

import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import javax.swing.JPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

open class TestPreviewRepresentation : PreviewRepresentation {
  internal var state: PreviewRepresentationState? = null
  var nActivations = 0
  private var restoreCount = 0
  var nCaretNotifications = 0
  var lastCaretEvent: CaretEvent? = null
  override val preferredInitialVisibility: PreferredVisibility? = null

  override val component = JPanel()

  override fun updateNotifications(parentEditor: FileEditor) {}

  override fun dispose() {}

  override fun onActivate() {
    nActivations++
  }

  override fun onDeactivate() {
    assertTrue(
      "onDeactivate called more times than onActivate (nActivations = $nActivations)",
      nActivations > 0
    )
    nActivations--
  }

  override fun setState(state: PreviewRepresentationState) {
    restoreCount++
    assertEquals("restoreState must not be called more than once", 1, restoreCount)
    this.state = state
  }

  override fun getState(): PreviewRepresentationState? = mapOf()

  override fun onCaretPositionChanged(event: CaretEvent, isModificationTriggered: Boolean) {
    if (isModificationTriggered) return
    nCaretNotifications++
    lastCaretEvent = event
  }
}

open class TestPreviewRepresentationProvider(
  override val displayName: String,
  var isAccept: Boolean,
  private val representation: PreviewRepresentation = TestPreviewRepresentation()
) : PreviewRepresentationProvider {
  override suspend fun accept(project: Project, psiFile: PsiFile) = isAccept

  override fun createRepresentation(psiFile: PsiFile): PreviewRepresentation = representation
}
