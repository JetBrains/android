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
package com.android.tools.profilers.energy

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Energy
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData.DEFAULT_AGENT_ATTACHED_RESPONSE
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Arrays
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import javax.swing.JTextPane

@RunsInEdt
class EnergyDetailsViewTest {
  private val wakeLockAcquired = Energy.WakeLockAcquired.newBuilder()
    .setTag("wakeLockTag")
    .setLevel(Energy.WakeLockAcquired.Level.SCREEN_DIM_WAKE_LOCK)
    .addFlags(Energy.WakeLockAcquired.CreationFlag.ACQUIRE_CAUSES_WAKEUP)
    .build()
  private val wakeLockAcquireEvent = Common.Event.newBuilder()
    .setTimestamp(TimeUnit.MILLISECONDS.toNanos(200))
    .setEnergyEvent(Energy.EnergyEventData.newBuilder().setWakeLockAcquired(wakeLockAcquired))
    .setGroupId(123)
    .build()
  private val wakeLockReleased = Energy.WakeLockReleased.newBuilder().setIsHeld(false).build()
  private val wakeLockReleaseEvent = Common.Event.newBuilder()
    .setTimestamp(TimeUnit.MILLISECONDS.toNanos(400))
    .setEnergyEvent(Energy.EnergyEventData.newBuilder().setWakeLockReleased(wakeLockReleased))
    .setGroupId(123)
    .setIsEnded(true)
    .build()
  private val callstackText = "android.os.PowerManager\$WakeLock.acquire(PowerManager.java:32)\n"

  private val alarmSet = Energy.AlarmSet.newBuilder()
    .setTriggerMs(1000)
    .setIntervalMs(100)
    .setWindowMs(200)
    .setType(Energy.AlarmSet.Type.ELAPSED_REALTIME_WAKEUP)
    .setOperation(Energy.PendingIntent.newBuilder().setCreatorPackage("package").setCreatorUid(1234).build())
    .build()
  private val alarmSetEvent = Common.Event.newBuilder()
    .setTimestamp(TimeUnit.MILLISECONDS.toNanos(600))
    .setEnergyEvent(Energy.EnergyEventData.newBuilder().setAlarmSet(alarmSet))
    .build()
  private val alarmCancelled = Energy.AlarmCancelled.newBuilder()
    .setListener(Energy.AlarmListener.newBuilder().setTag("cancelledTag").build())
    .build()
  private val alarmCancelledEvent = Common.Event.newBuilder()
    .setTimestamp(TimeUnit.MILLISECONDS.toNanos(900))
    .setEnergyEvent(Energy.EnergyEventData.newBuilder().setAlarmCancelled(alarmCancelled))
    .setIsEnded(true)
    .build()

