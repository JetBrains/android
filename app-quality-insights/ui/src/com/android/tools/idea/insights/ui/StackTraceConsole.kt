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
import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.ConnectionMode
import com.android.tools.idea.insights.Event
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.ui.vcs.CONNECTION_OF_SELECTED_CRASH
import com.android.tools.idea.insights.ui.vcs.InsightsAttachInlayDiffLinkFilter
import com.android.tools.idea.insights.ui.vcs.InsightsExceptionInfoCache
import com.android.tools.idea.insights.ui.vcs.VCS_INFO_OF_SELECTED_CRASH
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.intellij.execution.filters.FileHyperlinkInfo
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.laf.isDefaultForTheme
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.unscramble.AnalyzeStacktraceUtil
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val CONSOLE_LOCK = Any()

data class StackTraceConsoleState(
  val connection: Connection? = null,
  val mode: ConnectionMode? = null,
  val issue: AppInsightsIssue? = null,
  val event: Event? = null,
)

class StackTraceConsole(
  controller: AppInsightsProjectLevelController,
  private val project: Project,
  private val tracker: AppInsightsTracker,
) : Disposable {

  private val resolvedInfoCache =
    InsightsExceptionInfoCache(project, GlobalSearchScope.allScope(project))
  private val scope = AndroidCoroutineScope(this)

  private val stackTraceConsoleState =
    controller.state
      .mapNotNull { state ->
        state.selectedIssue?.let { issue ->
          StackTraceConsoleState(state.connections.selected, state.mode, issue, state.selectedEvent)
        }
      }
      .distinctUntilChanged()
      .stateIn(scope, SharingStarted.Eagerly, StackTraceConsoleState())

  val consoleView: ConsoleViewImpl = createStackPanel()
  var onStackPrintedListener: (() -> Unit)? = null

  private var currentEvent: Event? = null

  init {
    Disposer.register(this, consoleView)
    stackTraceConsoleState
      .filterNot { it.issue == null || it.event == null }
      .onEach { printStack(it.issue!!, it.event!!, it.connection, consoleView) }
      .flowOn(AndroidDispatchers.uiThread)
      .launchIn(scope)

    project.messageBus
      .connect(this)
      .subscribe(
        PROJECT_SYSTEM_SYNC_TOPIC,
        ProjectSystemSyncManager.SyncResultListener { clearResolvedInfoCacheAndRehighlight() },
      )
    consoleView.editor.setBorder(JBUI.Borders.empty())
    updateUI()
  }

  fun updateUI() {
    (consoleView?.editor as? EditorEx)?.apply {
      if (
        EditorColorsManager.getInstance()
          .globalScheme
          .isDefaultForTheme(LafManager.getInstance().currentUIThemeLookAndFeel)
      ) {
        backgroundColor = primaryContentBackground
      } else {
        backgroundColor = EditorColorsManager.getInstance().globalScheme.defaultBackground
      }
    }
  }

  private fun clearResolvedInfoCacheAndRehighlight() {
    resolvedInfoCache.clear()

    DumbService.getInstance(project).smartInvokeLater {
      synchronized(CONSOLE_LOCK) { consoleView.rehighlightHyperlinksAndFoldings() }
    }
  }

  private fun printStack(
    issue: AppInsightsIssue,
    event: Event,
    connection: Connection?,
    consoleView: ConsoleViewImpl,
  ) {
    if (event == currentEvent) {
      return
    }
    var startOfOtherThreads = 0
    synchronized(CONSOLE_LOCK) {
      currentEvent = null
      // ConsoleViewImpl.clear() clears non-deferred text asynchonously, causing a race condition
      // when setting the new text below, so here we manually flush and then set the text to empty.
      consoleView.flushDeferredText()
      consoleView.editor.document.setText("")
      consoleView.putClientProperty(VCS_INFO_OF_SELECTED_CRASH, event.appVcsInfo)
      consoleView.putClientProperty(CONNECTION_OF_SELECTED_CRASH, connection)

      fun Blames.getConsoleViewContentType() =
        if (this == Blames.BLAMED) ConsoleViewContentType.ERROR_OUTPUT
        else ConsoleViewContentType.NORMAL_OUTPUT

      for (stack in event.stacktraceGroup.exceptions) {
        consoleView.print(
          "${stack.rawExceptionMessage}\n",
          stack.stacktrace.blames.getConsoleViewContentType(),
        )
        val startOffset = consoleView.contentSize
        for (frame in stack.stacktrace.frames) {
          val frameLine = "    ${frame.rawSymbol}\n"
          consoleView.print(frameLine, frame.blame.getConsoleViewContentType())
        }
        val endOffset = consoleView.contentSize - 1 // TODO: -2 on windows?
        currentEvent = event
        if (stack.stacktrace.blames == Blames.BLAMED) {
          startOfOtherThreads = consoleView.contentSize
        }

        consoleView.performWhenNoDeferredOutput {
          consoleView.editor.foldingModel.runBatchFoldingOperation {
            synchronized(CONSOLE_LOCK) {
              if (event != currentEvent) {
                return@runBatchFoldingOperation
              }
              val region =
                consoleView.editor.foldingModel.addFoldRegion(
                  startOffset,
                  endOffset,
                  "    <${stack.stacktrace.frames.size} frames>",
                )
              if (stack.stacktrace.blames == Blames.NOT_BLAMED) {
                region?.isExpanded = false
              }
            }
          }
        }
      }
    }

    if (issue.issueDetails.fatality == FailureType.ANR) {
      consoleView.performWhenNoDeferredOutput {
        consoleView.editor.foldingModel.runBatchFoldingOperation {
          synchronized(CONSOLE_LOCK) {
            if (event != currentEvent) {
              return@synchronized
            }
            val region =
              consoleView.editor.foldingModel.addFoldRegion(
                startOfOtherThreads,
                consoleView.contentSize,
                "    Show all ${event.stacktraceGroup.exceptions.size} threads",
              )
            region?.isExpanded = false
          }
        }
      }
    }

    // TODO: ensure the editor component always resizes correctly after update.
    consoleView.performWhenNoDeferredOutput {
      synchronized(CONSOLE_LOCK) {
        if (event != currentEvent) {
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
    consoleView.addMessageFilter(
      InsightsAttachInlayDiffLinkFilter(resolvedInfoCache, consoleView, tracker)
    )
    (consoleView.editor as EditorEx).apply {
      contentComponent.isFocusCycleRoot = false
      contentComponent.isFocusable = true
      setVerticalScrollbarVisible(false)
      setCaretEnabled(false)
    }

    val listener = ListenerForTracking(consoleView, tracker, project, stackTraceConsoleState, scope)
    consoleView.editor.addEditorMouseListener(listener, this)

    return consoleView
  }

  override fun dispose() = Unit
}

class ListenerForTracking(
  private val consoleView: ConsoleViewImpl,
  private val tracker: AppInsightsTracker,
  private val project: Project,
  private val stackTraceConsoleState: StateFlow<StackTraceConsoleState>,
  private val scope: CoroutineScope,
) : EditorMouseListener {
  override fun mouseReleased(event: EditorMouseEvent) {
    val hyperlinkInfo = consoleView.hyperlinks.getHyperlinkInfoByEvent(event) ?: return
    // Run tracking logic in a coroutine since it requires read lock.
    // Without the read lock, it is considered a slow operation.
    scope.trackClick(hyperlinkInfo)
  }

  private fun CoroutineScope.trackClick(hyperlinkInfo: HyperlinkInfo) = launch {
    val metricsEventBuilder =
      AppQualityInsightsUsageEvent.AppQualityInsightsStacktraceDetails.newBuilder().apply {
        clickLocation =
          AppQualityInsightsUsageEvent.AppQualityInsightsStacktraceDetails.ClickLocation
            .TARGET_FILE_HYPER_LINK
        crashType = stackTraceConsoleState.value.issue?.issueDetails?.fatality?.toCrashType()
        localFile =
          (hyperlinkInfo as? FileHyperlinkInfo)?.descriptor?.file?.let {
            // isInSourceContent() requires read lock
            readAction { ProjectFileIndex.getInstance(project).isInSourceContent(it) }
          } ?: false
      }

    tracker.logStacktraceClicked(stackTraceConsoleState.value.mode, metricsEventBuilder.build())
  }
}
