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
package com.android.tools.idea.insights.ui

import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.android.tools.idea.insights.Blames
import com.android.tools.idea.insights.ConnectionMode
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.ui.StackTraceConsole.Companion.CURRENT_CONNECTION_MODE
import com.android.tools.idea.insights.ui.StackTraceConsole.Companion.CURRENT_ISSUE
import com.android.tools.idea.insights.ui.vcs.InsightsAttachInlayDiffLinkFilter
import com.android.tools.idea.insights.ui.vcs.VCS_INFO_OF_SELECTED_CRASH
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.intellij.execution.filters.FileHyperlinkInfo
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.unscramble.AnalyzeStacktraceUtil
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val CONSOLE_LOCK = Any()

class StackTraceConsole(
  private val controller: AppInsightsProjectLevelController,
  private val project: Project,
  private val tracker: AppInsightsTracker
) : Disposable {

  val consoleView: ConsoleViewImpl = createStackPanel()
  private val scope = AndroidCoroutineScope(this)
  var onStackPrintedListener: (() -> Unit)? = null

  private var currentIssue: AppInsightsIssue? = null
  private var connectionModeState =
    controller.state.map { it.mode }.stateIn(scope, SharingStarted.Eagerly, ConnectionMode.ONLINE)

  init {
    Disposer.register(this, consoleView)
    controller.coroutineScope.launch(AndroidDispatchers.uiThread) {
      controller.state
        .mapNotNull { it.selectedIssue }
        .collect { selected -> printStack(selected, consoleView) }
    }

    DataManager.registerDataProvider(consoleView.editor.component) { dataId ->
      when {
        CURRENT_ISSUE.`is`(dataId) -> currentIssue
        CURRENT_CONNECTION_MODE.`is`(dataId) -> connectionModeState.value
        else -> null
      }
    }
  }

  private fun printStack(issue: AppInsightsIssue, consoleView: ConsoleViewImpl) {
    if (issue.sampleEvent == currentIssue?.sampleEvent) {
      return
    }
    synchronized(CONSOLE_LOCK) {
      currentIssue = null
      consoleView.clear()
      consoleView.putClientProperty(VCS_INFO_OF_SELECTED_CRASH, issue.sampleEvent.appVcsInfo)

      fun Blames.getConsoleViewContentType() =
        if (this == Blames.BLAMED) ConsoleViewContentType.ERROR_OUTPUT
        else ConsoleViewContentType.NORMAL_OUTPUT

      for (stack in issue.sampleEvent.stacktraceGroup.exceptions) {
        consoleView.print(
          "${stack.rawExceptionMessage}\n",
          stack.stacktrace.blames.getConsoleViewContentType()
        )
        val startOffset = consoleView.contentSize
        for (frame in stack.stacktrace.frames) {
          val frameLine = "    ${frame.rawSymbol}\n"
          consoleView.print(frameLine, frame.blame.getConsoleViewContentType())
        }
        val endOffset = consoleView.contentSize - 1 // TODO: -2 on windows?
        currentIssue = issue

        consoleView.performWhenNoDeferredOutput {
          consoleView.editor.foldingModel.runBatchFoldingOperation {
            synchronized(CONSOLE_LOCK) {
              if (issue != currentIssue) {
                return@runBatchFoldingOperation
              }
              val region =
                consoleView.editor.foldingModel.addFoldRegion(
                  startOffset,
                  endOffset,
                  "    <${stack.stacktrace.frames.size} frames>"
                )
              if (stack.stacktrace.blames == Blames.NOT_BLAMED) {
                region?.isExpanded = false
              }
            }
          }
        }
      }
    }
    // TODO: ensure the editor component always resizes correctly after update.
    consoleView.performWhenNoDeferredOutput {
      synchronized(CONSOLE_LOCK) {
        if (issue != currentIssue) {
          return@synchronized
        }
        consoleView.revalidate()
        consoleView.scrollTo(0)
        onStackPrintedListener?.invoke()
      }
    }
  }

  private fun createStackPanel(): ConsoleViewImpl {
    val builder = TextConsoleBuilderFactory.getInstance().createBuilder(project)
    builder.filters(AnalyzeStacktraceUtil.EP_NAME.getExtensions(project))
    val consoleView = builder.console as ConsoleViewImpl
    @Suppress("UNUSED_VARIABLE") val unused = consoleView.component // causes editor to be created
    consoleView.addMessageFilter(InsightsAttachInlayDiffLinkFilter(consoleView, tracker))
    (consoleView.editor as EditorEx).apply {
      backgroundColor = primaryContentBackground
      contentComponent.isFocusCycleRoot = false
      contentComponent.isFocusable = true
      setVerticalScrollbarVisible(false)
      setCaretEnabled(false)
    }

    val listener = ListenerForTracking(consoleView, tracker, project)
    consoleView.editor.addEditorMouseListener(listener, this)

    return consoleView
  }

  override fun dispose() = Unit

  companion object {
    val CURRENT_ISSUE: DataKey<AppInsightsIssue> = DataKey.create("currently_selected_issue")
    val CURRENT_CONNECTION_MODE: DataKey<ConnectionMode> = DataKey.create("current_connection_mode")
  }
}

class ListenerForTracking(
  private val consoleView: ConsoleViewImpl,
  private val tracker: AppInsightsTracker,
  private val project: Project
) : EditorMouseListener {
  override fun mouseReleased(event: EditorMouseEvent) {
    val contextComponent = event.editor.component

    val currentIssue =
      DataManager.getInstance().getDataContext(contextComponent).getData(CURRENT_ISSUE) ?: return

    val currentConnectionMode =
      DataManager.getInstance().getDataContext(contextComponent).getData(CURRENT_CONNECTION_MODE)
        ?: return

    val hyperlinkInfo = consoleView.hyperlinks.getHyperlinkInfoByEvent(event) ?: return
    val metricsEventBuilder =
      AppQualityInsightsUsageEvent.AppQualityInsightsStacktraceDetails.newBuilder().apply {
        clickLocation =
          AppQualityInsightsUsageEvent.AppQualityInsightsStacktraceDetails.ClickLocation
            .TARGET_FILE_HYPER_LINK
        crashType = currentIssue.issueDetails.fatality.toCrashType()
        localFile =
          (hyperlinkInfo as? FileHyperlinkInfo)?.descriptor?.file?.let {
            ProjectFileIndex.getInstance(project).isInSourceContent(it)
          }
            ?: false
      }

    tracker.logStacktraceClicked(currentConnectionMode, metricsEventBuilder.build())
  }
}
