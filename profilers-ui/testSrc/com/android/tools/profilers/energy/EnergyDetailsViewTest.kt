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
import com.android.tools.profiler.proto.EnergyProfiler
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEvent
import com.android.tools.profiler.proto.EnergyProfiler.JobScheduled
import com.android.tools.profiler.proto.Profiler
import com.android.tools.profiler.protobuf3jarjar.ByteString
import com.android.tools.profilers.*
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.*
import java.util.concurrent.TimeUnit
import javax.swing.JTextPane

class EnergyDetailsViewTest {
  private val wakeLockAcquired = EnergyProfiler.WakeLockAcquired.newBuilder()
    .setTag("wakeLockTag")
    .setLevel(EnergyProfiler.WakeLockAcquired.Level.SCREEN_DIM_WAKE_LOCK)
    .addFlags(EnergyProfiler.WakeLockAcquired.CreationFlag.ACQUIRE_CAUSES_WAKEUP)
    .build()
  private val wakeLockAcquireEvent = EnergyEvent.newBuilder()
    .setTimestamp(TimeUnit.MILLISECONDS.toNanos(200))
    .setWakeLockAcquired(wakeLockAcquired)
    .setTraceId("traceId")
    .setEventId(123)
    .build()
  private val wakeLockReleased = EnergyProfiler.WakeLockReleased.newBuilder().setIsHeld(false).build()
  private val wakeLockReleaseEvent = EnergyEvent.newBuilder()
    .setTimestamp(TimeUnit.MILLISECONDS.toNanos(400))
    .setWakeLockReleased(wakeLockReleased)
    .setEventId(123)
    .setIsTerminal(true)
    .build()
  private val callstackText = "android.os.PowerManager\$WakeLock.acquire(PowerManager.java:32)\n"

  private val alarmSet = EnergyProfiler.AlarmSet.newBuilder()
    .setTriggerMs(1000)
    .setIntervalMs(100)
    .setWindowMs(200)
    .setType(EnergyProfiler.AlarmSet.Type.ELAPSED_REALTIME_WAKEUP)
    .setOperation(EnergyProfiler.PendingIntent.newBuilder().setCreatorPackage("package").setCreatorUid(1234).build())
    .build()
  private val alarmSetEvent =  EnergyEvent.newBuilder()
    .setTimestamp(TimeUnit.MILLISECONDS.toNanos(600))
    .setAlarmSet(alarmSet)
    .build()
  private val alarmCancelled = EnergyProfiler.AlarmCancelled.newBuilder()
    .setListener(EnergyProfiler.AlarmListener.newBuilder().setTag("cancelledTag").build())
    .build()
  private val alarmCancelledEvent = EnergyEvent.newBuilder()
    .setTimestamp(TimeUnit.MILLISECONDS.toNanos(900))
    .setAlarmCancelled(alarmCancelled)
    .setIsTerminal(true)
    .build()

  private val periodicJob = EnergyProfiler.JobInfo.newBuilder()
    .setJobId(1111)
    .setServiceName("ServiceNameValue")
    .setBackoffPolicy(EnergyProfiler.JobInfo.BackoffPolicy.BACKOFF_POLICY_LINEAR)
    .setInitialBackoffMs(1L)
    .setIsPeriodic(true)
    .setFlexMs(2L)
    .setIntervalMs(3L)
    .setNetworkType(EnergyProfiler.JobInfo.NetworkType.NETWORK_TYPE_METERED)
    .addAllTriggerContentUris(Arrays.asList("url1", "url2"))
    .setTriggerContentMaxDelay(4L)
    .setTriggerContentUpdateDelay(5L)
    .setIsRequireBatteryNotLow(true)
    .setIsRequireDeviceIdle(true)
    .setExtras("ExtrasValue")
    .setTransientExtras("TransientExtrasValue").build()
  private val nonPeriodicJob = EnergyProfiler.JobInfo.newBuilder(periodicJob)
    .setIsPeriodic(false)
    .setMinLatencyMs(10L)
    .setMaxExecutionDelayMs(20L)
    .build()
  private val jobParams = EnergyProfiler.JobParameters.newBuilder()
    .setJobId(3333)
    .addAllTriggeredContentAuthorities(Arrays.asList("auth1", "auth2"))
    .setIsOverrideDeadlineExpired(true)
    .setExtras("ExtrasValue")
    .setTransientExtras("TransientExtrasValue")
    .build()

  private val profilerService = FakeProfilerService(true)
  private val energyService = FakeEnergyService()
  @get:Rule
  var grpcChannel = FakeGrpcChannel(EnergyDetailsViewTest::class.java.simpleName, profilerService, energyService)

