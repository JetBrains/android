/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.naveditor.analytics

import com.android.tools.idea.common.model.NlModel
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import java.util.concurrent.Executor
import java.util.function.Consumer
import org.jetbrains.android.AndroidTestCase
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NavUsageTrackerImplTest : AndroidTestCase() {
  @JvmField @Rule val edtRule = EdtRule()

  @Test
  @RunsInEdt
  fun testLogEvent() {
    lateinit var eventProto: AndroidStudioEvent
    val model = mock<NlModel>()
    whenever(model.facet).thenReturn(myFacet)
    val logger = Consumer { event: AndroidStudioEvent.Builder -> eventProto = event.build() }
    val tracker = NavUsageTrackerImpl(Executor { it.run() }, model, logger)
    val event = NavEditorEvent.newBuilder().build()
    tracker.logEvent(event)
    assertEquals(AndroidStudioEvent.EventKind.NAV_EDITOR_EVENT, eventProto.kind)
    assertSame(event, eventProto.navEditorEvent)
  }
}
