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
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.logcat.actions.ClearLogcatAction
import com.android.tools.idea.logcat.actions.HeaderFormatOptionsAction
import com.android.tools.idea.logcat.messages.LogcatColors
import com.android.tools.idea.testing.AndroidExecutorsRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionGroup.EMPTY_GROUP
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.tools.SimpleActionGroup
import com.intellij.util.ConcurrencyUtil
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.spy
import java.awt.BorderLayout
import java.awt.BorderLayout.CENTER
import java.awt.BorderLayout.NORTH
import java.awt.BorderLayout.WEST
import java.awt.Dimension
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.swing.JComponent
import javax.swing.JPopupMenu

/**
 * Tests for [LogcatMainPanel]
 */
class LogcatMainPanelTest {
  private val projectRule = ProjectRule()

  private val executor = Executors.newCachedThreadPool()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule(), AndroidExecutorsRule(workerThreadExecutor = executor, ioThreadExecutor = executor))

  private lateinit var logcatMainPanel: LogcatMainPanel

  @After
  fun tearDown() {
    runInEdtAndWait { Disposer.dispose(logcatMainPanel) }
  }

  @RunsInEdt
  @Test
  fun createsComponents() {
    logcatMainPanel = logcatMainPanel()

    val borderLayout = logcatMainPanel.layout as BorderLayout

    assertThat(logcatMainPanel.componentCount).isEqualTo(3)
    assertThat(borderLayout.getLayoutComponent(NORTH)).isInstanceOf(LogcatHeaderPanel::class.java)
    assertThat(borderLayout.getLayoutComponent(CENTER)).isSameAs(logcatMainPanel.editor.component)
    assertThat(borderLayout.getLayoutComponent(WEST)).isInstanceOf(ActionToolbar::class.java)
    val toolbar = borderLayout.getLayoutComponent(WEST) as ActionToolbar
    assertThat(toolbar.actions.map { it::class }).containsExactly(
      ClearLogcatAction::class,
      ScrollToTheEndToolbarAction::class,
      HeaderFormatOptionsAction::class,
      Separator::class,
    ).inOrder()
  }

  @RunsInEdt
  @Test
  fun setsUpEditor() {
    logcatMainPanel = logcatMainPanel()

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
    logcatMainPanel = logcatMainPanel()
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
      logcatMainPanel = logcatMainPanel(zoneId = ZoneId.of("Asia/Yerevan"))
    }

    logcatMainPanel.messageProcessor.appendMessages(listOf(
      LogCatMessage(LogCatHeader(WARN, 1, 2, "app1", "tag1", Instant.ofEpochMilli(1000)), "message1"),
      LogCatMessage(LogCatHeader(INFO, 1, 2, "app2", "tag2", Instant.ofEpochMilli(1000)), "message2"),
    ))

    logcatMainPanel.messageProcessor.onIdle {
      assertThat(logcatMainPanel.editor.document.text).isEqualTo("""
        1970-01-01 04:00:01.000     1-2     tag1                    app1                                 W  message1
        1970-01-01 04:00:01.000     1-2     tag2                    app2                                 I  message2

      """.trimIndent())
    }
  }

  @Test
  fun appendMessages_disposedEditor() = runBlocking {
    runInEdtAndWait {
      logcatMainPanel = logcatMainPanel()
      Disposer.dispose(logcatMainPanel)
    }

    logcatMainPanel.messageProcessor.appendMessages(listOf(logCatMessage()))
  }

  @Test
  fun appendMessages_scrollToEnd() = runBlocking {
    runInEdtAndWait {
      logcatMainPanel = logcatMainPanel()
    }

    logcatMainPanel.messageProcessor.appendMessages(listOf(
      logCatMessage(),
      logCatMessage(),
    ))

    logcatMainPanel.messageProcessor.onIdle {
      @Suppress("ConvertLambdaToReference")
      assertThat(logcatMainPanel.editor.isCaretAtBottom()).isTrue()
    }
  }

  @Test
  fun appendMessages_notAtBottom_doesNotScrollToEnd() = runBlocking {
    runInEdtAndWait {
      logcatMainPanel = logcatMainPanel()
    }

    logcatMainPanel.messageProcessor.appendMessages(listOf(logCatMessage()))
    logcatMainPanel.messageProcessor.onIdle {
      logcatMainPanel.editor.caretModel.moveToOffset(0)
    }
    logcatMainPanel.messageProcessor.appendMessages(listOf(logCatMessage()))

    logcatMainPanel.messageProcessor.onIdle {
      @Suppress("ConvertLambdaToReference")
      assertThat(logcatMainPanel.editor.isCaretAtBottom()).isFalse()
    }
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
    val actionManager = ApplicationManager.getApplication().getService(ActionManager::class.java)
    val mockActionManager = spy(actionManager)
    try {
      ApplicationManager.getApplication().replaceService(ActionManager::class.java, mockActionManager, projectRule.project)
      `when`(mockActionManager.createActionPopupMenu(anyString(), any(ActionGroup::class.java))).thenAnswer {
        latestPopup = FakeActionPopupMenu(it.getArgument(1))
        latestPopup
      }
      logcatMainPanel = logcatMainPanel(popupActionGroup = popupActionGroup).apply {
        size = Dimension(100, 100)
      }
      val fakeUi = FakeUi(logcatMainPanel)

      fakeUi.rightClickOn(logcatMainPanel)

      assertThat(latestPopup!!.actionGroup).isSameAs(popupActionGroup)
    }
    finally {
      ApplicationManager.getApplication().replaceService(ActionManager::class.java, actionManager, projectRule.project)
    }
  }

  @RunsInEdt
  @Test
  fun isMessageViewEmpty_emptyDocument() {
    logcatMainPanel = logcatMainPanel()
    logcatMainPanel.editor.document.setText("")

    assertThat(logcatMainPanel.isMessageViewEmpty()).isTrue()
  }

  @RunsInEdt
  @Test
  fun isMessageViewEmpty_notEmptyDocument() {
    logcatMainPanel = logcatMainPanel()
    logcatMainPanel.editor.document.setText("not-empty")

    assertThat(logcatMainPanel.isMessageViewEmpty()).isFalse()
  }

  @Test
  fun clearMessageView() {
    runInEdtAndWait {
      logcatMainPanel = logcatMainPanel()
      logcatMainPanel.editor.document.setText("not-empty")
    }

    logcatMainPanel.clearMessageView()

    ConcurrencyUtil.awaitQuiescence(AndroidExecutors.getInstance().ioThreadExecutor as ThreadPoolExecutor, 5, TimeUnit.SECONDS)
    runInEdtAndWait { }
    assertThat(logcatMainPanel.editor.document.text).isEmpty()
    assertThat(logcatMainPanel.messageBacklog.messages).isEmpty()
    // TODO(aalbert): Test the 'logcat -c' functionality if new adb lib allows for it.
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

  private fun logcatMainPanel(
    popupActionGroup: ActionGroup = EMPTY_GROUP,
    logcatColors: LogcatColors = LogcatColors(),
    state: LogcatPanelConfig? = null,
    zoneId: ZoneId = ZoneId.of("Asia/Yerevan"),
  ) = LogcatMainPanel(projectRule.project, popupActionGroup, logcatColors, state, zoneId)
}
