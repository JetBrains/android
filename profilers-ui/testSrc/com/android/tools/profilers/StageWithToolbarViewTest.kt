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
package com.android.tools.profilers

import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.stdui.ContextMenuItem
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.AgentData
import com.google.common.truth.Truth
import icons.StudioIcons
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.swing.JComponent
import javax.swing.JPanel

@RunWith(Parameterized::class)
class StageWithToolbarViewTest(private val isTestingProfileable: Boolean) {
  private val timer = FakeTimer()
  private val service = if (isTestingProfileable) FakeTransportService(timer, true,
                                                                       AndroidVersion.VersionCodes.S,
                                                                       Common.Process.ExposureLevel.PROFILEABLE)
                        else FakeTransportService(timer)

  @JvmField
  @Rule
  val grpcChannel: FakeGrpcServer = FakeGrpcServer.createFakeGrpcServer("StageWithToolbarViewTestChannel", service)

  private val ideProfilerServices = FakeIdeProfilerServices()
  private lateinit var studioProfilers: StudioProfilers
  private lateinit var fakeStudioProfilersView: FakeStudioProfilersView
  private lateinit var stageWithToolbarView: StageWithToolbarView
  private val stageComponent = JPanel()

  @Before
  fun setUp() {
    // The Task-Based UX flag will be disabled for the call to setPreferredProcess, then re-enabled. This is because the setPreferredProcess
    // method changes behavior based on the flag's value, and some of the tests depend on the behavior with the flag turned off.
    ideProfilerServices.enableTaskBasedUx(false)
    studioProfilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideProfilerServices, timer)
    studioProfilers.setPreferredProcess(FakeTransportService.FAKE_DEVICE_NAME, FakeTransportService.FAKE_PROCESS_NAME, null)
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    ideProfilerServices.enableTaskBasedUx(true)

    val fakeIdeProfilerComponents: IdeProfilerComponents = FakeIdeProfilerComponents()
    fakeStudioProfilersView = FakeStudioProfilersView(studioProfilers, fakeIdeProfilerComponents)
    stageWithToolbarView = StageWithToolbarView(studioProfilers,
                                                stageComponent,
                                                fakeIdeProfilerComponents,
                                                { stage: Stage<*> -> buildStageView(stage) },
                                                JPanel())

