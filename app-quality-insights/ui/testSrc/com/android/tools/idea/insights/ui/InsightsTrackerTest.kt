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
import com.android.tools.idea.insights.AppInsightsProjectLevelControllerRule
import com.android.tools.idea.insights.ConnectionMode
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.ui.vcs.VCS_INFO_OF_SELECTED_CRASH
import com.android.tools.idea.insights.vcs.InsightsVcsTestRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.intellij.diff.DiffManager
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.EditorMouseFixture
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.flow.MutableStateFlow
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
  private val controllerRule = AppInsightsProjectLevelControllerRule(projectRule)

  @get:Rule
  val rule =
    RuleChain.outerRule(projectRule)
      .around(EdtRule())
      .around(vcsInsightsRule)
      .around(controllerRule)

  private lateinit var console: ConsoleViewImpl
  private lateinit var tracker: AppInsightsTracker
  private lateinit var mockDiffManager: DiffManager

  private val editor
    get() = console.editor

  @Before
  fun setUp() {
    tracker = MockitoKt.mock()

    mockDiffManager = MockitoKt.mock()
    ApplicationManager.getApplication()
      .replaceService(DiffManager::class.java, mockDiffManager, projectRule.testRootDisposable)

    console =
      initConsoleWithFilters(projectRule.project, tracker).apply {
        Disposer.register(projectRule.testRootDisposable, this)
      }

    val state =
      MutableStateFlow(
        StackTraceConsoleState(null, ConnectionMode.ONLINE, ISSUE1, ISSUE1.sampleEvent)
      )
    val listenerForTracking = ListenerForTracking(console, tracker, projectRule.project, state)
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
    cleanUpListenersFromEditorMouseHoverPopupManager()
  }

  @Test
  fun `log hyperlink`() {
    // Print and apply filters
    console.printAndHighlight(
      """
        java.lang.NullPointerException:
            test.simple.MainActivity.onCreate(MainActivity.kt:4)
      """
        .trimIndent()
    )

    // Click on the hyperlink
    console.editor.caretModel.moveToOffset(
      console.editor.document.textLength - "Activity.kt:4)".length
    )
    val position = console.editor.caretModel.logicalPosition
    val point = console.editor.logicalPositionToXY(position)

    val mouse = editor.mouse()
    mouse.clickAtXY(point.x, point.y)

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
  fun `do not log comma inlay`() {
    // Print and apply filters
    console.putClientProperty(VCS_INFO_OF_SELECTED_CRASH, ISSUE1.sampleEvent.appVcsInfo)
    console.printAndHighlight(
      """
        java.lang.NullPointerException:
            test.simple.MainActivity.onCreate(MainActivity.kt:4)
      """
        .trimIndent()
    )

    // Click on the ", " inlay
    console.editor.caretModel.moveToOffset(console.editor.document.textLength - 1)
    val position = console.editor.caretModel.logicalPosition
    val point = console.editor.logicalPositionToXY(position)

    val mouse = editor.mouse()
    mouse.clickAtXY(point.x, point.y)

    // Verify logged contents
    Mockito.verifyNoInteractions(tracker)
  }

  @Test
  fun `log show diff inlay`() {
    // Print and apply filters
    console.putClientProperty(VCS_INFO_OF_SELECTED_CRASH, ISSUE1.sampleEvent.appVcsInfo)
    console.printAndHighlight(
      """
        java.lang.NullPointerException:
            test.simple.MainActivity.onCreate(MainActivity.kt:4)
      """
        .trimIndent()
    )

    // Click on the "show diff" inlay
    console.editor.caretModel.moveToOffset(console.editor.document.textLength - 1)
    val position = console.editor.caretModel.logicalPosition
    val point = console.editor.logicalPositionToXY(position)

    val mouse = editor.mouse()
    // We have a sequence of inlays (", " and "show diff") and we want to click on the second
    // one. "16" is the width of "," inlay.
    mouse.clickAtXY(point.x + 16, point.y)

    // Verify logged contents
    Mockito.verify(tracker, Mockito.times(1))
      .logStacktraceClicked(
        MockitoKt.eq(null),
        MockitoKt.eq(
          AppQualityInsightsUsageEvent.AppQualityInsightsStacktraceDetails.newBuilder()
            .apply {
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