  private lateinit var view: EnergyDetailsView

  @Before
  fun before() {
    val timer = FakeTimer()
    val services = FakeIdeProfilerServices().apply { enableEnergyProfiler(true) }
    val profilers = StudioProfilers(grpcChannel.client, services, timer)
    profilerService.setAgentStatus(Profiler.AgentStatusResponse.Status.ATTACHED)
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
      assertUiContainsLabelAndValue(this, "Level", "SCREEN_DIM_WAKE_LOCK")
      assertUiContainsLabelAndValue(this, "Flags", "ACQUIRE_CAUSES_WAKEUP")
      assertUiContainsLabelAndValue(this, "Duration", "200ms")
    }
  }

  @Test
  fun alarmIsProperlyRendered() {
    view.setDuration(EnergyDuration(Arrays.asList(alarmSetEvent, alarmCancelledEvent)))
    val alarmTextPane = TreeWalker(view).descendants().filterIsInstance<JTextPane>().first()
    with(alarmTextPane.text) {
      assertUiContainsLabelAndValue(this, "TriggerTime", "1s")
      assertUiContainsLabelAndValue(this, "IntervalTime", "100ms")
      assertUiContainsLabelAndValue(this, "WindowTime", "200ms")
      assertUiContainsLabelAndValue(this, "Creator", "package\\b.+\\bUID\\b.+\\b1234")
      assertUiContainsLabelAndValue(this, "Duration", "300ms")
    }
  }

  @Test
  fun callstackIsProperlyRendered() {
    profilerService.addFile("traceId", ByteString.copyFromUtf8(callstackText))
    view.setDuration(EnergyDuration(Arrays.asList(wakeLockAcquireEvent)))
    val nonEmptyView = TreeWalker(view).descendants().filterIsInstance<EnergyCallstackView>().first()
    assertThat(nonEmptyView.components).isNotEmpty()
    view.setDuration(null)
    val emptyView = TreeWalker(view).descendants().filterIsInstance<EnergyCallstackView>().first()
    assertThat(emptyView.components).isEmpty()
  }

  @Test
  fun periodicJobScheduledIsProperlyRendered() {
    val jobScheduled = JobScheduled.newBuilder().setJob(periodicJob).setResult(JobScheduled.Result.RESULT_SUCCESS).build()
    val event = EnergyEvent.newBuilder().setTimestamp(TimeUnit.MILLISECONDS.toNanos(100)).setJobScheduled(jobScheduled).build()
    view.setDuration(EnergyDuration(Arrays.asList(event)))
    val textPane = TreeWalker(view).descendants().filterIsInstance<JTextPane>().first()
    with(textPane.text) {
      assertJobInfoLabelsAndValues(this)

      assertUiContainsLabelAndValue(this, "IsPeriodic", "true")
      assertUiContainsLabelAndValue(this, "FlexTime", "2ms")
      assertUiContainsLabelAndValue(this, "IntervalTime", "3ms")
      assertThat(this).doesNotContain("MinLatencyTime")
      assertThat(this).doesNotContain("MaxExecutionDelayTime")

      assertUiContainsLabelAndValue(this, "Result", "RESULT_SUCCESS")
    }
  }

  @Test
  fun nonPeriodicJobScheduleIsProperlyRendered() {
    val jobScheduled = JobScheduled.newBuilder().setJob(nonPeriodicJob).setResult(JobScheduled.Result.RESULT_FAILURE).build()
    val event = EnergyEvent.newBuilder().setTimestamp(TimeUnit.MILLISECONDS.toNanos(200)).setJobScheduled(jobScheduled).build()
    view.setDuration(EnergyDuration(Arrays.asList(event)))
    val textPane = TreeWalker(view).descendants().filterIsInstance<JTextPane>().first()
    with(textPane.text) {
      assertJobInfoLabelsAndValues(this)

      assertUiContainsLabelAndValue(this, "IsPeriodic", "false")
      assertUiContainsLabelAndValue(this, "MinLatencyTime", "10ms")
      assertUiContainsLabelAndValue(this, "MaxExecutionDelayTime", "20ms")
      assertThat(this).doesNotContain("FlexTime")
      assertThat(this).doesNotContain("IntervalTime")

      assertUiContainsLabelAndValue(this, "Result", "RESULT_FAILURE")
    }
  }

  @Test
  fun jobFinishedAndDurationIsProperlyRendered() {
    val jobScheduled = JobScheduled.newBuilder().setJob(periodicJob).setResult(JobScheduled.Result.RESULT_SUCCESS).build()
    val scheduled = EnergyEvent.newBuilder().setTimestamp(TimeUnit.MILLISECONDS.toNanos(100)).setJobScheduled(jobScheduled).build()
    val jobFinished = EnergyProfiler.JobFinished.newBuilder().setParams(jobParams).build()
    val finished = EnergyEvent.newBuilder().setTimestamp(TimeUnit.MILLISECONDS.toNanos(500)).setJobFinished(jobFinished).setIsTerminal(true).build()
    view.setDuration(EnergyDuration(Arrays.asList(scheduled, finished)))
    val textPane = TreeWalker(view).descendants().filterIsInstance<JTextPane>().first()
    with(textPane.text) {
      assertUiContainsLabelAndValue(this, "TriggerContentAuthorities", "auth1, auth2")
      assertUiContainsLabelAndValue(this, "IsOverrideDeadlineExpired", "true")
      assertUiContainsLabelAndValue(this,"Extras", "ExtrasValue")
      assertUiContainsLabelAndValue(this,"TransientExtras", "TransientExtrasValue")
      assertUiContainsLabelAndValue(this,"Duration", "400ms")
    }
  }

  @Test
  fun locationUpdateRequestedIsProperlyRendered() {
    val locationRequest = EnergyProfiler.LocationRequest.newBuilder()
      .setProvider("ProviderValue")
      .setPriority(EnergyProfiler.LocationRequest.Priority.BALANCED)
      .setIntervalMs(100)
      .setFastestIntervalMs(200)
      .setSmallestDisplacementMeters(0.1f)
      .build()
    val requested = EnergyProfiler.LocationUpdateRequested.newBuilder()
      .setIntent(EnergyProfiler.PendingIntent.newBuilder().setCreatorUid(1).setCreatorPackage("package"))
      .setRequest(locationRequest)
      .build()
    val eventBuilder = EnergyEvent.newBuilder().setTimestamp(TimeUnit.MILLISECONDS.toNanos(10)).setLocationUpdateRequested(requested)
    view.setDuration(EnergyDuration(Arrays.asList(eventBuilder.build())))
    val textPane = TreeWalker(view).descendants().filterIsInstance<JTextPane>().first()
    with(textPane.text) {
      assertUiContainsLabelAndValue(this,"Provider", "ProviderValue")
      assertUiContainsLabelAndValue(this,"IntervalTime", "100ms")
      assertUiContainsLabelAndValue(this, "FastestIntervalTime", "200ms")
      assertUiContainsLabelAndValue(this, "SmallestDisplacement", "0.1m")
      assertUiContainsLabelAndValue(this, "Creator", "package\\b.+\\bUID\\b.+\\b1")
    }
  }

  private fun assertJobInfoLabelsAndValues(uiText: String) {
    assertUiContainsLabelAndValue(uiText, "JobId", "1111")
    assertUiContainsLabelAndValue(uiText, "ServiceName", "ServiceNameValue")
    assertUiContainsLabelAndValue(uiText, "BackoffPolicy", "BACKOFF_POLICY_LINEAR")
    assertUiContainsLabelAndValue(uiText, "InitialBackoffTime", "1ms")

    assertUiContainsLabelAndValue(uiText, "NetworkType", "NETWORK_TYPE_METERED")
    assertUiContainsLabelAndValue(uiText, "TriggerContentURIs", "url1, url2")
    assertUiContainsLabelAndValue(uiText, "TriggerContentMaxDelayTime", "4ms")
    assertUiContainsLabelAndValue(uiText, "TriggerContentUpdateDelayTime", "5ms")
    assertUiContainsLabelAndValue(uiText, "PersistOnReboot", "false")
    assertUiContainsLabelAndValue(uiText, "RequiresBatteryNotLow", "true")
    assertUiContainsLabelAndValue(uiText, "RequiresCharging", "false")
    assertUiContainsLabelAndValue(uiText, "RequiresDeviceIdle", "true")
    assertUiContainsLabelAndValue(uiText, "RequiresStorageNotLow", "false")
    assertUiContainsLabelAndValue(uiText, "Extras", "ExtrasValue")
    assertUiContainsLabelAndValue(uiText, "TransientExtras", "TransientExtrasValue")
  }

  private fun assertUiContainsLabelAndValue(uiText: String, label: String, value: String) {
    assertThat(uiText).containsMatch("\\b$label\\b.+\\b$value\\b")
  }
}
