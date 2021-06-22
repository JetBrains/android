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
package com.android.tools.idea.logcat

import com.android.ddmlib.IDevice
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.idea.ddms.DeviceContext
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import javax.swing.JButton
import javax.swing.JCheckBox

@RunsInEdt
class SuppressLogTagsActionTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  @get:Rule
  val mockitoRule: MockitoRule = MockitoJUnit.rule()

  @Mock
  private lateinit var mockRefresh: Runnable

  @Mock
  private lateinit var mockDeviceContext: DeviceContext

  private lateinit var preferences: AndroidLogcatGlobalPreferences

  private val suppressLogTagsAction by lazy { SuppressLogTagsAction(mockDeviceContext, mockRefresh) }

  @Before
  fun setUp() {
    enableHeadlessDialogs(projectRule.fixture.testRootDisposable)

    preferences = ApplicationManager.getApplication().getService(AndroidLogcatGlobalPreferences::class.java)

    `when`(mockDeviceContext.selectedDevice).thenReturn(mock(IDevice::class.java))
  }

  @After
  fun tearDown() {
    preferences.suppressedLogTags.clear()
  }

  @Test
  fun actionPerformed() {
    preferences.suppressedLogTags.addAll(listOf("Tag1", "Tag2"))
    val
      logcatText = """
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag4: This is a sample message"
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag3: This is a sample message"
            """.trimIndent()
    val event = createEvent(logcatText)

    createModalDialogAndInteractWithIt({ suppressLogTagsAction.actionPerformed(event) }) { dialogWrapper ->
      val treeWalker = TreeWalker(dialogWrapper.rootPane)
      val checkBoxes = treeWalker.descendants().filterIsInstance<JCheckBox>()

      assertThat(checkBoxes.groupBy({ it.isSelected }, { it.text }))
        .containsExactly(true, listOf("Tag1", "Tag2"), false, listOf("Tag3", "Tag4"))
    }
  }

  @Test
  fun actionPerformed_logcatTagsIncludePreferenceTag() {
    preferences.suppressedLogTags.addAll(listOf("Tag1", "Tag2"))
    val
      logcatText = """
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag1: This is a sample message"
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag3: This is a sample message"
            """.trimIndent()
    val event = createEvent(logcatText)

    createModalDialogAndInteractWithIt({ suppressLogTagsAction.actionPerformed(event) }) { dialogWrapper ->
      val treeWalker = TreeWalker(dialogWrapper.rootPane)
      val checkBoxes = treeWalker.descendants().filterIsInstance<JCheckBox>()

      assertThat(checkBoxes.groupBy({ it.isSelected }, { it.text }))
        .containsExactly(true, listOf("Tag1", "Tag2"), false, listOf("Tag3"))
    }
  }

  @Test
  fun actionPerformed_logcatTagsIncludeDuplicates() {
    preferences.suppressedLogTags.addAll(listOf("Tag1", "Tag2"))
    val
      logcatText = """
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag3: This is a sample message"
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag3: This is a sample message"
            """.trimIndent()
    val event = createEvent(logcatText)

    createModalDialogAndInteractWithIt({ suppressLogTagsAction.actionPerformed(event) }) { dialog ->
      assertThat(getCheckBoxes(dialog).groupBy({ it.isSelected }, { it.text }))
        .containsExactly(true, listOf("Tag1", "Tag2"), false, listOf("Tag3"))
    }
  }

  @Test
  fun actionPerformed_applyWithNoChanges() {
    preferences.suppressedLogTags.addAll(listOf("Tag1"))
    val
      logcatText = """
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag2: This is a sample message"
            """.trimIndent()
    val event = createEvent(logcatText)

    createModalDialogAndInteractWithIt({ suppressLogTagsAction.actionPerformed(event) }) {
      clickButton(it, "OK")
    }

    assertThat(preferences.suppressedLogTags).containsExactly("Tag1")
    verify(mockRefresh, never()).run()
  }

  @Test
  fun actionPerformed_applyWithChanges() {
    preferences.suppressedLogTags.addAll(listOf("Tag1"))
    val
      logcatText = """
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag2: This is a sample message"
            """.trimIndent()
    val event = createEvent(logcatText)

    createModalDialogAndInteractWithIt({ suppressLogTagsAction.actionPerformed(event) }) { dialog ->
      val checkBoxes = getCheckBoxes(dialog)
      checkBoxes.first { it.text == "Tag1" }.isSelected = false
      checkBoxes.first { it.text == "Tag2" }.isSelected = true
      clickButton(dialog, "OK")
    }

    assertThat(preferences.suppressedLogTags).containsExactly("Tag2")
    verify(mockRefresh).run()
  }

  @Test
  fun actionPerformed_cancelWithChanges() {
    preferences.suppressedLogTags.addAll(listOf("Tag1"))
    val
      logcatText = """
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag2: This is a sample message"
            """.trimIndent()
    val event = createEvent(logcatText)

    createModalDialogAndInteractWithIt({ suppressLogTagsAction.actionPerformed(event) }) { dialog ->
      val checkBoxes = getCheckBoxes(dialog)
      checkBoxes.first { it.text == "Tag1" }.isSelected = false
      checkBoxes.first { it.text == "Tag2" }.isSelected = true
      clickButton(dialog, "Cancel")
    }

    assertThat(preferences.suppressedLogTags).containsExactly("Tag1")
    verify(mockRefresh, never()).run()
  }

  private fun createEvent(logcatText: String): AnActionEvent {
    val event = mock(AnActionEvent::class.java)
    val editor = mock(Editor::class.java)

    `when`(event.getData(CommonDataKeys.EDITOR)).thenReturn(editor)
    `when`(event.getData(CommonDataKeys.PROJECT)).thenReturn(projectRule.project)
    `when`(editor.document).thenReturn(DocumentImpl(logcatText))

    return event
  }

  private fun getCheckBoxes(dialog: DialogWrapper): List<JCheckBox> {
    return TreeWalker(dialog.rootPane).descendants().filterIsInstance<JCheckBox>()
  }

  private fun clickButton(dialog: DialogWrapper, label: String) {
    TreeWalker(dialog.rootPane).descendants().filterIsInstance<JButton>().first { it.text == label }.doClick()
  }
}