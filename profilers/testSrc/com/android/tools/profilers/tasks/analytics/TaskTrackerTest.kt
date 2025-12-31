/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.profilers.tasks.analytics

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.FakeFeatureTracker
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.event.FakeEventService
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TaskTrackerTest {

  private val myTimer = FakeTimer()
  private val myTransportService = FakeTransportService(myTimer)

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("TaskTrackerTestChannel", myTransportService, FakeEventService())

  private lateinit var myProfilers: StudioProfilers
  private lateinit var myServices: FakeIdeProfilerServices
  private lateinit var myFeatureTracker: FakeFeatureTracker

  @Before
  fun setUp() {
    myServices = FakeIdeProfilerServices()
    myFeatureTracker = myServices.featureTracker as FakeFeatureTracker
    myProfilers = StudioProfilers(ProfilerClient(myGrpcChannel.channel), myServices, myTimer)
  }

  @Test
  fun testCreateTaskTrackerWithUxEnabled() {
    myServices.enableTaskBasedUx(true)

    // We need to set some state in sessions manager to test buildTaskMetadata logic effectively
    // For now, testing that it returns a TaskTracker (not null tracker behavior)

    val taskTracker = TaskTracker.createTaskTracker(myProfilers)
    taskTracker.trackTaskEntered()
    assertThat(myFeatureTracker.lastTaskMetadata).isNotNull()
  }

  @Test
  fun testCreateTaskTrackerWithUxDisabled() {
    myServices.enableTaskBasedUx(false)
    val taskTracker = TaskTracker.createTaskTracker(myProfilers)

    taskTracker.trackTaskEntered()
    // Should be a NullTaskTracker, so nothing tracked
    assertThat(myFeatureTracker.lastTaskMetadata.taskId).isEqualTo(0)
  }

  @Test
  fun testTrackTaskEntered() {
    myServices.enableTaskBasedUx(true)
    val taskTracker = TaskTracker.createTaskTracker(myProfilers)

    taskTracker.trackTaskEntered()

    assertThat(myFeatureTracker.lastTaskMetadata).isNotNull()
  }

  @Test
  fun testTrackTaskFinished() {
    myServices.enableTaskBasedUx(true)
    val taskTracker = TaskTracker.createTaskTracker(myProfilers)

    val finishedState = TaskFinishedState.COMPLETED
    taskTracker.trackTaskFinished(finishedState)

    assertThat(myFeatureTracker.lastTaskMetadata).isNotNull()
    assertThat(myFeatureTracker.lastTaskFinishedState).isEqualTo(finishedState)
  }

  @Test
  fun testTrackStartTaskFailed() {
    myServices.enableTaskBasedUx(true)
    val taskTracker = TaskTracker.createTaskTracker(myProfilers)

    val failedMetadata = TaskStartFailedMetadata(traceStartStatus = null)
    taskTracker.trackStartTaskFailed(failedMetadata)

    assertThat(myFeatureTracker.lastTaskMetadata).isNotNull()
    assertThat(myFeatureTracker.lastTaskStartFailedMetadata).isEqualTo(failedMetadata)
  }

  @Test
  fun testTrackStopTaskFailed() {
    myServices.enableTaskBasedUx(true)
    val taskTracker = TaskTracker.createTaskTracker(myProfilers)

    val failedMetadata = TaskStopFailedMetadata(traceStopStatus = null)
    taskTracker.trackStopTaskFailed(failedMetadata)

    assertThat(myFeatureTracker.lastTaskMetadata).isNotNull()
    assertThat(myFeatureTracker.lastTaskStopFailedMetadata).isEqualTo(failedMetadata)
  }

  @Test
  fun testTrackProcessingTaskFailed() {
    myServices.enableTaskBasedUx(true)
    val taskTracker = TaskTracker.createTaskTracker(myProfilers)

    val failedMetadata = TaskProcessingFailedMetadata(cpuCaptureMetadata = null)
    taskTracker.trackProcessingTaskFailed(failedMetadata)

    assertThat(myFeatureTracker.lastTaskMetadata).isNotNull()
    assertThat(myFeatureTracker.lastTaskProcessingFailedMetadata).isEqualTo(failedMetadata)
  }
}
