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
package com.android.tools.profilers.memory

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_NAME
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS_NAME
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.memory.adapters.CaptureObject
import com.android.tools.profilers.memory.adapters.FakeCaptureObject
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.testFramework.ApplicationRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.function.Supplier
import javax.swing.JLabel

class MemoryCaptureStageViewTest {

  private lateinit var profilers: StudioProfilers
  private lateinit var mockLoader: FakeCaptureObjectLoader
  private val myTimer = FakeTimer()
  private lateinit var ideProfilerServices: FakeIdeProfilerServices
  private lateinit var profilersView: StudioProfilersView

  private val transportService = FakeTransportService(myTimer)

  @get:Rule
  val appRule = ApplicationRule()

  @Rule
  @JvmField
  val grpcChannel = FakeGrpcChannel("MemoryProfilerStageViewTestChannel", transportService)

  @Before
  fun setupBase() {
    ideProfilerServices = FakeIdeProfilerServices()
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideProfilerServices, myTimer)
    profilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null)
    profilersView = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    mockLoader = FakeCaptureObjectLoader()

    // Advance the clock to make sure StudioProfilers has a chance to select device + process.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
  }

  @Test
  fun testNavigationButtonNameIsCaptureInNewUi() {
    // Load a fake capture
    val fakeCapture = makeFakeCapture()
    val stage = createStageWithCaptureLoaded(fakeCapture)
    val view = MemoryCaptureStageView(profilersView, stage)
    val walker = TreeWalker(view.toolbar)
    val label = walker.descendantStream().filter { c -> c is JLabel }.findFirst().get() as JLabel
    assertThat(label.text).startsWith(fakeCapture.name)
  }

  @Test
  fun `cancelling in large heap dump dialog cancels loading`() {
    val capture = makeFakeCapture { setCanSafelyLoad(false) }
    ideProfilerServices.setShouldProceedYesNoDialog(false)
    assertThat(createStageWithCaptureLoaded(capture).captureSelection.selectedCapture).isNull()
  }

  @Test
  fun `confirming in large heap dump dialog proceeds loading`() {
    val capture = makeFakeCapture { setCanSafelyLoad(false) }
    ideProfilerServices.setShouldProceedYesNoDialog(true)
    assertThat(createStageWithCaptureLoaded(capture).captureSelection.selectedCapture).isEqualTo(capture)
  }

  private fun createStageWithCaptureLoaded(capture: CaptureObject) = MemoryCaptureStage(
    profilers,
    mockLoader,
    CaptureDurationData(1, false, false, CaptureEntry(Any(), Supplier { capture })),
    MoreExecutors.directExecutor()
  ).apply {
    enter()
    captureSelection.refreshSelectedHeap()
  }

  private fun makeFakeCapture(prepare: FakeCaptureObject.Builder.() -> Unit = {}) = FakeCaptureObject.Builder()
    .setCaptureName("SAMPLE_CAPTURE1")
    .setStartTime(0)
    .setEndTime(10)
    .setInfoMessage("Foo")
    .apply(prepare)
    .build()
}