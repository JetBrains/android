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

import com.android.testutils.MockitoKt
import com.android.tools.idea.insights.ConnectionMode
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.ui.vcs.InsightsAttachInlayDiffLinkFilter
import com.android.tools.idea.insights.ui.vcs.VCS_INFO_OF_SELECTED_CRASH
import com.android.tools.idea.insights.vcs.InsightsVcsTestRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.intellij.execution.filters.ExceptionFilters
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.event.EditorEventMulticasterImpl
import com.intellij.openapi.util.Disposer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.EditorMouseFixture
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito

@RunsInEdt
class InsightsTrackerTest {
  private val projectRule = AndroidProjectRule.onDisk()
  private val vcsInsightsRule = InsightsVcsTestRule(projectRule)

  @get:Rule val rule = RuleChain.outerRule(projectRule).around(EdtRule()).around(vcsInsightsRule)

  private lateinit var console: ConsoleViewImpl
  private lateinit var tracker: AppInsightsTracker

  private val editor
    get() = console.editor

  @Before
  fun setUp() {
    tracker = Mockito.mock(AppInsightsTracker::class.java)

    (DataManager.getInstance() as HeadlessDataManager).setTestDataProvider { dataId ->
      when {
        StackTraceConsole.CURRENT_ISSUE.`is`(dataId) -> ISSUE1
        StackTraceConsole.CURRENT_CONNECTION_MODE.`is`(dataId) -> ConnectionMode.ONLINE
        else -> null
      }
    }

    // Init console and set up filters.
    val consoleBuilder =
      TextConsoleBuilderFactory.getInstance().createBuilder(projectRule.project).apply {
        filters(ExceptionFilters.getFilters(GlobalSearchScope.allScope(projectRule.project)))
      }

    console =
      (consoleBuilder.console as ConsoleViewImpl).apply {
        Disposer.register(projectRule.testRootDisposable, this)
        addMessageFilter(InsightsAttachInlayDiffLinkFilter(this))
        component // call to init editor
      }

    val listenerForTracking = ListenerForTracking(console, tracker, projectRule.project)
    editor.addEditorMouseListener(listenerForTracking, projectRule.testRootDisposable)

    projectRule.fixture.addClass(
      """
        package test.simple;

        public class MainActivity {
            public void onCreate() {
              //TODO
            }
        }
      """
        .trimIndent()
    )
  }

  @After
  fun tearDown() {
    // Here we explicitly remove listeners added in `EditorMouseHoverPopupManager` to sidestep false
    // leakages as the lifecycles of those listeners are tied to the application, and it's not
    // excluded when checking leaks. (I filed https://youtrack.jetbrains.com/issue/IDEA-323699 --
    // hopefully it could be resolved.)
    val editorEventMulticaster =
      EditorFactory.getInstance().eventMulticaster as EditorEventMulticasterImpl

    editorEventMulticaster.listeners.onEach { (key, value) ->
      when (key) {
        CaretListener::class.java -> {
          val listener =
            value.firstOrNull {
              it.javaClass.name.startsWith(
                "com.intellij.openapi.editor.EditorMouseHoverPopupManager\$"
              )
            } as? CaretListener
              ?: return@onEach
          editorEventMulticaster.removeCaretListener(listener)
        }
        VisibleAreaListener::class.java -> {
          val listener =
            value.firstOrNull {
              it.javaClass.name.startsWith(
                "com.intellij.openapi.editor.EditorMouseHoverPopupManager\$"
              )
            } as? VisibleAreaListener
              ?: return@onEach
          editorEventMulticaster.removeVisibleAreaListener(listener)
        }
      }
    }
  }

  @Test
  fun `log hyperlink`() {
    // Print and apply filters
    console.print(
      """
        java.lang.NullPointerException:
            test.simple.MainActivity.onCreate(MainActivity.kt:4)
      """
        .trimIndent()
    )

    // Click on the hyperlink
    val mouse = editor.mouse()
    mouse.clickAt(1, 47)

    // Verify logged contents
    Mockito.verify(tracker, Mockito.times(1))
      .logStacktraceClicked(
        MockitoKt.eq(ConnectionMode.ONLINE),
        MockitoKt.eq(
          AppQualityInsightsUsageEvent.AppQualityInsightsStacktraceDetails.newBuilder()
            .apply {
              crashType = AppQualityInsightsUsageEvent.CrashType.FATAL
              localFile = true
              clickLocation =
                AppQualityInsightsUsageEvent.AppQualityInsightsStacktraceDetails.ClickLocation
                  .TARGET_FILE_HYPER_LINK
            }
            .build()
        )
      )
  }

  @Test
  fun `log diff inlay`() {
    // Print and apply filters
    console.putClientProperty(VCS_INFO_OF_SELECTED_CRASH, ISSUE1.sampleEvent.appVcsInfo)
    console.print(
      """
        java.lang.NullPointerException:
            test.simple.MainActivity.onCreate(MainActivity.kt:4)
      """
        .trimIndent()
    )

    // Click on the inlay
    val mouse = editor.mouse()
    mouse.clickAt(1, 55)

    // Verify logged contents
    Mockito.verify(tracker, Mockito.times(1))
      .logStacktraceClicked(
        MockitoKt.eq(ConnectionMode.ONLINE),
        MockitoKt.eq(
          AppQualityInsightsUsageEvent.AppQualityInsightsStacktraceDetails.newBuilder()
            .apply {
              crashType = AppQualityInsightsUsageEvent.CrashType.FATAL
              clickLocation =
                AppQualityInsightsUsageEvent.AppQualityInsightsStacktraceDetails.ClickLocation
                  .DIFF_INLAY
            }
            .build()
        )
      )
  }
}

private fun Editor.mouse(): EditorMouseFixture {
  return EditorMouseFixture(this as EditorImpl)
}

private fun ConsoleViewImpl.print(text: String) {
  print(text, ConsoleViewContentType.NORMAL_OUTPUT)

  WriteAction.run<RuntimeException>(this::flushDeferredText)
  waitAllRequests()
  editor.caretModel.moveToOffset(0)
}
