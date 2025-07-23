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
package com.android.tools.profilers.taskbased.tabs.task.leakcanary

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.tools.adtui.compose.utils.StudioComposeTestRule
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.leakcanarylib.data.Analysis
import com.android.tools.leakcanarylib.data.AnalysisSuccess
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.WithFakeTimer
import com.android.tools.profilers.leakcanary.LeakCanaryModel
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LeakCanaryScreenTest : WithFakeTimer {
  override val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)

  @Rule
  @JvmField
  val grpcChannel = FakeGrpcChannel("LeakCanaryScreenTestChannel", transportService)
  private lateinit var profilers: StudioProfilers
  private lateinit var leakCanaryModel: LeakCanaryModel
  private lateinit var ideProfilerServices: FakeIdeProfilerServices

  @get:Rule
  val composeTestRule = StudioComposeTestRule.createStudioComposeTestRule()

  @Before
  fun setup() {
    ideProfilerServices = FakeIdeProfilerServices()
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideProfilerServices, timer)
    leakCanaryModel = LeakCanaryModel(profilers)
  }

  @Test
  fun `test leak canary left and right panel data with the first selected leak`() {
    val analysis = getMultipleLeaksAnalysis()
    leakCanaryModel.addLeaks((analysis as AnalysisSuccess).leaks)
    leakCanaryModel.onLeakSelection(leakCanaryModel.leaks.value[0])
    composeTestRule.setContent { LeakCanaryScreen(leakCanaryModel = leakCanaryModel) }
    composeTestRule.onNodeWithText(TaskBasedUxStrings.LEAKCANARY_LEAK_HEADER_TEXT).isDisplayed()
    composeTestRule
      .onNodeWithText(TaskBasedUxStrings.LEAKCANARY_OCCURRENCES_HEADER_TEXT)
      .isDisplayed()
    composeTestRule
      .onNodeWithText(TaskBasedUxStrings.LEAKCANARY_TOTAL_LEAKED_HEADER_TEXT)
      .isDisplayed()
    composeTestRule
      .onNodeWithText(TaskBasedUxStrings.LEAKCANARY_LEAK_LIST_EMPTY_INITIAL_MESSAGE)
      .assertDoesNotExist()

    // 2 leaks total in the Analysis
    composeTestRule.onAllNodesWithTag("leakListRow", useUnmergedTree = true).assertCountEquals(2)

    // By default, first leak will be displayed in the leak details panel
    composeTestRule.onNodeWithTag("dalvik.system.PathClassLoader").isDisplayed()
    composeTestRule.onNodeWithTag("com.amaze.filemanager.ui.fragments.TabFragment").isDisplayed()
    composeTestRule
      .onNodeWithTag("↓ TabFragment.rootView")
      .assertDoesNotExist() // doesn't exist till row is open

    composeTestRule.onNodeWithTag("com.amaze.filemanager.ui.fragments.TabFragment").performClick()
    composeTestRule.onNodeWithText("Leaking").isDisplayed()
    composeTestRule.onNodeWithText("No").isDisplayed()
    composeTestRule.onNodeWithText("Fragment.mLifecycleRegistry.state is CREATED").isDisplayed()

    composeTestRule.onNodeWithTag("dalvik.system.PathClassLoader").performClick()
    composeTestRule.onAllNodesWithText("Leaking").assertCountEquals(2) // 2 are open now
    composeTestRule.onAllNodesWithText("No").assertCountEquals(2) // 2 are open now
    composeTestRule
      .onNodeWithText("InternalLeakCanary↓ is not leaking and A ClassLoader is never leaking")
      .isDisplayed()

    composeTestRule
      .onNodeWithTag("androidx.constraintlayout.widget.ConstraintLayout")
      .performClick()
    composeTestRule.onAllNodesWithText("Leaking").assertCountEquals(3) // 3 are open now
    composeTestRule.onNodeWithText("Yes").isDisplayed()
    composeTestRule.onNodeWithText("More info").isDisplayed()
    composeTestRule.onNodeWithText("View.mWindowAttachCount = 1").isDisplayed()
  }

  @Test
  fun `test leak canary left and right panel data changes on the selected leak changes`() {
    val analysis = getMultipleLeaksAnalysis()
    composeTestRule.setContent { LeakCanaryScreen(leakCanaryModel = leakCanaryModel) }
    // Initially, it's an empty leak list
    composeTestRule.onNodeWithText(TaskBasedUxStrings.LEAKCANARY_LEAK_HEADER_TEXT).isDisplayed()
    composeTestRule
      .onNodeWithText(TaskBasedUxStrings.LEAKCANARY_OCCURRENCES_HEADER_TEXT)
      .isDisplayed()
    composeTestRule
      .onNodeWithText(TaskBasedUxStrings.LEAKCANARY_TOTAL_LEAKED_HEADER_TEXT)
      .isDisplayed()

    // Adding leaks
    leakCanaryModel.addLeaks((analysis as AnalysisSuccess).leaks)

    // 2 leaks in the heap analysis
    composeTestRule.onAllNodesWithTag("leakListRow", useUnmergedTree = true).assertCountEquals(2)

    // Click on the 2nd leak to display its details in leak details panel
    composeTestRule.onAllNodesWithTag("leakListRow", useUnmergedTree = true)[1].performClick()

    // By default, first leak will be displayed in the leak details panel
    composeTestRule.onNodeWithTag("com.amaze.filemanager.ui.activities.MainActivity").isDisplayed()
    composeTestRule.onNodeWithTag("androidx.constraintlayout.widget.ConstraintLayout").isDisplayed()
    // Doesn't exist till row is open
    composeTestRule
      .onNodeWithText("TabFragment↓ is not leaking and Activity#mDestroyed is false")
      .assertDoesNotExist()

    // Perform expand row in leak details
    composeTestRule.onNodeWithTag("com.amaze.filemanager.ui.activities.MainActivity").performClick()
    composeTestRule
      .onNodeWithText(
        "mainActivity instance of com.amaze.filemanager.ui.activities." +
          "MainActivity with mDestroyed = false"
      )
      .isDisplayed()
    composeTestRule.onNodeWithText("Leaking").isDisplayed()
    composeTestRule.onNodeWithText("No").isDisplayed()
  }

  private fun getMultipleLeaksAnalysis(): Analysis {
    val analysis =
      """
        ====================================
        HEAP ANALYSIS RESULT
        ====================================
        1 APPLICATION LEAKS

        References underlined with "~~~" are likely causes.
        Learn more at https://squ.re/leaks.

        8928 bytes retained by leaking objects
        Displaying only 1 leak trace out of 4 with the same signature
        Signature: 2d8918c3076020f19fa7ab4b6ca5cb4423772b20
        ┬───
        │ GC Root: Input or output parameters in native code
        │
        ├─ dalvik.system.PathClassLoader instance
        │    Leaking: NO (InternalLeakCanary↓ is not leaking and A ClassLoader is never leaking)
        │    ↓ ClassLoader.runtimeInternalObjects
        ├─ com.amaze.filemanager.ui.fragments.TabFragment instance
        │    Leaking: NO (Fragment.mLifecycleRegistry.state is CREATED)
        │    ↓ TabFragment.rootView
        │                  ~~~~~~~~
        ╰→ androidx.constraintlayout.widget.ConstraintLayout instance
        ​     Leaking: YES (Test Leaking 2)
        ​     View.mWindowAttachCount = 1
        ​     mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
        ====================================
        1 LIBRARY LEAKS

        Leak pattern: native global variable referencing com.example.library.LeakingClass
        Description: LeakingClass holds a static reference to LeakedObject
        8928 bytes retained by leaking objects
        Displaying only 1 leak trace out of 4 with the same signature
        Signature: 2d8918c3076020f19fa7ab4b6ca5cb4423772b20
        ┬───
        │ GC Root: Input or output parameters in native code
        │
        ├─ com.amaze.filemanager.ui.activities.MainActivity instance
        │    Leaking: NO (TabFragment↓ is not leaking and Activity#mDestroyed is false)
        │    mainActivity instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
        │    ↓ ComponentActivity.mOnConfigurationChangedListeners
        ╰→ androidx.constraintlayout.widget.ConstraintLayout instance
        ​     Leaking: YES (Leaking test 1)
        ​     mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
        ====================================
        0 UNREACHABLE OBJECTS

        An unreachable object is still in memory but LeakCanary could not find a strong reference path
        from GC roots.
        ====================================
        METADATA

        Please include this in bug reports and Stack Overflow questions.

        Build.VERSION.SDK_INT: 34
        Analysis duration: 2779 ms
        Heap dump file path: /Users/addivya/bin/leakcanary/shark/shark-cli/src/main/java/shark/data/3.hprof
        Heap dump timestamp: 1710721509247
        Heap dump duration: Unknown
        ====================================
    """
        .trimIndent()

    return Analysis.fromString(analysis)
  }

  @Test
  fun  `test leak canary left and right panel data with leaks having className in multiple lines`() {
    val analysis = getLeaksAnalysisWithClassNameInMultipleLine()
    composeTestRule.setContent {
      LeakCanaryScreen(leakCanaryModel = leakCanaryModel)
    }
    // Initially, it's an empty leak list
    composeTestRule.onNodeWithText(TaskBasedUxStrings.LEAKCANARY_LEAK_HEADER_TEXT).isDisplayed()
    composeTestRule.onNodeWithText(TaskBasedUxStrings.LEAKCANARY_OCCURRENCES_HEADER_TEXT).isDisplayed()
    composeTestRule.onNodeWithText(TaskBasedUxStrings.LEAKCANARY_TOTAL_LEAKED_HEADER_TEXT).isDisplayed()

    // Adding leaks
    leakCanaryModel.addLeaks((analysis as AnalysisSuccess).leaks)

    // 1 leak in the heap analysis
    composeTestRule.onAllNodesWithTag("leakListRow", useUnmergedTree = true).assertCountEquals(2)

    composeTestRule.onAllNodesWithTag("leakListRow", useUnmergedTree = true)[0].performClick()

    // By default, first leak will be displayed in the leak details panel
    composeTestRule.onNodeWithTag("com.google.android.apps.chromecast.app.widget.recyclerview.BackgroundClickableRecyclerView").isDisplayed()
    composeTestRule.onNodeWithTag("com.google.android.libraries.home.coreui.devicetile.DeviceTileViewHolderImpl").isDisplayed()

    // Perform expand row in leak details
    composeTestRule.onNodeWithTag("com.google.android.apps.chromecast.app.widget.recyclerview.BackgroundClickableRecyclerView").performClick()
    composeTestRule.onNodeWithText("mContext instance of android.view.ContextThemeWrapper, wrapping activity").isDisplayed()
    composeTestRule.onNodeWithText("Leaking").isDisplayed()
    composeTestRule.onNodeWithText("Unknown").isDisplayed()
  }

  private fun getLeaksAnalysisWithClassNameInMultipleLine(): Analysis {
    val analysis = """
        ====================================
        HEAP ANALYSIS RESULT
        ====================================
        1 APPLICATION LEAKS

        References underlined with "~~~" are likely causes.
        Learn more at https://squ.re/leaks.

        31776 bytes retained by leaking objects
        Signature: bd360f360b8ab62a514d66f2d6b138aa26ae83a2
        ┬───
        │ GC Root: System class
        │
        ├─ android.view.WindowManagerGlobal class
        │    Leaking: NO (a class is never leaking)
        │    ↓ static WindowManagerGlobal.sDefaultWindowManager
        │                                 ~~~~~~~~~~~~~~~~~~~~~
        ├─ android.view.WindowManagerGlobal instance
        │    Leaking: UNKNOWN
        │    Retaining 883 B in 14 objects
        │    ↓ WindowManagerGlobal.mRoots
        │                          ~~~~~~
        ├─ java.util.ArrayList instance
        │    Leaking: UNKNOWN
        │    Retaining 60 B in 2 objects
        │    ↓ ArrayList[0]
        │               ~~~
        ├─ android.view.ViewRootImpl instance
        │    Leaking: UNKNOWN
        │    Retaining 18.5 kB in 328 objects
        │    mContext instance of com.android.internal.policy.DecorContext, wrapping
        │    activity com.google.android.apps.chromecast.app.main.MainActivity with
        │    mDestroyed = false
        │    ViewRootImpl#mView is not null
        │    mWindowAttributes.mTitle = "com.google.android.apps.chromecast.app/com.
        │    google.android.apps.chromecast.app.main.MainActivity"
        │    mWindowAttributes.type = 1
        │    ↓ ViewRootImpl.mAttachInfo
        │                   ~~~~~~~~~~~
        ├─ java.util.ArrayList instance
        │    Leaking: UNKNOWN
        │    Retaining 3.4 MB in 59810 objects
        │    ↓ ArrayList[1]
        │               ~~~
        ├─ com.google.android.libraries.home.coreui.devicetile.DeviceTileViewHolderImpl
        │  instance
        │    Leaking: UNKNOWN
        │    Retaining 3.1 kB in 72 objects
        │    context instance of android.view.ContextThemeWrapper, wrapping activity
        │    com.google.android.apps.chromecast.app.main.MainActivity with mDestroyed =
        │    false
        │    ↓ DeviceTileViewHolderImpl.dialogBuilderFactory
        │                               ~~~~~~~~~~~~~~~~~~~~
        ├─ com.google.android.apps.chromecast.app.widget.recyclerview.
        │  BackgroundClickableRecyclerView instance
        │    Leaking: UNKNOWN
        │    Retaining 1.8 MB in 27660 objects
        │    View not part of a window view hierarchy
        │    View.mAttachInfo is null (view detached)
        │    View.mID = R.id.favorites_tab_rv
        │    View.mWindowAttachCount = 1
        │    mContext instance of android.view.ContextThemeWrapper, wrapping activity
        │    com.google.android.apps.chromecast.app.main.MainActivity with mDestroyed =
        │    false
        │    ↓ View.mParent
        │           ~~~~~~~
        ├─ android.widget.LinearLayout instance
        │    Leaking: UNKNOWN
        │    Retaining 320.2 kB in 5454 objects
        │    View not part of a window view hierarchy
        │    View.mAttachInfo is null (view detached)
        │    View.mID = R.id.favorites_tab_rv_with_reorder_toolbar
        │    View.mWindowAttachCount = 1
        │    mContext instance of android.view.ContextThemeWrapper, wrapping activity
        │    com.google.android.apps.chromecast.app.main.MainActivity with mDestroyed =
        │    false
        │    ↓ View.mParent
        │           ~~~~~~~
        ├─ android.widget.FrameLayout instance
        │    Leaking: UNKNOWN
        │    Retaining 307.4 kB in 5270 objects
        │    View not part of a window view hierarchy
        │    View.mAttachInfo is null (view detached)
        │    View.mWindowAttachCount = 1
        │    mContext instance of android.view.ContextThemeWrapper, wrapping activity
        │    com.google.android.apps.chromecast.app.main.MainActivity with mDestroyed =
        │    false
        │    ↓ View.mParent
        │           ~~~~~~~
        ├─ androidx.swiperefreshlayout.widget.SwipeRefreshLayout instance
        │    Leaking: UNKNOWN
        │    Retaining 268.4 kB in 4531 objects
        │    View not part of a window view hierarchy
        │    View.mAttachInfo is null (view detached)
        │    View.mID = R.id.favorites_tab_refresh_layout
        │    View.mWindowAttachCount = 1
        │    mContext instance of android.view.ContextThemeWrapper, wrapping activity
        │    com.google.android.apps.chromecast.app.main.MainActivity with mDestroyed =
        │    false
        │    ↓ View.mParent
        │           ~~~~~~~
        ╰→ androidx.coordinatorlayout.widget.
        ​   CoordinatorLayout instance
        ​     Leaking: YES (ObjectWatcher was watching this because com.google.android.
        ​     apps.chromecast.app.homemanagement.containers.dashboard.FavoritesFragment
        ​     received Fragment#onDestroyView() callback (references to its views
        ​     should be cleared to prevent leaks))
        ​     Retaining 262.9 kB in 4440 objects
        ​     key = 51c35379-8e0a-4ea8-887e-f9496ddaf115
        ​     watchDurationMillis = 243352
        ​     retainedDurationMillis = 238294
        ​     View not part of a window view hierarchy
        ​     View.mAttachInfo is null (view detached)
        ​     View.mWindowAttachCount = 1
        ​     mContext instance of android.view.ContextThemeWrapper, wrapping activity
        ​     com.google.android.apps.chromecast.app.main.MainActivity with mDestroyed
        ​     = false
        ====================================
        1 LIBRARY LEAKS

        Leak pattern: native global variable referencing com.example.library.LeakingClass
        Description: LeakingClass holds a static reference to LeakedObject
        8928 bytes retained by leaking objects
        Displaying only 1 leak trace out of 4 with the same signature
        Signature: 2d8918c3076020f19fa7ab4b6ca5cb4423772b20
        ┬───
        │ GC Root: Input or output parameters in native code
        │
        ├─ com.amaze.filemanager.ui.activities.MainActivity instance
        │    Leaking: NO (TabFragment↓ is not leaking and Activity#mDestroyed is false)
        │    mainActivity instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
        │    ↓ ComponentActivity.mOnConfigurationChangedListeners
        ╰→ androidx.constraintlayout.widget.ConstraintLayout instance
        ​     Leaking: YES (Leaking test 1)
        ​     mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
        ====================================
        0 UNREACHABLE OBJECTS

        An unreachable object is still in memory but LeakCanary could not find a strong reference path
        from GC roots.
        ====================================
        METADATA

        Please include this in bug reports and Stack Overflow questions.

        Build.VERSION.SDK_INT: 34
        Analysis duration: 2779 ms
        Heap dump file path: /Users/addivya/bin/leakcanary/shark/shark-cli/src/main/java/shark/data/3.hprof
        Heap dump timestamp: 1710721509247
        Heap dump duration: Unknown
        ====================================
    """.trimIndent()

    return Analysis.fromString(analysis)
  }
}