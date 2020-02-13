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
package com.android.tools.idea.uibuilder.actions.analytics

import com.android.tools.idea.uibuilder.actions.LayoutEditorHelpAssistantAction.Type
import com.google.wireless.android.sdk.stats.DesignEditorHelpPanelEvent
import com.google.wireless.android.sdk.stats.DesignEditorHelpPanelEvent.HelpPanelAction
import com.google.wireless.android.sdk.stats.DesignEditorHelpPanelEvent.HelpPanelType
import org.jetbrains.android.AndroidTestCase
import kotlin.test.assertNotEquals

class AssistantPanelMetricsTrackerTest : AndroidTestCase() {

  fun testLogOpen() {
    var eventBuilder: DesignEditorHelpPanelEvent.Builder? = null
    val type = Type.FULL
    val metric = object : AssistantPanelMetricsTracker(type) {
      override fun logEvent(event: DesignEditorHelpPanelEvent.Builder) {
        eventBuilder = event
      }
    }
    metric.logOpen()
    assertTrue(metric.timer.isRunning)
    assertNotNull(eventBuilder)
    assertEquals(HelpPanelType.FULL_ALL, eventBuilder!!.helpPanelType)
    assertEquals(HelpPanelAction.OPEN, eventBuilder!!.action)
  }

  fun testLogCLose() {
    var eventBuilder: DesignEditorHelpPanelEvent.Builder? = null
    val type = Type.FULL
    val metric = object : AssistantPanelMetricsTracker(type) {
      override fun logEvent(event: DesignEditorHelpPanelEvent.Builder) {
        eventBuilder = event
      }
    }

    metric.logOpen()
    Thread.sleep(100L)
    metric.logClose()
    assertTrue(!metric.timer.isRunning)
    assertNotEquals(0, metric.timer.elapsed().toMillis())
    assertEquals(HelpPanelType.FULL_ALL, eventBuilder!!.helpPanelType)
    assertEquals(metric.timer.elapsed().toMillis(), eventBuilder!!.timeToCloseMs)
    assertEquals(HelpPanelAction.CLOSE, eventBuilder!!.action)
  }
}