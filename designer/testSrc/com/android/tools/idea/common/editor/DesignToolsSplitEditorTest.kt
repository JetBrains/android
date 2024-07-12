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

import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.common.analytics.CommonUsageTracker
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.LayoutEditorEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JLabel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mockito.mock

@RunsInEdt
class DesignToolsSplitEditorTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @get:Rule val runsInEdt = EdtRule()

  private lateinit var splitEditor: DesignToolsSplitEditor
  private lateinit var textEditor: TextEditor
  private lateinit var designerEditor: DesignerEditor
  private var showDefaultGutterPopupValue = false

  @Before
  fun setUp() {
    val nlModel =
      NlModelBuilderUtil.model(
          projectRule,
          "layout",
          "layout.xml",
          ComponentDescriptor("LinearLayout"),
        )
        .build()
        .apply { Disposer.register(projectRule.testRootDisposable, this) }

    val project = projectRule.project
    val surface = NlSurfaceBuilder.build(projectRule.project, projectRule.testRootDisposable)
    designerEditor =
      object : DesignerEditor(nlModel.file.virtualFile, project) {
        override fun getName(): String = "Test Design Editor"

        override fun getEditorId(): String = "TestDesignEditor"

        override fun createEditorPanel(): DesignerEditorPanel =
          DesignerEditorPanel(
              this,
              project,
              file!!,
              WorkBench(project, "TestWorkBench", null, projectRule.testRootDisposable),
              { _ -> surface },
              {},
              { _, _, _, _, _ -> nlModel },
              { _ -> listOf() },
              { _, _ -> JLabel() },
              DesignerEditorPanel.State.FULL,
            )
            .apply { Disposer.register(projectRule.testRootDisposable, this) }
      }

    val textEditorComponent = object : JComponent() {}
    textEditor = mock()
    whenever(textEditor.component).thenReturn(textEditorComponent)
    whenever(textEditor.file).thenReturn(mock(VirtualFile::class.java))
    val editor = mock(EditorEx::class.java)
    whenever(editor.contentComponent).thenReturn(mock(JComponent::class.java))
    whenever(textEditor.editor).thenReturn(editor)

    val gutterComponentEx = mock(EditorGutterComponentEx::class.java)
    whenever(editor.gutterComponentEx).thenReturn(gutterComponentEx)

    whenever(gutterComponentEx.setShowDefaultGutterPopup(anyBoolean())).then {
      showDefaultGutterPopupValue = (it.arguments[0] as? Boolean) ?: false
      Unit
    }
    val component = object : JComponent() {}
    splitEditor =
      object : DesignToolsSplitEditor(textEditor, designerEditor, project) {
        // The fact that we have to call registerModeNavigationShortcuts here repeating the behavior
        // in SplitEditor is incorrect
        // and should be fixed. However, we can not use the original getComponent method since it
        // calls getComponent of
        // TextEditorWithPreview which fails with a NullPointerException in testing environment.
        // This test, however, has a value
        // because we test that registerModeNavigationShortcuts does the right thing.
        // TODO(b/146150328)
        private var registeredShortcuts = false

        override fun getComponent(): JComponent {
          if (!registeredShortcuts) {
            registeredShortcuts = true
            registerModeNavigationShortcuts(component)
          }
          return component
        }
      }
    Disposer.register(projectRule.testRootDisposable, splitEditor)
    CommonUsageTracker.NOP_TRACKER.resetLastTrackedEvent()
  }

  @After
  fun tearDown() {
    KeyboardFocusManager.setCurrentKeyboardFocusManager(null)
  }

  @Test
  fun testTrackingModeChange() {
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent).isNull()
    var triggerExplicitly = false
    splitEditor.selectTextMode(triggerExplicitly)
    // We don't track change mode events when users don't trigger them explicitly
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent).isNull()

    triggerExplicitly = true
    splitEditor.selectTextMode(triggerExplicitly)
    // We don't track mode selection when it's redundant, i.e trying to select the mode that is
    // already selected
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent).isNull()

    splitEditor.selectDesignMode(triggerExplicitly)
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent)
      .isEqualTo(LayoutEditorEvent.LayoutEditorEventType.SELECT_VISUAL_MODE)

    splitEditor.selectSplitMode(triggerExplicitly)
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent)
      .isEqualTo(LayoutEditorEvent.LayoutEditorEventType.SELECT_SPLIT_MODE)

    splitEditor.selectTextMode(triggerExplicitly)
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent)
      .isEqualTo(LayoutEditorEvent.LayoutEditorEventType.SELECT_TEXT_MODE)
  }

  @Test
  fun testModeChange() {
    var triggerExplicitly = true
    splitEditor.selectTextMode(triggerExplicitly)
    assertThat(splitEditor.isTextMode()).isTrue()
    assertTrue(showDefaultGutterPopupValue)

    triggerExplicitly = false
    // We change mode even when users don't trigger it explicitly, e.g. when jumping to XML
    // definition
    splitEditor.selectDesignMode(triggerExplicitly)
    assertThat(splitEditor.isDesignMode()).isTrue()
    assertFalse(showDefaultGutterPopupValue)

    splitEditor.selectSplitMode(triggerExplicitly)
    assertThat(splitEditor.isSplitMode()).isTrue()
    assertTrue(showDefaultGutterPopupValue)
  }

  @Test
  fun testFileIsDelegateToTextEditor() {
    val splitEditorFile = splitEditor.file!!
    assertThat(splitEditorFile).isEqualTo(textEditor.file)
  }

  @Test
  fun testKeyboardShortcuts() {
    val modifiers =
      (if (SystemInfo.isMac) InputEvent.CTRL_DOWN_MASK else InputEvent.ALT_DOWN_MASK) or
        InputEvent.SHIFT_DOWN_MASK
    val focusManager = mock(KeyboardFocusManager::class.java)
    val component = splitEditor.component
    whenever(focusManager.focusOwner).thenReturn(component)
    KeyboardFocusManager.setCurrentKeyboardFocusManager(focusManager)
    val dispatcher = IdeKeyEventDispatcher(null)

    splitEditor.selectSplitMode(true)
    // The circular sequence is ... Code <-> Split <-> Design <-> Code <-> Split <-> Design <-> Code
    // ...
    dispatcher.dispatchKeyEvent(
      KeyEvent(splitEditor.component, KeyEvent.KEY_PRESSED, 0, modifiers, KeyEvent.VK_LEFT)
    )
    assertThat(splitEditor.isTextMode()).isTrue()

    dispatcher.dispatchKeyEvent(
      KeyEvent(splitEditor.component, KeyEvent.KEY_PRESSED, 0, modifiers, KeyEvent.VK_LEFT)
    )
    assertThat(splitEditor.isDesignMode()).isTrue()

    dispatcher.dispatchKeyEvent(
      KeyEvent(splitEditor.component, KeyEvent.KEY_PRESSED, 0, modifiers, KeyEvent.VK_LEFT)
    )
    assertThat(splitEditor.isSplitMode()).isTrue()

    dispatcher.dispatchKeyEvent(
      KeyEvent(splitEditor.component, KeyEvent.KEY_PRESSED, 0, modifiers, KeyEvent.VK_RIGHT)
    )
    assertThat(splitEditor.isDesignMode()).isTrue()

    dispatcher.dispatchKeyEvent(
      KeyEvent(splitEditor.component, KeyEvent.KEY_PRESSED, 0, modifiers, KeyEvent.VK_RIGHT)
    )
    assertThat(splitEditor.isTextMode()).isTrue()

    dispatcher.dispatchKeyEvent(
      KeyEvent(splitEditor.component, KeyEvent.KEY_PRESSED, 0, modifiers, KeyEvent.VK_RIGHT)
    )
    assertThat(splitEditor.isSplitMode()).isTrue()
  }
}
