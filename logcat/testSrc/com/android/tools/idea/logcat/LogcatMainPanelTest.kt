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

import com.android.ddmlib.Log.LogLevel.INFO
import com.android.ddmlib.Log.LogLevel.WARN
import com.android.ddmlib.logcat.LogCatHeader
import com.android.ddmlib.logcat.LogCatMessage
import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.logcat.messages.LogcatColors
import com.android.tools.idea.testing.AndroidExecutorsRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionGroup.EMPTY_GROUP
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.tools.SimpleActionGroup
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import java.awt.BorderLayout
import java.awt.BorderLayout.CENTER
import java.awt.BorderLayout.NORTH
import java.awt.Dimension
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executors
import javax.swing.JComponent
import javax.swing.JPopupMenu

/**
 * Tests for [LogcatMainPanel]
 */
class LogcatMainPanelTest {
  private val projectRule = ProjectRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule(), AndroidExecutorsRule(Executors.newCachedThreadPool()))

  private lateinit var logcatMainPanel: LogcatMainPanel

  @After
  fun tearDown() {
    runInEdtAndWait { Disposer.dispose(logcatMainPanel) }
  }

  @RunsInEdt
  @Test
  fun createsComponents() {
    logcatMainPanel = LogcatMainPanel(projectRule.project, EMPTY_GROUP, LogcatColors(), state = null)

    val borderLayout = logcatMainPanel.layout as BorderLayout

    assertThat(logcatMainPanel.componentCount).isEqualTo(2)
    assertThat(borderLayout.getLayoutComponent(NORTH)).isInstanceOf(LogcatHeaderPanel::class.java)
    assertThat(borderLayout.getLayoutComponent(CENTER)).isSameAs(logcatMainPanel.editor.component)
  }

  @RunsInEdt
  @Test
  fun setsUpEditor() {
    logcatMainPanel = LogcatMainPanel(projectRule.project, EMPTY_GROUP, LogcatColors(), state = null)

    assertThat(logcatMainPanel.editor.gutterComponentEx.isPaintBackground).isFalse()
    val editorSettings = logcatMainPanel.editor.settings
    assertThat(editorSettings.isAllowSingleLogicalLineFolding).isTrue()
    assertThat(editorSettings.isLineMarkerAreaShown).isFalse()
    assertThat(editorSettings.isIndentGuidesShown).isFalse()
    assertThat(editorSettings.isLineNumbersShown).isFalse()
    assertThat(editorSettings.isFoldingOutlineShown).isTrue()
    assertThat(editorSettings.isAdditionalPageAtBottom).isFalse()
    assertThat(editorSettings.additionalColumnsCount).isEqualTo(0)
    assertThat(editorSettings.additionalLinesCount).isEqualTo(0)
    assertThat(editorSettings.isRightMarginShown).isFalse()
    assertThat(editorSettings.isCaretRowShown).isFalse()
    assertThat(editorSettings.isShowingSpecialChars).isFalse()
  }

  @RunsInEdt
  @Test
  fun setsDocumentCyclicBuffer() {
    // Set a buffer of 1k
    System.setProperty("idea.cycle.buffer.size", "1")
    logcatMainPanel = LogcatMainPanel(projectRule.project, EMPTY_GROUP, LogcatColors(), state = null)
    val document = logcatMainPanel.editor.document as DocumentImpl

    // Insert 2000 chars
    for (i in 1..200) {
      document.insertString(document.textLength, "123456789\n")
    }

    assertThat(document.text.length).isAtMost(1024)
  }

  /**
   * This test can't run in the EDT because it depends on coroutines that are launched in the UI Thread and need to be able to wait for them
   * to complete. If it runs in the EDT, it cannot wait for these tasks to execute.
   */
  @Test
  fun appendMessages() = runBlocking {
    runInEdtAndWait {
      logcatMainPanel = LogcatMainPanel(projectRule.project, EMPTY_GROUP, LogcatColors(), state = null, ZoneId.of("Asia/Yerevan"))
    }

    logcatMainPanel.messageProcessor.appendMessages(listOf(
      LogCatMessage(LogCatHeader(WARN, 1, 2, "app1", "tag1", Instant.ofEpochMilli(1000)), "message1"),
      LogCatMessage(LogCatHeader(INFO, 1, 2, "app2", "tag2", Instant.ofEpochMilli(1000)), "message2"),
    ))

    logcatMainPanel.messageProcessor.onIdle {
      assertThat(logcatMainPanel.editor.document.text).isEqualTo(
        """
        1970-01-01 04:00:01.000      1-2      tag1 app1 W message1
        1970-01-01 04:00:01.000      1-2      tag2 app2 I message2

      """.trimIndent())
    }
  }

  @Test
  fun appendMessages_disposedEditor() = runBlocking {
    runInEdtAndWait {
      logcatMainPanel = LogcatMainPanel(projectRule.project, EMPTY_GROUP, LogcatColors(), state = null, ZoneId.of("Asia/Yerevan"))
      Disposer.dispose(logcatMainPanel)
    }

    logcatMainPanel.messageProcessor.appendMessages(listOf(
      LogCatMessage(LogCatHeader(WARN, 1, 2, "app1", "tag1", Instant.ofEpochMilli(1000)), "message1"),
    ))
  }

  @RunsInEdt
  @Test
  fun installPopupHandler() {
    val popupActionGroup = SimpleActionGroup().apply {
      add(object : AnAction("An Action") {
        override fun actionPerformed(e: AnActionEvent) {}
      })
    }
    var latestPopup: ActionPopupMenu? = null
    val actionManager = mock<ActionManager>()
    ApplicationManager.getApplication().replaceService(ActionManager::class.java, actionManager, projectRule.project)
    `when`(actionManager.getAction(anyString())).thenReturn(
      object : AnAction("An FakeAction") {
        override fun actionPerformed(e: AnActionEvent) {}
      })
    `when`(actionManager.createActionPopupMenu(anyString(), any(ActionGroup::class.java))).thenAnswer {
      latestPopup = FakeActionPopupMenu(it.getArgument(1))
      latestPopup
    }
    logcatMainPanel = LogcatMainPanel(projectRule.project, popupActionGroup, LogcatColors(), state = null).apply {
      size = Dimension(100, 100)
    }
    val fakeUi = FakeUi(logcatMainPanel)

    fakeUi.rightClickOn(logcatMainPanel)

    assertThat(latestPopup!!.actionGroup).isSameAs(popupActionGroup)
  }

  private class FakeActionPopupMenu(private val actionGroup: ActionGroup) : ActionPopupMenu {
    override fun getComponent(): JPopupMenu {
      throw UnsupportedOperationException()
    }

    override fun getPlace(): String {
      throw UnsupportedOperationException()
    }

    override fun getActionGroup(): ActionGroup = actionGroup

    override fun setTargetComponent(component: JComponent) {
      throw UnsupportedOperationException()
    }
  }
}