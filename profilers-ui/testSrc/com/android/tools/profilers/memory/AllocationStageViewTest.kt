package com.android.tools.profilers.memory

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_ID
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_NAME
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS_NAME
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.cpu.FakeCpuService
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.*
import com.android.tools.profilers.network.FakeNetworkService
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class AllocationStageViewTest(private val isLive: Boolean) {
  private val timer = FakeTimer()
  private val service = FakeMemoryService()
  private val transportService = FakeTransportService(timer)
  @Rule @JvmField
  val grpcChannel = FakeGrpcChannel("LiveAllocationStageTestChannel", service, transportService,
                                    FakeProfilerService(timer), FakeCpuService(), FakeEventService(),
                                    FakeNetworkService.newBuilder().build())
  private lateinit var profilers: StudioProfilers
  private lateinit var stage: AllocationStage
  private lateinit var mockLoader: FakeCaptureObjectLoader
  private lateinit var observer: MemoryAspectObserver
  private lateinit var ideProfilerServices: FakeIdeProfilerServices
  private lateinit var profilersView: StudioProfilersView
  private lateinit var stageView: AllocationStageView

  @Before
  fun setupBase() {
    ideProfilerServices = FakeIdeProfilerServices()
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideProfilerServices, timer)
    profilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null)
    ideProfilerServices.enableEventsPipeline(true)
    mockLoader = FakeCaptureObjectLoader()
    stage =
      if (isLive) AllocationStage.makeLiveStage(profilers, mockLoader)
      else AllocationStage.makeStaticStage(profilers, minTrackingTimeUs = 1.0, maxTrackingTimeUs = 5.0)
    observer = MemoryAspectObserver(stage.aspect, stage.captureSelection.aspect)
    profilersView = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    stageView = AllocationStageView(profilersView, stage)

    // Advance the clock to make sure StudioProfilers has a chance to select device + process.
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    profilers.stage = stage
  }

  @Test
  fun `stage view has a timeline and important buttons`() {
    stageView.apply {
      val descendants = (TreeWalker(component).descendants() + TreeWalker(toolbar).descendants()).toSet()
      val expected =
        if (isLive) listOf(timelineComponent, samplingMenu, selectAllButton, forceGcButton, stopButton)
        else listOf(timelineComponent, selectAllButton)
      val unexpected =
        if (isLive) listOf()
        else listOf(samplingMenu, forceGcButton, stopButton)
      assertThat(descendants).containsAllIn(expected)
      assertThat(expected.all { it.isVisible }).isTrue()
      assertThat(unexpected.all { !it.isVisible || it !in descendants })
    }
  }

  @Test
  fun `sampling menu updates text when sampling mode changes`() {
    tick()
    assertThat(stageView.samplingMenu.combobox.selectedItem).isEqualTo(SAMPLED)

    requestFullSampling()
    tick()
    assertThat(stageView.samplingMenu.combobox.selectedItem).isEqualTo(FULL)
  }

  @Test
  fun `back button leads to main memory stage`() {
    stage.stopTracking()
    profilersView.backButton.doClick()
    assertThat(profilers.stage).isInstanceOf(MainMemoryProfilerStage::class.java)
  }

  private fun tick() = timer.tick(FakeTimer.ONE_SECOND_IN_NS)

  private fun requestFullSampling() =
    transportService.addEventToStream(
      FAKE_DEVICE_ID, ProfilersTestData.generateMemoryAllocSamplingData(FAKE_PROCESS.pid, 1, 1).build())

  companion object {
    @Parameterized.Parameters @JvmStatic
    fun isLiveAllocationStage() = listOf(false, true)
  }
 }