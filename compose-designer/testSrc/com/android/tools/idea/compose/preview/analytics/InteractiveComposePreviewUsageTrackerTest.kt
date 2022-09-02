/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.analytics

import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.InteractivePreviewEvent
import java.util.concurrent.Executor
import java.util.function.Consumer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class InteractiveComposePreviewUsageTrackerTest {
  private lateinit var myInteractivePreviewUsageTracker: InteractivePreviewUsageTracker

  private var myLastEventBuilder: AndroidStudioEvent.Builder? = null

  private val myEventLogger = Consumer { event: AndroidStudioEvent.Builder ->
    myLastEventBuilder = event
  }

  @Before
  fun setUp() {
    myInteractivePreviewUsageTracker =
      InteractiveComposePreviewUsageTracker(Executor { command -> command.run() }, myEventLogger)
  }

  @Test
  fun testFpsTracking() {
    myInteractivePreviewUsageTracker.logInteractiveSession(30, 15000, 15)

    assertNotNull(myLastEventBuilder)

    val event = myLastEventBuilder!!.build()

    assertEquals(event.kind, AndroidStudioEvent.EventKind.INTERACTIVE_PREVIEW_EVENT)

    val interactiveEvent = event.interactivePreviewEvent

    assertEquals(
      interactiveEvent.type,
      InteractivePreviewEvent.InteractivePreviewEventType.REPORT_FPS
    )
    assertEquals(interactiveEvent.fps, 30)
    assertEquals(interactiveEvent.durationMs, 15000)
    assertEquals(interactiveEvent.actions, 15)
  }

  @Test
  fun testStartUpLogging() {
    myInteractivePreviewUsageTracker.logStartupTime(500, 3)

    assertNotNull(myLastEventBuilder)

    val event = myLastEventBuilder!!.build()

    assertEquals(event.kind, AndroidStudioEvent.EventKind.INTERACTIVE_PREVIEW_EVENT)

    val interactiveEvent = event.interactivePreviewEvent

    assertEquals(
      interactiveEvent.type,
      InteractivePreviewEvent.InteractivePreviewEventType.REPORT_STARTUP_TIME
    )
    assertEquals(interactiveEvent.startupTimeMs, 500)
    assertEquals(interactiveEvent.peerPreviews, 3)
  }
}
