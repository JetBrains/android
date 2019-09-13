/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.common.editor

import com.android.testutils.MockitoKt.any
import com.android.tools.idea.common.analytics.CommonUsageTracker
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.analytics.NlAnalyticsManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.LayoutEditorEvent
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke

class SplitEditorTest : AndroidTestCase() {

  private lateinit var splitEditor : SplitEditor
  private lateinit var textEditor : TextEditor
  private lateinit var designerEditor : DesignerEditor

  override fun setUp() {
    super.setUp()
    StudioFlags.NELE_SPLIT_EDITOR.override(true)

    val surface = mock(NlDesignSurface::class.java)
    val analyticsManager = NlAnalyticsManager(surface)
    `when`(surface.analyticsManager).thenReturn(analyticsManager)
    val panel = mock(DesignerEditorPanel::class.java)
    `when`(panel.surface).thenReturn(surface)
    designerEditor = mock(DesignerEditor::class.java)
    `when`(designerEditor.component).thenReturn(panel)
    val textEditorComponent = mock(JComponent::class.java)
    textEditor = mock(TextEditor::class.java)
    `when`(textEditor.component).thenReturn(textEditorComponent)
    `when`(textEditor.file).thenReturn(mock(VirtualFile::class.java))
    val component = mock(JComponent::class.java)
    `when`(component.getActionForKeyStroke(any(KeyStroke::class.java))).thenCallRealMethod()
    splitEditor = object : SplitEditor(textEditor, designerEditor, "testEditor", project) {
      override fun getComponent() = component
    }
    CommonUsageTracker.NOP_TRACKER.resetLastTrackedEvent()
  }

  fun testTrackingModeChange() {
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent).isNull()
    var triggerExplicitly = false
    splitEditor.selectTextMode(triggerExplicitly)
    // We don't track change mode events when users don't trigger them explicitly
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent).isNull()

    triggerExplicitly = true
    splitEditor.selectTextMode(triggerExplicitly)
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent).isEqualTo(LayoutEditorEvent.LayoutEditorEventType.SELECT_TEXT_MODE)

    splitEditor.selectDesignMode(triggerExplicitly)
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent).isEqualTo(LayoutEditorEvent.LayoutEditorEventType.SELECT_VISUAL_MODE)

    splitEditor.selectSplitMode(triggerExplicitly)
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent).isEqualTo(LayoutEditorEvent.LayoutEditorEventType.SELECT_SPLIT_MODE)
  }

  fun testModeChange() {
    var triggerExplicitly = true
    splitEditor.selectTextMode(triggerExplicitly)
    assertThat(splitEditor.isTextMode).isTrue()

    triggerExplicitly = false
    // We change mode even when users don't trigger it explicitly, e.g. when jumping to XML definition
    splitEditor.selectDesignMode(triggerExplicitly)
    assertThat(splitEditor.isDesignMode).isTrue()

    splitEditor.selectSplitMode(triggerExplicitly)
    assertThat(splitEditor.isSplitMode).isTrue()
  }

  fun testFileIsDelegateToTextEditor() {
    val splitEditorFile = splitEditor.file!!
    assertThat(splitEditorFile).isEqualTo(textEditor.file)
  }

  fun testKeyboardShortcuts() {
    splitEditor.selectSplitMode(true)
    val leftKey = KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, SplitEditor.ACTION_SHORTCUT_MODIFIERS)
    val navigateLeftAction = splitEditor.component.getActionForKeyStroke(leftKey)
    val navigateLeftActionEvent = ActionEvent(splitEditor.component, 0, leftKey.keyChar.toString(), leftKey.modifiers)

    assertThat(splitEditor.isSplitMode).isTrue()
    navigateLeftAction.actionPerformed(navigateLeftActionEvent)
    assertThat(splitEditor.isTextMode).isTrue()

    splitEditor.selectSplitMode(true)
    val rightKey = KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, SplitEditor.ACTION_SHORTCUT_MODIFIERS)
    val navigateRightAction = splitEditor.component.getActionForKeyStroke(rightKey)
    val navigateRightActionEvent = ActionEvent(splitEditor.component, 0, rightKey.keyChar.toString(), rightKey.modifiers)

    assertThat(splitEditor.isSplitMode).isTrue()
    navigateRightAction.actionPerformed(navigateRightActionEvent)
    assertThat(splitEditor.isDesignMode).isTrue()
  }

  override fun tearDown() {
    super.tearDown()
    StudioFlags.NELE_SPLIT_EDITOR.clearOverride()
  }
}