  private val periodicJob = Energy.JobInfo.newBuilder()
    .setJobId(1111)
    .setServiceName("ServiceNameValue")
    .setBackoffPolicy(Energy.JobInfo.BackoffPolicy.BACKOFF_POLICY_LINEAR)
    .setInitialBackoffMs(1L)
    .setIsPeriodic(true)
    .setFlexMs(2L)
    .setIntervalMs(3L)
    .setNetworkType(Energy.JobInfo.NetworkType.NETWORK_TYPE_METERED)
    .addAllTriggerContentUris(Arrays.asList("url1", "url2"))
    .setTriggerContentMaxDelay(4L)
    .setTriggerContentUpdateDelay(5L)
    .setIsRequireBatteryNotLow(true)
    .setIsRequireDeviceIdle(true)
    .setExtras("ExtrasValue")
    .setTransientExtras("TransientExtrasValue").build()
  private val nonPeriodicJob = Energy.JobInfo.newBuilder(periodicJob)
    .setIsPeriodic(false)
    .setMinLatencyMs(10L)
    .setMaxExecutionDelayMs(20L)
    .build()
  private val jobParams = Energy.JobParameters.newBuilder()
    .setJobId(3333)
    .addAllTriggeredContentAuthorities(Arrays.asList("auth1", "auth2"))
    .setIsOverrideDeadlineExpired(true)
    .setExtras("ExtrasValue")
    .setTransientExtras("TransientExtrasValue")
    .build()

  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, true)
  @get:Rule
  var grpcChannel = FakeGrpcChannel(EnergyDetailsViewTest::class.java.simpleName, transportService)
  @get:Rule
  val edtRule = EdtRule()
  @get:Rule
  val applicationRule = ApplicationRule()

  private lateinit var view: EnergyDetailsView

  @Before
  fun before() {
    val services = FakeIdeProfilerServices().apply { enableEnergyProfiler(true) }
    val profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), services, timer)
    transportService.setAgentStatus(DEFAULT_AGENT_ATTACHED_RESPONSE)
    timer.tick(TimeUnit.SECONDS.toNanos(1))

    // StudioProfilersView initialization needs to happen after the tick, as during setDevice/setProcess the StudioMonitorStage is
    // constructed. If the StudioMonitorStageView is constructed as well, grpc exceptions will be thrown due to lack of various services
    // in the channel, and the tick loop would not complete properly to set the process and agent status.
    profilers.stage = EnergyProfilerStage(profilers)
    // Initialize the view after the stage, otherwise it will create the views for the monitoring stage.
    val studioProfilersView = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    view = EnergyDetailsView(studioProfilersView.stageView as EnergyProfilerStageView)
  }

  @Test
  fun viewIsVisibleWhenDataIsNotNull() {
    view.isVisible = false
    view.setDuration(EnergyDuration(Arrays.asList(wakeLockAcquireEvent)))
    assertThat(view.isVisible).isTrue()
  }

  @Test
  fun viewIsNotVisibleWhenDataIsNull() {
    view.isVisible = true
    view.setDuration(null)
    assertThat(view.isVisible).isFalse()
  }

  @Test
  fun wakeLockIsProperlyRendered() {
    view.setDuration(EnergyDuration(Arrays.asList(wakeLockAcquireEvent, wakeLockReleaseEvent)))
    val wakeLockTextPane = TreeWalker(view).descendants().filterIsInstance<JTextPane>().first()
    with(wakeLockTextPane.text) {
      assertUiContainsLabelAndValue(this, "Tag", "wakeLockTag")
      assertUiContainsLabelAndValue(this, "Level", "Screen Dim")
      assertUiContainsLabelAndValue(this, "Duration", "200 ms")
    }
  }

  @Test
  fun alarmIsProperlyRendered() {
    view.setDuration(EnergyDuration(Arrays.asList(alarmSetEvent, alarmCancelledEvent)))
    val alarmTextPane = TreeWalker(view).descendants().filterIsInstance<JTextPane>().first()
    with(alarmTextPane.text) {
      assertUiContainsLabelAndValue(this, "Trigger Time", "1 s")
      assertUiContainsLabelAndValue(this, "Interval Time", "100 ms")
      assertUiContainsLabelAndValue(this, "Window Time", "200 ms")
      assertUiContainsLabelAndValue(this, "Creator", "package\\b.+\\bUID\\b.+\\b1234")
      assertUiContainsLabelAndValue(this, "Duration", "300 ms")
    }
  }

  @Test
  fun callstackIsProperlyRendered() {
    val eventWithTrace = wakeLockAcquireEvent.toBuilder().setEnergyEvent(
      wakeLockAcquireEvent.energyEvent.toBuilder().setCallstack(callstackText)).build()
    view.setDuration(EnergyDuration(Arrays.asList(eventWithTrace)))
    val nonEmptyView = TreeWalker(view).descendants().filterIsInstance<EnergyCallstackView>().first()
    assertThat(nonEmptyView.components.any { c -> c is JPanel }).isTrue()
    view.setDuration(null)
    val emptyView = TreeWalker(view).descendants().filterIsInstance<EnergyCallstackView>().first()
    assertThat(emptyView.components).isEmpty()
  }

  @Test
  fun periodicJobScheduledIsProperlyRendered() {
    val jobScheduled = Energy.JobScheduled.newBuilder().setJob(periodicJob).setResult(Energy.JobScheduled.Result.RESULT_SUCCESS).build()
    val event = Common.Event.newBuilder()
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(100))
      .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobScheduled(jobScheduled))
      .build()
    view.setDuration(EnergyDuration(Arrays.asList(event)))
    val textPane = TreeWalker(view).descendants().filterIsInstance<JTextPane>().first()
    with(textPane.text) {
      assertJobInfoLabelsAndValues(this)

      assertUiContainsLabelAndValue(this, "Periodic", "3 ms interval, 2 ms flex")
      assertThat(this).doesNotContain("Minimum Latency")
      assertThat(this).doesNotContain("Override Deadline")

      assertUiContainsLabelAndValue(this, "Result", "SUCCESS")
    }
  }

  @Test
  fun nonPeriodicJobScheduleIsProperlyRendered() {
    val jobScheduled = Energy.JobScheduled.newBuilder().setJob(nonPeriodicJob).setResult(Energy.JobScheduled.Result.RESULT_FAILURE).build()
    val event = Common.Event.newBuilder()
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(200))
      .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobScheduled(jobScheduled))
      .build()
    view.setDuration(EnergyDuration(Arrays.asList(event)))
    val textPane = TreeWalker(view).descendants().filterIsInstance<JTextPane>().first()
    with(textPane.text) {
      assertJobInfoLabelsAndValues(this)

      assertUiContainsLabelAndValue(this, "Minimum Latency", "10 ms")
      assertUiContainsLabelAndValue(this, "Override Deadline", "20 ms")
      assertThat(this).doesNotContain("Periodic")

      assertUiContainsLabelAndValue(this, "Result", "FAILURE")
    }
  }

  @Test
  fun jobFinishedAndDurationIsProperlyRendered() {
    val jobScheduled = Energy.JobScheduled.newBuilder().setJob(periodicJob).setResult(Energy.JobScheduled.Result.RESULT_SUCCESS).build()
    val scheduled = Common.Event.newBuilder()
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(100))
      .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobScheduled(jobScheduled))
      .build()
    val jobFinished = Energy.JobFinished.newBuilder().setParams(jobParams).build()
    val finished = Common.Event.newBuilder()
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(500))
      .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobFinished(jobFinished))
      .setIsEnded(true)
      .build()
    view.setDuration(EnergyDuration(Arrays.asList(scheduled, finished)))
    val textPane = TreeWalker(view).descendants().filterIsInstance<JTextPane>().first()
    with(textPane.text) {
      assertUiContainsLabelAndValue(this, "Triggered Content Authorities", "auth1, auth2")
      assertUiContainsLabelAndValue(this, "Is Override Deadline Expired", "true")
      assertUiContainsLabelAndValue(this, "Duration", "400 ms")
    }
  }

  @Test
  fun locationUpdateRequestedIsProperlyRendered() {
    val locationRequest = Energy.LocationRequest.newBuilder()
      .setProvider("ProviderValue")
      .setPriority(Energy.LocationRequest.Priority.BALANCED)
      .setIntervalMs(100)
      .setFastestIntervalMs(200)
      .setSmallestDisplacementMeters(0.1f)
      .build()
    val requested = Energy.LocationUpdateRequested.newBuilder()
      .setIntent(Energy.PendingIntent.newBuilder().setCreatorUid(1).setCreatorPackage("package"))
      .setRequest(locationRequest)
      .build()
    val eventBuilder = Common.Event.newBuilder()
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(10))
      .setEnergyEvent(Energy.EnergyEventData.newBuilder().setLocationUpdateRequested(requested))
    view.setDuration(EnergyDuration(Arrays.asList(eventBuilder.build())))
    val textPane = TreeWalker(view).descendants().filterIsInstance<JTextPane>().first()
    with(textPane.text) {
      assertUiContainsLabelAndValue(this, "Provider", "ProviderValue")
      assertUiContainsLabelAndValue(this, "Min Interval Time", "100 ms")
      assertUiContainsLabelAndValue(this, "Fastest Interval Time", "200 ms")
      assertUiContainsLabelAndValue(this, "Min Distance", "0.1m")
      assertUiContainsLabelAndValue(this, "Creator", "package\\b.+\\bUID\\b.+\\b1")
    }
  }

  private fun assertJobInfoLabelsAndValues(uiText: String) {
    assertUiContainsLabelAndValue(uiText, "Job ID", "1111")
    assertUiContainsLabelAndValue(uiText, "Service", "ServiceNameValue")
    assertUiContainsLabelAndValue(uiText, "Backoff Criteria", "1 ms Linear")

    assertThat(uiText).contains("Network Type: Metered")
    assertThat(uiText).contains("Battery Not Low")
    assertThat(uiText).contains("Device Idle")

    assertUiContainsLabelAndValue(uiText, "Trigger Content URIs", "url1, url2")
    assertUiContainsLabelAndValue(uiText, "Trigger Content Max Delay", "4 ms")
    assertUiContainsLabelAndValue(uiText, "Trigger Content Update Delay", "5 ms")
  }

  private fun assertUiContainsLabelAndValue(uiText: String, label: String, value: String) {
    assertThat(uiText).containsMatch("\\b$label\\b.+\\b$value\\b")
  }
}
