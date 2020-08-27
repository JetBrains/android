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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import javax.swing.JPanel

open class TestPreviewRepresentation : PreviewRepresentation {
  internal var state: PreviewRepresentationState? = null
  var nActivations = 0
  private var restoreCount = 0
  var nCaretNotifications = 0
  var lastCaretEvent: CaretEvent? = null

  override val component = JPanel()
  override fun updateNotifications(parentEditor: FileEditor) {}
  override fun dispose() {}

  override fun onActivate() {
    nActivations++
  }

  override fun onDeactivate() {
    nActivations--
  }

  override fun setState(state: PreviewRepresentationState) {
    restoreCount++
    LightJavaCodeInsightFixtureTestCase.assertEquals("restoreState must not be called more than once", 1, restoreCount)
    this.state = state
  }

  override fun getState(): PreviewRepresentationState? = mapOf()

  override fun onCaretPositionChanged(event: CaretEvent) {
    nCaretNotifications++
    lastCaretEvent = event
  }
}

open class TestPreviewRepresentationProvider(override val displayName: String,
                                             private val isAccept: Boolean,
                                             private val representation: PreviewRepresentation = TestPreviewRepresentation()) : PreviewRepresentationProvider {
  override fun accept(project: Project, virtualFile: VirtualFile) = isAccept
  override fun createRepresentation(psiFile: PsiFile): PreviewRepresentation = representation
}