    if (isTestingProfileable) {
      // We setup and profile a process, we assume that process has an agent attached by default.
      updateAgentStatus(FakeTransportService.FAKE_PROCESS.pid, ProfilersTestData.DEFAULT_AGENT_ATTACHED_RESPONSE)
    }
  }

  private fun buildStageView(stage: Stage<*>): StageView<*> = when (stage) {
    is NullMonitorStage -> NullMonitorStageView(fakeStudioProfilersView, stage)
    is StudioMonitorStage -> StudioMonitorStageView(fakeStudioProfilersView, stage)
    is FakeStage -> FakeStageView(fakeStudioProfilersView, stage)
    else -> { throw IllegalStateException("Unsupported stage found: ${stage.stageType}") }
  }

  @Test
  fun testGoLiveButtonStates() {
    ideProfilerServices.enableTaskBasedUx(false)

    // Check that go live is initially enabled and toggled
    val liveButton = stageWithToolbarView.goLiveButton
    val contextMenuItems = ProfilerContextMenu.createIfAbsent(stageComponent).contextMenuItems
    var attachItem: ContextMenuItem? = null
    var detachItem: ContextMenuItem? = null
    for (item in contextMenuItems) {
      if (item.text == StageWithToolbarView.ATTACH_LIVE) {
        attachItem = item
      }
      else if (item.text == StageWithToolbarView.DETACH_LIVE) {
        detachItem = item
      }
    }
    Truth.assertThat(attachItem).isNotNull()
    Truth.assertThat(detachItem).isNotNull()
    Truth.assertThat(studioProfilers.sessionsManager.isSessionAlive).isTrue()
    Truth.assertThat(liveButton.isEnabled).isTrue()
    Truth.assertThat(liveButton.isSelected).isTrue()
    Truth.assertThat(attachItem!!.isEnabled).isFalse()
    Truth.assertThat(detachItem!!.isEnabled).isTrue()

    // Detaching from live should unselect the button.
    detachItem.run()
    Truth.assertThat(studioProfilers.sessionsManager.isSessionAlive).isTrue()
    Truth.assertThat(liveButton.isEnabled).isTrue()
    Truth.assertThat(liveButton.isSelected).isFalse()
    Truth.assertThat(attachItem.isEnabled).isTrue()
    Truth.assertThat(detachItem.isEnabled).isFalse()
    Truth.assertThat(liveButton.icon).isEqualTo(StudioIcons.Profiler.Toolbar.GOTO_LIVE)
    Truth.assertThat(liveButton.toolTipText).startsWith(StageWithToolbarView.ATTACH_LIVE)

    // Attaching to live should select the button again.
    attachItem.run()
    Truth.assertThat(studioProfilers.sessionsManager.isSessionAlive).isTrue()
    Truth.assertThat(liveButton.isEnabled).isTrue()
    Truth.assertThat(liveButton.isSelected).isTrue()
    Truth.assertThat(attachItem.isEnabled).isFalse()
    Truth.assertThat(detachItem.isEnabled).isTrue()
    Truth.assertThat(liveButton.icon).isEqualTo(StudioIcons.Profiler.Toolbar.PAUSE_LIVE)
    Truth.assertThat(liveButton.toolTipText).startsWith(StageWithToolbarView.DETACH_LIVE)

    // Stopping the session should disable and unselect the button
    endSession()
    val deadSession = studioProfilers.sessionsManager.selectedSession
    Truth.assertThat(studioProfilers.sessionsManager.isSessionAlive).isFalse()
    Truth.assertThat(liveButton.isEnabled).isFalse()
    Truth.assertThat(liveButton.isSelected).isFalse()
    Truth.assertThat(attachItem.isEnabled).isFalse()
    Truth.assertThat(detachItem.isEnabled).isFalse()
    startSessionWithNewDeviceAndProcess()
    if (isTestingProfileable) {
      updateAgentStatus(NEW_PROCESS_ID, ProfilersTestData.DEFAULT_AGENT_ATTACHED_RESPONSE)
    }
    Truth.assertThat(studioProfilers.sessionsManager.isSessionAlive).isTrue()

    // Live button should be selected when switching to a live session.
    Truth.assertThat(liveButton.isEnabled).isTrue()
    Truth.assertThat(liveButton.isSelected).isTrue()
    Truth.assertThat(attachItem.isEnabled).isFalse()
    Truth.assertThat(detachItem.isEnabled).isTrue()

    // Switching to a dead session should disable and unselect the button.
    studioProfilers.sessionsManager.setSession(deadSession)
    Truth.assertThat(liveButton.isEnabled).isFalse()
    Truth.assertThat(liveButton.isSelected).isFalse()
    Truth.assertThat(attachItem.isEnabled).isFalse()
    Truth.assertThat(detachItem.isEnabled).isFalse()
  }

  @Test
  fun testGoLiveButtonWhenToggleStreaming() {
    val liveButton = stageWithToolbarView.goLiveButton
    Truth.assertThat(liveButton.isEnabled).isTrue()
    studioProfilers.timeline.isStreaming = false
    Truth.assertThat(liveButton.isSelected).isFalse()
    Truth.assertThat(liveButton.icon).isEqualTo(StudioIcons.Profiler.Toolbar.GOTO_LIVE)
    Truth.assertThat(liveButton.toolTipText).startsWith(StageWithToolbarView.ATTACH_LIVE)
    studioProfilers.timeline.isStreaming = true
    Truth.assertThat(liveButton.isSelected).isTrue()
    Truth.assertThat(liveButton.icon).isEqualTo(StudioIcons.Profiler.Toolbar.PAUSE_LIVE)
    Truth.assertThat(liveButton.toolTipText).startsWith(StageWithToolbarView.DETACH_LIVE)
  }

  @Test
  fun testTimelineButtonEnableStates() {
    ideProfilerServices.enableTaskBasedUx(false)

    val zoomInButton = stageWithToolbarView.zoomInButton
    val zoomOutButton = stageWithToolbarView.zoomOutButton
    val resetButton = stageWithToolbarView.resetZoomButton
    val frameSelectionButton = stageWithToolbarView.zoomToSelectionButton
    val liveButton = stageWithToolbarView.goLiveButton

    // A live session without agent should have all controls enabled
    Truth.assertThat(studioProfilers.sessionsManager.isSessionAlive).isTrue()
    Truth.assertThat(zoomInButton.isEnabled).isTrue()
    Truth.assertThat(zoomOutButton.isEnabled).isTrue()
    Truth.assertThat(resetButton.isEnabled).isTrue()
    Truth.assertThat(frameSelectionButton.isEnabled).isFalse() // Frame selection button is dependent on selection being available.
    Truth.assertThat(liveButton.isEnabled).isTrue()

    // Updating the selection should enable the frame selection control.
    studioProfilers.timeline.selectionRange.set(studioProfilers.timeline.dataRange)
    Truth.assertThat(zoomInButton.isEnabled).isTrue()
    Truth.assertThat(zoomOutButton.isEnabled).isTrue()
    Truth.assertThat(resetButton.isEnabled).isTrue()
    Truth.assertThat(frameSelectionButton.isEnabled).isTrue()
    Truth.assertThat(liveButton.isEnabled).isTrue()

    // Stopping the session should disable the live control
    endSession()
    Truth.assertThat(zoomInButton.isEnabled).isTrue()
    Truth.assertThat(zoomOutButton.isEnabled).isTrue()
    Truth.assertThat(resetButton.isEnabled).isTrue()
    Truth.assertThat(frameSelectionButton.isEnabled).isTrue()
    Truth.assertThat(liveButton.isEnabled).isFalse()

    // Starting a session that is waiting for an agent to initialize should have all controls disabled.
    startSessionWithNewDeviceAndProcess()
    updateAgentStatus(NEW_PROCESS_ID, ProfilersTestData.DEFAULT_AGENT_UNSPECIFIED_RESPONSE)
    Truth.assertThat(zoomInButton.isEnabled).isFalse()
    Truth.assertThat(zoomOutButton.isEnabled).isFalse()
    Truth.assertThat(resetButton.isEnabled).isFalse()
    Truth.assertThat(frameSelectionButton.isEnabled).isFalse()
    Truth.assertThat(liveButton.isEnabled).isFalse()

    // Controls should be enabled after agent is attached.
    updateAgentStatus(NEW_PROCESS_ID, ProfilersTestData.DEFAULT_AGENT_ATTACHED_RESPONSE)
    Truth.assertThat(zoomInButton.isEnabled).isTrue()
    Truth.assertThat(zoomOutButton.isEnabled).isTrue()
    Truth.assertThat(resetButton.isEnabled).isTrue()
    Truth.assertThat(frameSelectionButton.isEnabled).isFalse()
    Truth.assertThat(liveButton.isEnabled).isTrue()

    // Setting to an empty session should have all controls disabled.
    studioProfilers.sessionsManager.setSession(Common.Session.getDefaultInstance())
    Truth.assertThat(zoomInButton.isEnabled).isFalse()
    Truth.assertThat(zoomOutButton.isEnabled).isFalse()
    Truth.assertThat(resetButton.isEnabled).isFalse()
    Truth.assertThat(frameSelectionButton.isEnabled).isFalse()
    Truth.assertThat(liveButton.isEnabled).isFalse()
  }

  @Test
  fun testLoadingPanelWhileWaitingForPreferredProcess() {
    ideProfilerServices.enableTaskBasedUx(false)

    Truth.assertThat(stageWithToolbarView.stageViewComponent.isVisible).isTrue()
    Truth.assertThat(stageWithToolbarView.stageLoadingComponent.isVisible).isFalse()

    // Sets a preferred process, the UI should wait and show the loading panel.
    endSession()
    updatePreferredProcess(FakeTransportService.FAKE_DEVICE_NAME)
    Truth.assertThat(studioProfilers.autoProfilingEnabled).isTrue()
    Truth.assertThat(stageWithToolbarView.stageViewComponent.isVisible).isFalse()
    Truth.assertThat(stageWithToolbarView.stageLoadingComponent.isVisible).isTrue()
    val process = Common.Process.newBuilder()
      .setPid(NEW_PROCESS_ID)
      .setDeviceId(FakeTransportService.FAKE_DEVICE_ID)
      .setState(Common.Process.State.ALIVE)
      .setName(FAKE_PROCESS_2)
      .setExposureLevel(if (isTestingProfileable) Common.Process.ExposureLevel.PROFILEABLE else Common.Process.ExposureLevel.DEBUGGABLE)
      .build()
    startSession(FakeTransportService.FAKE_DEVICE, process)
    if (isTestingProfileable) {
      updateAgentStatus(NEW_PROCESS_ID, ProfilersTestData.DEFAULT_AGENT_ATTACHED_RESPONSE)
    }

    // Preferred process is found, session begins and the loading stops.
    Truth.assertThat(stageWithToolbarView.stageViewComponent.isVisible).isTrue()
    Truth.assertThat(stageWithToolbarView.stageLoadingComponent.isVisible).isFalse()
  }

  @Test
  fun testLoadingPanelDoesNotShowWhileWaitingForPreferredProcessInTaskBasedUX() {
    ideProfilerServices.enableTaskBasedUx(true)
    Truth.assertThat(stageWithToolbarView.stageViewComponent.isVisible).isTrue()
    Truth.assertThat(stageWithToolbarView.stageLoadingComponent.isVisible).isFalse()

    // Sets a preferred process, the UI should NOT wait and show the loading panel.
    endSession()
    updatePreferredProcess(FakeTransportService.FAKE_DEVICE_NAME)
    // Auto profiling is disabled in the Task-Based UX, and thus prevents the loading state from occurring on preferred process change.
    Truth.assertThat(studioProfilers.autoProfilingEnabled).isFalse()
    Truth.assertThat(stageWithToolbarView.stageViewComponent.isVisible).isTrue()
    Truth.assertThat(stageWithToolbarView.stageLoadingComponent.isVisible).isFalse()
  }

  @Test
  fun testLoadingPanelWhileWaitingForAgentAttach() {
    ideProfilerServices.enableTaskBasedUx(false)

    Assume.assumeFalse(isTestingProfileable) // hardcoded `FAKE_DEVICE` is different than one used for the profileable test
    Truth.assertThat(stageWithToolbarView.stageViewComponent.isVisible).isTrue()
    Truth.assertThat(stageWithToolbarView.stageLoadingComponent.isVisible).isFalse()
    val process = Common.Process.newBuilder()
      .setPid(NEW_PROCESS_ID)
      .setDeviceId(FakeTransportService.FAKE_DEVICE_ID)
      .setState(Common.Process.State.ALIVE)
      .setName(FAKE_PROCESS_2)
      .setExposureLevel(Common.Process.ExposureLevel.DEBUGGABLE)
      .build()
    startSession(FakeTransportService.FAKE_DEVICE, process)
    updateAgentStatus(NEW_PROCESS_ID, ProfilersTestData.DEFAULT_AGENT_UNSPECIFIED_RESPONSE)

    // Agent is detached, the UI should wait and show the loading panel.
    Truth.assertThat(stageWithToolbarView.stageViewComponent.isVisible).isFalse()
    Truth.assertThat(stageWithToolbarView.stageLoadingComponent.isVisible).isTrue()
    updateAgentStatus(NEW_PROCESS_ID, ProfilersTestData.DEFAULT_AGENT_ATTACHED_RESPONSE)

    // Attach status is detected, loading should stop.
    Truth.assertThat(stageWithToolbarView.stageViewComponent.isVisible).isTrue()
    Truth.assertThat(stageWithToolbarView.stageLoadingComponent.isVisible).isFalse()
  }

  @Test
  fun testLoadingPanelDoesNotShowWhileWaitingForAgentAttachInTaskBasedUX() {
    ideProfilerServices.enableTaskBasedUx(true)
    Assume.assumeFalse(isTestingProfileable) // hardcoded `FAKE_DEVICE` is different than one used for the profileable test
    Truth.assertThat(stageWithToolbarView.stageViewComponent.isVisible).isTrue()
    Truth.assertThat(stageWithToolbarView.stageLoadingComponent.isVisible).isFalse()
    val process = Common.Process.newBuilder()
      .setPid(NEW_PROCESS_ID)
      .setDeviceId(FakeTransportService.FAKE_DEVICE_ID)
      .setState(Common.Process.State.ALIVE)
      .setName(FAKE_PROCESS_2)
      .setExposureLevel(Common.Process.ExposureLevel.DEBUGGABLE)
      .build()
    startSession(FakeTransportService.FAKE_DEVICE, process)
    updateAgentStatus(NEW_PROCESS_ID, ProfilersTestData.DEFAULT_AGENT_UNSPECIFIED_RESPONSE)

    // Agent is detached, the UI should NOT wait and NOT show the loading panel.
    Truth.assertThat(stageWithToolbarView.stageViewComponent.isVisible).isTrue()
    Truth.assertThat(stageWithToolbarView.stageLoadingComponent.isVisible).isFalse()
  }

  @Test
  fun testNullStageIfDeviceIsUnsupported() {
    ideProfilerServices.enableTaskBasedUx(false)

    Truth.assertThat(stageWithToolbarView.stageViewComponent.isVisible).isTrue()
    Truth.assertThat(stageWithToolbarView.stageLoadingComponent.isVisible).isFalse()

    // Disconnect the current device and connect to an unsupported device.
    val deadDevice = FakeTransportService.FAKE_DEVICE.toBuilder().setState(Common.Device.State.DISCONNECTED).build()
    val device = Common.Device.newBuilder()
      .setDeviceId(999)
      .setSerial(UNSUPPORTED_DEVICE_NAME)
      .setApiLevel(AndroidVersion.VersionCodes.KITKAT)
      .setFeatureLevel(AndroidVersion.VersionCodes.KITKAT)
      .setModel(UNSUPPORTED_DEVICE_NAME)
      .setState(Common.Device.State.ONLINE)
      .setUnsupportedReason(UNSUPPORTED_REASON)
      .build()
    val process = Common.Process.newBuilder()
      .setPid(NEW_PROCESS_ID)
      .setDeviceId(device.deviceId)
      .setState(Common.Process.State.ALIVE)
      .setName(FAKE_PROCESS_2)
      .setExposureLevel(if (isTestingProfileable) Common.Process.ExposureLevel.PROFILEABLE else Common.Process.ExposureLevel.DEBUGGABLE)
      .build()
    service.updateDevice(FakeTransportService.FAKE_DEVICE, deadDevice)

    // Set the preferred device to the unsupported one. Loading screen will be displayed.
    endSession()
    updatePreferredProcess(UNSUPPORTED_DEVICE_NAME)
    Truth.assertThat(stageWithToolbarView.stageViewComponent.isVisible).isFalse()
    Truth.assertThat(stageWithToolbarView.stageLoadingComponent.isVisible).isTrue()

    // Preferred device is found. Loading stops and null stage should be displayed with the unsupported reason.
    service.addDevice(device)
    service.addProcess(device, process)
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    studioProfilers.setProcess(device, process)
    Truth.assertThat(stageWithToolbarView.stageViewComponent.isVisible).isTrue()
    Truth.assertThat(stageWithToolbarView.stageLoadingComponent.isVisible).isFalse()
  }

  @Test
  fun nonTimelineStageHidesTimelineNavigationToolbar() {
    studioProfilers.stage = FakeStage(studioProfilers, null, false)
    Truth.assertThat(stageWithToolbarView.timelineNavigationToolbar.isVisible).isFalse()
  }

  @Test
  fun timelineStageShowsTimelineNavigationToolbar() {
    // The default stage is NullMonitorStage.
    Truth.assertThat(stageWithToolbarView.timelineNavigationToolbar.isVisible).isTrue()
  }

  private fun startSessionWithNewDeviceAndProcess() {
    val onlineDevice = Common.Device.newBuilder()
      .setDeviceId(NEW_DEVICE_ID.toLong())
      .setFeatureLevel(AndroidVersion.VersionCodes.O)
      .setState(Common.Device.State.ONLINE)
      .build()
    val onlineProcess = Common.Process.newBuilder()
      .setPid(NEW_PROCESS_ID)
      .setDeviceId(NEW_DEVICE_ID.toLong())
      .setState(Common.Process.State.ALIVE)
      .setExposureLevel(if (isTestingProfileable) Common.Process.ExposureLevel.PROFILEABLE else Common.Process.ExposureLevel.DEBUGGABLE)
      .build()
    startSession(onlineDevice, onlineProcess)
  }

  private fun startSession(device: Common.Device, process: Common.Process) {
    service.addDevice(device)
    updateProcess(device, process)
  }

  private fun updateProcess(device: Common.Device, process: Common.Process) {
    service.addProcess(device, process)
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    studioProfilers.setProcess(device, process)
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
  }

  private fun updateAgentStatus(pid: Int, agentData: AgentData) {
    val sessionStreamId = studioProfilers.session.streamId
    service.addEventToStream(sessionStreamId, Common.Event.newBuilder()
      .setPid(pid)
      .setKind(Common.Event.Kind.AGENT)
      .setAgentData(agentData)
      .build())
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
  }

  private fun endSession() {
    studioProfilers.sessionsManager.endCurrentSession()
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
  }

  private fun updatePreferredProcess(preferredDeviceName: String) {
    studioProfilers.setPreferredProcess(preferredDeviceName, FAKE_PROCESS_2, null)
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
  }

  private class FakeStudioProfilersView(override val studioProfilers: StudioProfilers,
                                        override val ideProfilerComponents: IdeProfilerComponents) : StudioProfilersView {
    override val component: JComponent
      get() = JPanel()
    override val stageWithToolbarView: StageWithToolbarView
      get() = TODO("Not yet implemented")
    override val stageComponent: JPanel
      get() = TODO("Not yet implemented")
    override val stageView: StageView<*>?
      get() = null
    override fun installCommonMenuItems(component: JComponent) {}
    override fun dispose() {}
  }

  companion object {
    private const val NEW_DEVICE_ID = 1
    private const val NEW_PROCESS_ID = 2
    private const val FAKE_PROCESS_2 = "FakeProcess2"
    private const val UNSUPPORTED_DEVICE_NAME = "UnsupportedDevice"
    private const val UNSUPPORTED_REASON = "This device is unsupported"

    @JvmStatic
    @Parameterized.Parameters
    fun isTestingProfileable() = listOf(false, true)
  }
}

