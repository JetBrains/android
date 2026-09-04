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

import com.android.tools.adtui.StatLabel
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_NAME
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS_NAME
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.SessionProfilersView
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.memory.adapters.FakeCaptureObject
import com.android.tools.profilers.memory.adapters.FakeInstanceObject
import com.android.tools.profilers.memory.adapters.HeapDumpCaptureObject
import com.android.tools.profilers.memory.adapters.ValueObject.ValueType
import com.android.tools.profilers.memory.adapters.classifiers.AllHeapSet
import com.android.tools.profilers.memory.adapters.classifiers.HeapSet
import com.android.tools.profilers.memory.adapters.instancefilters.ActivityFragmentLeakInstanceFilter
import com.android.tools.profilers.memory.adapters.instancefilters.AllClassTypeFilter
import com.android.tools.profilers.memory.adapters.instancefilters.BitmapDuplicationInstanceFilter
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import icons.StudioIcons
import javax.swing.JComponent
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class CapturePanelTest {

  private lateinit var profilers: StudioProfilers
  private lateinit var ideProfilerServices: FakeIdeProfilerServices
  private val myTimer = FakeTimer()
  private val transportService = FakeTransportService(myTimer)

  @Rule @JvmField val grpcChannel = FakeGrpcChannel("MemoryProfilerStageViewTestChannel", transportService)

  @get:Rule val applicationRule = ApplicationRule()

  @get:Rule val disposableRule = DisposableRule()

  @Before
  fun setupBase() {
    ideProfilerServices = FakeIdeProfilerServices()
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideProfilerServices, myTimer)
    profilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null)
  }

  /**
   * Verifies that for a [HeapDumpCaptureObject], selecting a heap accurately computes and displays class counts and shallow sizes in the
   * summary panel, and the panel is visible.
   */
  @Test
  fun `panel shows numbers for selected heap`() {
    val fakeCapture = FakeCaptureObject.Builder().build()
    val capture = mock(HeapDumpCaptureObject::class.java)
    `when`(capture.classDatabase).thenReturn(fakeCapture.classDatabase)
    `when`(capture.activityFragmentLeakFilter).thenReturn(ActivityFragmentLeakInstanceFilter(fakeCapture.classDatabase))
    `when`(capture.bitmapDuplicationFilter).thenReturn(BitmapDuplicationInstanceFilter(emptySet()))
    `when`(capture.supportedClassTypeFilters).thenReturn(setOf(AllClassTypeFilter))
    `when`(capture.instances).thenAnswer { fakeCapture.instances }
    `when`(capture.classifierAttributes).thenReturn(fakeCapture.classifierAttributes)
    `when`(capture.instanceAttributes).thenReturn(fakeCapture.instanceAttributes)
    `when`(capture.isGroupingSupported(any())).thenReturn(true)
    `when`(capture.isDoneLoading).thenReturn(true)
    `when`(capture.isError).thenReturn(false)

    val heap1 = HeapSet(capture, "heap1", 1)
    val heap2 = HeapSet(capture, "heap2", 2)
    val allHeap = AllHeapSet(capture, arrayOf(heap1, heap2)).also { it.clearClassifierSets() }

    val insts1 =
      arrayOf(
        FakeInstanceObject.Builder(fakeCapture, 1, "obj").setHeapId(1).setShallowSize(4).build(),
        FakeInstanceObject.Builder(fakeCapture, 2, "int").setHeapId(1).setShallowSize(8).build(),
        FakeInstanceObject.Builder(fakeCapture, 3, "str").setHeapId(1).setShallowSize(14).build(),
      )
    val insts2 =
      arrayOf(
        FakeInstanceObject.Builder(fakeCapture, 4, "cat").setHeapId(2).setShallowSize(3).build(),
        FakeInstanceObject.Builder(fakeCapture, 5, "dog").setHeapId(2).setShallowSize(5).build(),
        FakeInstanceObject.Builder(fakeCapture, 6, "rat").setHeapId(2).setShallowSize(7).build(),
      )
    val insts = insts1 + insts2
    insts.forEach { allHeap.addDeltaInstanceObject(it) }

    val stage = MainMemoryProfilerStage(profilers, FakeCaptureObjectLoader())
    val selection = MemoryCaptureSelection(profilers.ideServices)
    val profilersView = SessionProfilersView(profilers, FakeIdeProfilerComponents(), disposableRule.disposable)
    val panel =
      CapturePanel(profilersView, selection, null, profilers.timeline.selectionRange, FakeIdeProfilerComponents(), profilers.timeline, true)

    selection.selectCaptureEntry(CaptureEntry(Any()) { capture })
    selection.finishSelectingCaptureObject(capture)

    selection.selectHeapSet(heap1)
    assertThat(panel.component.getStatLabel("Classes")!!.parent.isVisible).isTrue()
    assertThat(panel.component.getStatLabelValue("Classes")).isEqualTo("${insts1.size}")
    assertThat(panel.component.getStatLabelValue("Shallow Size")).isEqualTo("${insts1.sumOf { it.shallowSize }}")

    selection.selectHeapSet(heap2)
    assertThat(panel.component.getStatLabel("Classes")!!.parent.isVisible).isTrue()
    assertThat(panel.component.getStatLabelValue("Classes")).isEqualTo("${insts2.size}")
    assertThat(panel.component.getStatLabelValue("Shallow Size")).isEqualTo("${insts2.sumOf { it.shallowSize }}")

    selection.selectHeapSet(allHeap)
    assertThat(panel.component.getStatLabel("Classes")!!.parent.isVisible).isTrue()
    assertThat(panel.component.getStatLabelValue("Classes")).isEqualTo("${insts.size}")
    assertThat(panel.component.getStatLabelValue("Shallow Size")).isEqualTo("${insts.sumOf { it.shallowSize }}")
  }

  @Test
  fun `panel shows warning icon for issues`() {
    val fakeCapture = FakeCaptureObject.Builder().build()
    fakeCapture.classDatabase.registerClass(1, 0, "android.app.Activity")

    val capture = mock(HeapDumpCaptureObject::class.java)
    `when`(capture.classDatabase).thenReturn(fakeCapture.classDatabase)
    `when`(capture.activityFragmentLeakFilter).thenReturn(ActivityFragmentLeakInstanceFilter(fakeCapture.classDatabase))
    `when`(capture.bitmapDuplicationFilter).thenReturn(BitmapDuplicationInstanceFilter(emptySet()))
    `when`(capture.supportedClassTypeFilters).thenReturn(setOf(AllClassTypeFilter))
    `when`(capture.instances).thenAnswer { fakeCapture.instances }
    `when`(capture.classifierAttributes).thenReturn(fakeCapture.classifierAttributes)
    `when`(capture.instanceAttributes).thenReturn(fakeCapture.instanceAttributes)
    `when`(capture.isGroupingSupported(any())).thenReturn(true)
    `when`(capture.isDoneLoading).thenReturn(true)
    `when`(capture.isError).thenReturn(false)

    val heap = HeapSet(capture, "heap", 1)

    // Add a fake issue (e.g., leaked activity)
    val leakedActivity =
      FakeInstanceObject.Builder(fakeCapture, 1, "android.app.Activity")
        .setHeapId(1)
        .setDepth(1) // Required to be a leak
        .addField("mDestroyed", ValueType.BOOLEAN, true) // Required to be a leak
        .build()

    heap.addDeltaInstanceObject(leakedActivity)

    val stage = MainMemoryProfilerStage(profilers, FakeCaptureObjectLoader())
    val selection = MemoryCaptureSelection(profilers.ideServices)
    val profilersView = SessionProfilersView(profilers, FakeIdeProfilerComponents(), disposableRule.disposable)
    val panel =
      CapturePanel(profilersView, selection, null, profilers.timeline.selectionRange, FakeIdeProfilerComponents(), profilers.timeline, true)

    selection.selectCaptureEntry(CaptureEntry(Any()) { capture })
    selection.finishSelectingCaptureObject(capture)
    selection.selectHeapSet(heap)

    val leakLabel = panel.component.getStatLabel("Leaks")
    assertThat(leakLabel).isNotNull()
    assertThat(leakLabel!!.icon).isEqualTo(StudioIcons.Common.WARNING)
    assertThat(leakLabel.isVisible).isTrue()
  }

  @Test
  fun `panel does not show warning icon for 0 issues`() {
    val fakeCapture = FakeCaptureObject.Builder().build()

    val capture = mock(HeapDumpCaptureObject::class.java)
    `when`(capture.classDatabase).thenReturn(fakeCapture.classDatabase)
    `when`(capture.activityFragmentLeakFilter).thenReturn(ActivityFragmentLeakInstanceFilter(fakeCapture.classDatabase))
    `when`(capture.bitmapDuplicationFilter).thenReturn(BitmapDuplicationInstanceFilter(emptySet()))
    `when`(capture.supportedClassTypeFilters).thenReturn(setOf(AllClassTypeFilter))
    `when`(capture.instances).thenAnswer { fakeCapture.instances }
    `when`(capture.classifierAttributes).thenReturn(fakeCapture.classifierAttributes)
    `when`(capture.instanceAttributes).thenReturn(fakeCapture.instanceAttributes)
    `when`(capture.isGroupingSupported(any())).thenReturn(true)
    `when`(capture.isDoneLoading).thenReturn(true)
    `when`(capture.isError).thenReturn(false)

    val heap = HeapSet(capture, "heap", 1)

    val activity =
      FakeInstanceObject.Builder(fakeCapture, 1, "android.app.Activity")
        .setHeapId(1)
        .setDepth(1)
        .addField("mDestroyed", ValueType.BOOLEAN, false)
        .build()

    heap.addDeltaInstanceObject(activity)

    val stage = MainMemoryProfilerStage(profilers, FakeCaptureObjectLoader())
    val selection = MemoryCaptureSelection(profilers.ideServices)
    val profilersView = SessionProfilersView(profilers, FakeIdeProfilerComponents(), disposableRule.disposable)
    val panel =
      CapturePanel(profilersView, selection, null, profilers.timeline.selectionRange, FakeIdeProfilerComponents(), profilers.timeline, true)

    selection.selectCaptureEntry(CaptureEntry(Any()) { capture })
    selection.finishSelectingCaptureObject(capture)

    selection.selectHeapSet(heap)

    val leakLabel = panel.component.getStatLabel("Leaks")
    assertThat(leakLabel).isNotNull()
    assertThat(leakLabel!!.icon).isNull()
    assertThat(leakLabel.isVisible).isTrue()
  }

  /**
   * Verifies that for non-heap dump captures (such as live allocation tracking), the summary panel remains hidden and does not perform
   * redundant stats calculations.
   */
  @Test
  fun `summary panel is hidden for non-heap dump captures`() {
    val capture = FakeCaptureObject.Builder().build()
    val heap = HeapSet(capture, "heap", 1)
    val selection = MemoryCaptureSelection(profilers.ideServices)
    val profilersView = SessionProfilersView(profilers, FakeIdeProfilerComponents(), disposableRule.disposable)
    val panel =
      CapturePanel(profilersView, selection, null, profilers.timeline.selectionRange, FakeIdeProfilerComponents(), profilers.timeline, true)

    selection.selectCaptureEntry(CaptureEntry(Any()) { capture })
    selection.finishSelectingCaptureObject(capture)
    selection.selectHeapSet(heap)

    val classesLabel = panel.component.getStatLabel("Classes")
    assertThat(classesLabel).isNotNull()
    assertThat(classesLabel!!.parent.isVisible).isFalse()
  }

  companion object {
    fun JComponent.getStatLabel(desc: String): StatLabel? =
      TreeWalker(this).descendantStream().filter { it is StatLabel && it.descText == desc }.map { it as StatLabel }.findFirst().orElse(null)

    fun JComponent.getStatLabelValue(desc: String): String? = getStatLabel(desc)?.numText
  }
}
