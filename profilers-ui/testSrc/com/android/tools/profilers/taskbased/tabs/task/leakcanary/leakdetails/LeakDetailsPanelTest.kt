/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.profilers.taskbased.tabs.task.leakcanary.leakdetails

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.tools.adtui.compose.utils.StudioComposeTestRule
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.codenavigation.CodeLocation
import com.android.tools.idea.codenavigation.CodeNavigator.Listener
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.leakcanarylib.data.Leak
import com.android.tools.leakcanarylib.data.LeakType
import com.android.tools.leakcanarylib.data.LeakingStatus
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.WithFakeTimer
import com.android.tools.profilers.leakcanary.LeakCanaryModel
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings
import com.intellij.mock.MockApplication
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.DisposableRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy

class LeakDetailsPanelTest : WithFakeTimer {
  override val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)

  @Rule
  @JvmField
  val grpcChannel = FakeGrpcChannel("LeakDetailsPanelTestChannel", transportService)
  private lateinit var profilers: StudioProfilers
  private lateinit var leakCanaryModel: LeakCanaryModel
  private lateinit var ideProfilerServices: FakeIdeProfilerServices
  private lateinit var mockActionManager: ActionManager

  @get:Rule
  val composeTestRule = StudioComposeTestRule.createStudioComposeTestRule()

  @get:Rule
  val disposableRule = DisposableRule()
  @Before
  fun setup() {
    ideProfilerServices = spy(FakeIdeProfilerServices())
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideProfilerServices, timer)
    leakCanaryModel = LeakCanaryModel(profilers)
    mockActionManager = mock(ActionManager::class.java)
  }

  /***
   * This setup is to mock the ApplicationManager to setup Mocked ActionManager when
   * ApplicationManager.getApplication().getService(ActionManager.class) is called.
   * This helps with successful AnActionEvent.createFromAnAction call which needs ActionManager setup.
   */
  private fun setupApplicationManagerActionManager() {
    val app = spy(MockApplication(disposableRule.disposable))
    ApplicationManager.setApplication(app, disposableRule.disposable)
    app.registerService(ActionManager::class.java, mockActionManager)
  }

  @Test
  fun `test leak details panel display for selected leak`() {
    val leaks = getSampleLeak()
    val selectedLeak = leaks[0]

    composeTestRule.setContent {
      LeakDetailsPanel(selectedLeak = selectedLeak, leakCanaryModel::goToDeclaration, true)
    }
    composeTestRule.onAllNodesWithContentDescription(LeakingStatus.YES.name).assertCountEquals(1) // 1 - yes leak icon
    composeTestRule.onAllNodesWithContentDescription(LeakingStatus.NO.name).assertCountEquals(2) // 2 - no leak icon

    // Before expanding the rows, check if leak nodes are displayed
    composeTestRule.onNodeWithTag("dalvik.system.PathClassLoader").isDisplayed()
    composeTestRule.onNodeWithTag("com.amaze.filemanager.ui.fragments.AppsListFragment").isDisplayed()
    composeTestRule.onNodeWithTag("↓ AppsListFragment.rootView").assertDoesNotExist() // Doesn't exist till row is open
    composeTestRule.onNodeWithTag("↓ testing leaking").assertDoesNotExist() // Doesn't exist till row is open

    // 3 leaks in the selected leak
    composeTestRule.onAllNodesWithText("Leaking:").assertCountEquals(0) // Doesn't exist till row is open
    composeTestRule.onAllNodesWithText("No:").assertCountEquals(0) // Doesn't exist till row is open
    composeTestRule.onAllNodesWithText("Yes:").assertCountEquals(0) // Doesn't exist till row is open
    composeTestRule.onNodeWithText("Go to declaration").assertDoesNotExist()
  }

  @Test
  fun `test leak details panel displays clicking row expands the row`() {
    val leaks = getSampleLeak()
    val selectedLeak = leaks[0]

    composeTestRule.setContent {
      LeakDetailsPanel(selectedLeak = selectedLeak, leakCanaryModel::goToDeclaration, true)
    }

    // Before expanding the rows, check if leak nodes are displayed
    composeTestRule.onNodeWithTag("dalvik.system.PathClassLoader").isDisplayed()
    composeTestRule.onNodeWithTag("com.amaze.filemanager.ui.fragments.AppsListFragment").isDisplayed()
    composeTestRule.onNodeWithTag("androidx.constraintlayout.widget.ConstraintLayout").isDisplayed()
    composeTestRule.onNodeWithTag("↓ AppsListFragment.rootView").assertDoesNotExist() // Doesn't exist till row is open

    composeTestRule.onNodeWithTag("com.amaze.filemanager.ui.fragments.AppsListFragment").performClick()
    composeTestRule.onNodeWithText("Leaking").isDisplayed()
    composeTestRule.onNodeWithText("No").isDisplayed()
    composeTestRule.onNodeWithText("Fragment.mLifecycleRegistry.state is CREATED").isDisplayed()

    composeTestRule.onNodeWithTag("dalvik.system.PathClassLoader").performClick()
    composeTestRule.onAllNodesWithText("Leaking").assertCountEquals(2) // 2 occurrence of this text are open now
    composeTestRule.onAllNodesWithText("No").assertCountEquals(2) // 2 occurrence of this text are open now
    composeTestRule.onNodeWithText("InternalLeakCanary↓ is not leaking and A ClassLoader is never leaking").isDisplayed()

    composeTestRule.onNodeWithTag("androidx.constraintlayout.widget.ConstraintLayout").performClick()
    composeTestRule.onAllNodesWithText("Leaking").assertCountEquals(3) // 3 occurrence of this text are open now
    composeTestRule.onNodeWithText("Yes").isDisplayed()
    composeTestRule.onNodeWithText("More info").isDisplayed()
    composeTestRule.onNodeWithText("key = d8a25ea4-cdd7-4a2a-b459-afe3956b109b").isDisplayed()
    composeTestRule.onNodeWithText("watchDurationMillis = 242280").isDisplayed()
    composeTestRule.onNodeWithText("retainedDurationMillis = 237276").isDisplayed()
  }

  @Test
  fun `test leak go to declaration displayed on expand and clickable`() {
    val leaks = getSampleLeak()
    val selectedLeak = leaks[0]

    composeTestRule.setContent {
      LeakDetailsPanel(selectedLeak = selectedLeak, leakCanaryModel::goToDeclaration, true)
    }

    var goToDeclarationLocation: String? = null
    val codeNavigatorListener: Listener = object: Listener {
      override fun onNavigated(location: CodeLocation) {
        goToDeclarationLocation = location.className
      }
    }

    ideProfilerServices.codeNavigator.addListener(codeNavigatorListener)

    composeTestRule.onAllNodesWithText("Go to declaration").assertCountEquals(0) // None of the nodes are expanded yet
    composeTestRule.onNodeWithTag("com.amaze.filemanager.ui.fragments.AppsListFragment").performClick()
    composeTestRule.onNodeWithText("Go to declaration").isDisplayed()
    setupApplicationManagerActionManager()
    composeTestRule.onNodeWithText("Go to declaration").performClick()

    // Verify go to declaration performed its action by navigating to class
    assertNotNull(goToDeclarationLocation)
    assertEquals(goToDeclarationLocation, "com.amaze.filemanager.ui.fragments.AppsListFragment")

    composeTestRule.onNodeWithTag("dalvik.system.PathClassLoader").performClick()
    composeTestRule.onNodeWithTag("androidx.constraintlayout.widget.ConstraintLayout").performClick()
    composeTestRule.onAllNodesWithText("Go to declaration").assertCountEquals(3) // All 3 nodes are expanded now
  }

  @Test
  fun `test vertical leak line displays correctly`() {
    composeTestRule.setContent{
      VerticalLeakStatusLine(LeakingStatus.YES)
    }
    composeTestRule.onNodeWithTag("verticalLeakLine").assertIsDisplayed()
  }

  @Test
  fun `test leak details shows place holder when recording and no leak detected`() {
    composeTestRule.setContent {
      LeakDetailsPanel(selectedLeak = null, leakCanaryModel::goToDeclaration, true)
    }
    composeTestRule.onNodeWithText(TaskBasedUxStrings.LEAKCANARY_LEAK_DETAIL_EMPTY_INITIAL_MESSAGE).assertIsDisplayed()
    composeTestRule.onNodeWithText(TaskBasedUxStrings.LEAKCANARY_NOT_LEAKING).assertDoesNotExist()
  }

  @Test
  fun `test leak details shows place holder when not recording and no leak detected`() {
    composeTestRule.setContent {
      LeakDetailsPanel(selectedLeak = null, leakCanaryModel::goToDeclaration, false)
    }
    composeTestRule.onNodeWithText(TaskBasedUxStrings.LEAKCANARY_LEAK_DETAIL_EMPTY_INITIAL_MESSAGE).assertDoesNotExist()
    composeTestRule.onNodeWithText(TaskBasedUxStrings.LEAKCANARY_NO_LEAK_FOUND_MESSAGE).assertIsDisplayed()
  }

  private fun getSampleLeak(): List<Leak> {
    val applicationLeakText = """
            2200 bytes retained by leaking objects
            Signature: 41c3c2258578581a1b0c9f78b59966266ed118b9
            ┬───
            │ GC Root: Input or output parameters in native code
            │
            ├─ dalvik.system.PathClassLoader instance
            │    Leaking: NO (InternalLeakCanary↓ is not leaking and A ClassLoader is never leaking)
            │    ↓ ClassLoader.runtimeInternalObjects
            ├─ com.amaze.filemanager.ui.fragments.AppsListFragment instance
            │    Leaking: NO (Fragment.mLifecycleRegistry.state is CREATED)
            │    ↓ AppsListFragment.rootView
            │                       ~~~~~~~~
            ╰→ androidx.constraintlayout.widget.ConstraintLayout instance
            ​     Leaking: YES (testing leaking)
            ​     Retaining 2.2 kB in 43 objects
            ​     key = d8a25ea4-cdd7-4a2a-b459-afe3956b109b
            ​     watchDurationMillis = 242280
            ​     retainedDurationMillis = 237276
            """.trimIndent()

    return Leak.fromString(applicationLeakText, LeakType.APPLICATION_LEAKS)
  }
}