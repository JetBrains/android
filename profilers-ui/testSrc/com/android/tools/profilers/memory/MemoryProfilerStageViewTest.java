/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profilers.memory;

import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE;
import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_ID;
import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS;
import static com.android.tools.profiler.proto.Common.SessionMetaData.SessionType.MEMORY_CAPTURE;
import static com.android.tools.profilers.memory.ClassGrouping.ARRANGE_BY_CLASS;
import static com.android.tools.profilers.memory.ClassGrouping.ARRANGE_BY_PACKAGE;
import static com.android.tools.profilers.memory.MemoryProfilerTestUtils.findChildClassSetNodeWithClassName;
import static com.android.tools.profilers.memory.MemoryProfilerTestUtils.findDescendantClassSetNodeWithInstance;
import static com.android.tools.profilers.memory.MemoryProfilerTestUtils.getRootClassifierSet;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.RangeTooltipComponent;
import com.android.tools.adtui.TreeWalker;
import com.android.tools.adtui.chart.linechart.DurationDataRenderer;
import com.android.tools.adtui.flat.FlatSeparator;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Memory.HeapDumpInfo;
import com.android.tools.profiler.proto.Memory.MemoryAllocSamplingData;
import com.android.tools.profiler.proto.Memory.TrackStatus.Status;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationSamplingRateEvent;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profilers.FakeIdeProfilerComponents;
import com.android.tools.profilers.FakeProfilerService;
import com.android.tools.profilers.ProfilerAspect;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.ReferenceWalker;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.StudioProfilersView;
import com.android.tools.profilers.cpu.FakeCpuService;
import com.android.tools.profilers.event.FakeEventService;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.FakeCaptureObject;
import com.android.tools.profilers.memory.adapters.FakeInstanceObject;
import com.android.tools.profilers.memory.adapters.HeapDumpCaptureObject;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.android.tools.profilers.memory.adapters.LegacyAllocationCaptureObject;
import com.android.tools.profilers.memory.adapters.classifiers.ClassSet;
import com.android.tools.profilers.memory.adapters.classifiers.ClassifierSet;
import com.android.tools.profilers.memory.adapters.classifiers.HeapSet;
import com.android.tools.profilers.network.FakeNetworkService;
import com.android.tools.profilers.sessions.SessionsManager;
import com.android.tools.profilers.stacktrace.ContextMenuItem;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import icons.StudioIcons;
import java.awt.Component;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.ComboBoxModel;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.tree.TreePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;


@RunWith(Parameterized.class)
public final class MemoryProfilerStageViewTest extends MemoryProfilerTestBase {
  @Parameterized.Parameters
  public static Collection<Boolean> useNewEventPipelineParameter() {
    return Arrays.asList(false, true);
  }

  @NotNull private final FakeTransportService myTransportService = new FakeTransportService(myTimer);
  @NotNull private final FakeMemoryService myService = new FakeMemoryService(myTransportService);
  @Rule
  public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("MemoryProfilerStageViewTestChannel", myTransportService, myService, new FakeProfilerService(myTimer),
                        new FakeCpuService(), new FakeEventService(), new FakeNetworkService.Builder().build());
  private StudioProfilersView myProfilersView;
  private final boolean myUnifiedPipeline;

  public MemoryProfilerStageViewTest(boolean useNewEventPipeline) {
    myUnifiedPipeline = useNewEventPipeline;
  }

  @Override
  protected FakeGrpcChannel getGrpcChannel() {
    return myGrpcChannel;
  }

  @Override
  protected void onProfilersCreated(StudioProfilers profilers) {
    myIdeProfilerServices.enableEventsPipeline(myUnifiedPipeline);
    myProfilersView = new StudioProfilersView(profilers, new FakeIdeProfilerComponents());
  }

  @Before
  public void init() {
    myIdeProfilerServices.enableSeparateHeapDumpUi(false);
  }

  @Test
  public void testCaptureAndHeapView() {
    myIdeProfilerServices.enableSeparateHeapDumpUi(true);

    final String sampleClassName1 = "SAMPLE_CLASS1";
    final String sampleClassName2 = "SAMPLE_CLASS2";

    Map<Integer, String> heapIdMap = ImmutableMap.of(0, "heap1", 1, "heap2");

    MemoryProfilerStageView stageView = (MemoryProfilerStageView)myProfilersView.getStageView();

    FakeCaptureObject fakeCapture1 = new FakeCaptureObject.Builder()
      .setCaptureName("SAMPLE_CAPTURE1")
      .setHeapIdToNameMap(heapIdMap)
      .setStartTime(5)
      .setEndTime(10)
      .build();
    InstanceObject fakeInstance1 = new FakeInstanceObject.Builder(fakeCapture1, 1, sampleClassName1)
      .setName("SAMPLE_INSTANCE1")
      .setHeapId(0)
      .setDepth(4)
      .setShallowSize(5)
      .setRetainedSize(6)
      .build();
    InstanceObject fakeInstance2 = new FakeInstanceObject.Builder(fakeCapture1, 2, sampleClassName2)
      .setName("SAMPLE_INSTANCE2")
      .setDepth(1)
      .setShallowSize(2)
      .setRetainedSize(3)
      .build();
    fakeCapture1.addInstanceObjects(ImmutableSet.of(fakeInstance1, fakeInstance2));

    FakeCaptureObject fakeCapture2 = new FakeCaptureObject.Builder()
      .setCaptureName("SAMPLE_CAPTURE2")
      .setHeapIdToNameMap(heapIdMap)
      .setStartTime(5)
      .setEndTime(10)
      .build();
    InstanceObject fakeInstance3 = new FakeInstanceObject.Builder(fakeCapture2, 1, sampleClassName1)
      .setName("SAMPLE_INSTANCE1")
      .setHeapId(0)
      .setDepth(4)
      .setShallowSize(5)
      .setRetainedSize(6)
      .build();
    InstanceObject fakeInstance4 = new FakeInstanceObject.Builder(fakeCapture2, 2, sampleClassName2)
      .setName("SAMPLE_INSTANCE2")
      .setDepth(1)
      .setShallowSize(2)
      .setRetainedSize(3)
      .build();
    fakeCapture2.addInstanceObjects(ImmutableSet.of(fakeInstance3, fakeInstance4));

    MemoryClassifierView classifierView = stageView.getClassifierView();

    JComponent captureComponent = stageView.getChartCaptureSplitter().getSecondComponent();
    assertThat(captureComponent).isNull();
    JComponent instanceComponent = stageView.getMainSplitter().getSecondComponent();
    assertThat(instanceComponent.isVisible()).isFalse();

    assertView(null, null, null, null, false);

    myStage.selectCaptureDuration(makeCaptureDurationData(fakeCapture1), null);
    assertView(fakeCapture1, null, null, null, true);
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0, 0);
    myMockLoader.runTask();

    JTree classifierTree = classifierView.getTree();
    assertThat(classifierTree).isNotNull();
    HeapSet selectedHeap = myStage.getCaptureSelection().getSelectedHeapSet();
    assertThat(selectedHeap).isNotNull();
    assertView(fakeCapture1, selectedHeap, null, null, false);
    myAspectObserver.assertAndResetCounts(0, 0, 1, 0, 1, 0, 0, 0);

    // Tests selecting a capture which loads immediately.
    myMockLoader.setReturnImmediateFuture(true);
    myStage.selectCaptureDuration(makeCaptureDurationData(fakeCapture2), null);
    classifierTree = classifierView.getTree();
    assertThat(classifierTree).isNotNull();
    selectedHeap = myStage.getCaptureSelection().getSelectedHeapSet();
    // 2 heap changes: 1 from changing the capture, the other from the auto-selection after the capture is loaded.
    assertView(fakeCapture2, selectedHeap, null, null, false);
    myAspectObserver.assertAndResetCounts(0, 1, 1, 0, 2, 0, 0, 0);

    stageView.getHeapView().getHeapComboBox().setSelectedItem(fakeCapture2.getHeapSet(0));
    assertSelection(fakeCapture2, fakeCapture2.getHeapSet(0), null, null);
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0, 0);

    ClassSet selectedClassSet =
      findDescendantClassSetNodeWithInstance(getRootClassifierSet(classifierTree).getAdapter(), fakeInstance3);
    assertThat(selectedClassSet).isNotNull();
    myStage.getCaptureSelection().selectClassSet(selectedClassSet);
    assertView(fakeCapture2, fakeCapture2.getHeapSet(0), selectedClassSet, null, false);
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 1, 0, 0);

    assertThat(myStage.getCaptureSelection().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    myStage.getCaptureSelection().setClassGrouping(ARRANGE_BY_PACKAGE);
    assertThat(stageView.getClassGrouping().getComponent().getSelectedItem()).isEqualTo(ARRANGE_BY_PACKAGE);
    myAspectObserver.assertAndResetCounts(0, 0, 0, 1, 0, 1, 0, 0);

    MemoryObjectTreeNode<ClassifierSet> memoryClassRoot = getRootClassifierSet(classifierTree);
    MemoryObjectTreeNode<ClassSet> targetSet = findChildClassSetNodeWithClassName(memoryClassRoot, sampleClassName1);
    classifierTree.setSelectionPath(new TreePath(new Object[]{memoryClassRoot, targetSet}));
    assertSelection(fakeCapture2, fakeCapture2.getHeapSet(0), targetSet.getAdapter(), null);

    myStage.getCaptureSelection().selectInstanceObject(fakeInstance3);
    assertSelection(fakeCapture2, fakeCapture2.getHeapSet(0), targetSet.getAdapter(), fakeInstance3);
    assertView(fakeCapture2, fakeCapture2.getHeapSet(0), targetSet.getAdapter(), fakeInstance3, false);

    myStage.selectCaptureDuration(null, null);
    assertView(null, null, null, null, false);
  }

  @Test
  public void testCaptureAndHeapViewLegacy() {
    final String sampleClassName1 = "SAMPLE_CLASS1";
    final String sampleClassName2 = "SAMPLE_CLASS2";

    Map<Integer, String> heapIdMap = ImmutableMap.of(0, "heap1", 1, "heap2");

    MemoryProfilerStageView stageView = (MemoryProfilerStageView)myProfilersView.getStageView();

    FakeCaptureObject fakeCapture1 =
      new FakeCaptureObject.Builder().setCaptureName("SAMPLE_CAPTURE1").setHeapIdToNameMap(heapIdMap).setStartTime(5).setEndTime(10).build();
    InstanceObject fakeInstance1 =
      new FakeInstanceObject.Builder(fakeCapture1, 1, sampleClassName1).setName("SAMPLE_INSTANCE1").setHeapId(0).setDepth(4)
        .setShallowSize(5).setRetainedSize(6).build();
    InstanceObject fakeInstance2 =
      new FakeInstanceObject.Builder(fakeCapture1, 2, sampleClassName2).setName("SAMPLE_INSTANCE2").setDepth(1).setShallowSize(2)
        .setRetainedSize(3).build();
    fakeCapture1.addInstanceObjects(ImmutableSet.of(fakeInstance1, fakeInstance2));

    FakeCaptureObject fakeCapture2 =
      new FakeCaptureObject.Builder().setCaptureName("SAMPLE_CAPTURE2").setHeapIdToNameMap(heapIdMap).setStartTime(5).setEndTime(10).build();
    InstanceObject fakeInstance3 =
      new FakeInstanceObject.Builder(fakeCapture2, 1, sampleClassName1).setName("SAMPLE_INSTANCE1").setHeapId(0).setDepth(4)
        .setShallowSize(5).setRetainedSize(6).build();
    InstanceObject fakeInstance4 =
      new FakeInstanceObject.Builder(fakeCapture2, 2, sampleClassName2).setName("SAMPLE_INSTANCE2").setDepth(1).setShallowSize(2)
        .setRetainedSize(3).build();
    fakeCapture2.addInstanceObjects(ImmutableSet.of(fakeInstance3, fakeInstance4));

    MemoryClassifierView classifierView = stageView.getClassifierView();

    JComponent captureComponent = stageView.getChartCaptureSplitter().getSecondComponent();
    assertThat(captureComponent).isNull();
    JComponent instanceComponent = stageView.getMainSplitter().getSecondComponent();
    assertThat(instanceComponent.isVisible()).isFalse();

    assertViewLegacy(null, null, null, null, false);

    myStage.selectCaptureDuration(makeCaptureDurationData(fakeCapture1), null);
    assertViewLegacy(fakeCapture1, null, null, null, true);
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0, 0);
    myMockLoader.runTask();

    JTree classifierTree = classifierView.getTree();
    assertThat(classifierTree).isNotNull();
    HeapSet selectedHeap = myStage.getCaptureSelection().getSelectedHeapSet();
    assertThat(selectedHeap).isNotNull();
    assertViewLegacy(fakeCapture1, selectedHeap, null, null, false);
    myAspectObserver.assertAndResetCounts(0, 0, 1, 0, 1, 0, 0, 0);

    // Tests selecting a capture which loads immediately.
    myMockLoader.setReturnImmediateFuture(true);
    myStage.selectCaptureDuration(makeCaptureDurationData(fakeCapture2), null);
    classifierTree = classifierView.getTree();
    assertThat(classifierTree).isNotNull();
    selectedHeap = myStage.getCaptureSelection().getSelectedHeapSet();
    // 2 heap changes: 1 from changing the capture, the other from the auto-selection after the capture is loaded.
    assertViewLegacy(fakeCapture2, selectedHeap, null, null, false);
    myAspectObserver.assertAndResetCounts(0, 1, 1, 0, 2, 0, 0, 0);

    stageView.getHeapView().getHeapComboBox().setSelectedItem(fakeCapture2.getHeapSet(0));
    assertSelection(fakeCapture2, fakeCapture2.getHeapSet(0), null, null);
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0, 0);

    ClassSet selectedClassSet = findDescendantClassSetNodeWithInstance(getRootClassifierSet(classifierTree).getAdapter(), fakeInstance3);
    assertThat(selectedClassSet).isNotNull();
    myStage.getCaptureSelection().selectClassSet(selectedClassSet);
    assertViewLegacy(fakeCapture2, fakeCapture2.getHeapSet(0), selectedClassSet, null, false);
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 1, 0, 0);

    assertThat(myStage.getCaptureSelection().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    myStage.getCaptureSelection().setClassGrouping(ARRANGE_BY_PACKAGE);
    assertThat(stageView.getClassGrouping().getComponent().getSelectedItem()).isEqualTo(ARRANGE_BY_PACKAGE);
    myAspectObserver.assertAndResetCounts(0, 0, 0, 1, 0, 1, 0, 0);

    MemoryObjectTreeNode<ClassifierSet> memoryClassRoot = getRootClassifierSet(classifierTree);
    MemoryObjectTreeNode<ClassSet> targetSet = findChildClassSetNodeWithClassName(memoryClassRoot, sampleClassName1);
    classifierTree.setSelectionPath(new TreePath(new Object[]{memoryClassRoot, targetSet}));
    assertSelection(fakeCapture2, fakeCapture2.getHeapSet(0), targetSet.getAdapter(), null);

    myStage.getCaptureSelection().selectInstanceObject(fakeInstance3);
    assertSelection(fakeCapture2, fakeCapture2.getHeapSet(0), targetSet.getAdapter(), fakeInstance3);
    assertViewLegacy(fakeCapture2, fakeCapture2.getHeapSet(0), targetSet.getAdapter(), fakeInstance3, false);

    myStage.selectCaptureDuration(null, null);
    assertViewLegacy(null, null, null, null, false);
  }

  private static CaptureDurationData<CaptureObject> makeCaptureDurationData(CaptureObject capture) {
    return new CaptureDurationData<>(1,
                                     false,
                                     false,
                                     new CaptureEntry<>(new Object(), () -> capture));
  }


  @Test
  public void testCaptureElapsedTime() {
    final int startTime = 1;
    final int endTime = 5;
    long deltaUs = TimeUnit.SECONDS.toMicros(endTime - startTime);
    long startTimeNs = TimeUnit.SECONDS.toNanos(startTime);
    long endTimeNs = TimeUnit.SECONDS.toNanos(endTime);

    assertThat(myStage.isTrackingAllocations()).isFalse();

    MemoryProfilerStageView stageView = (MemoryProfilerStageView)myProfilersView.getStageView();
    myTimer.setCurrentTimeNs(TimeUnit.SECONDS.toNanos(startTime));
    assertThat(stageView.getCaptureElapsedTimeLabel().getText()).isEmpty();

    MemoryProfilerTestUtils
      .startTrackingHelper(myStage, myUnifiedPipeline, myTransportService, myService, myTimer, startTimeNs, Status.SUCCESS, true);
    assertThat(stageView.getCaptureElapsedTimeLabel().getText())
      .isEqualTo(TimeFormatter.getSemiSimplifiedClockString(0));

    myTimer.setCurrentTimeNs(TimeUnit.SECONDS.toNanos(endTime));
    myStage.getCaptureSelection().getAspect().changed(CaptureSelectionAspect.CURRENT_CAPTURE_ELAPSED_TIME);
    assertThat(stageView.getCaptureElapsedTimeLabel().getText())
      .isEqualTo(TimeFormatter.getSemiSimplifiedClockString(deltaUs));

    // Triggering a heap dump should not affect the allocation recording duration
    MemoryProfilerTestUtils.heapDumpHelper(myStage, myUnifiedPipeline, myTransportService, myService, myTimer.getCurrentTimeNs(),
                                           Long.MAX_VALUE, Memory.HeapDumpStatus.Status.SUCCESS);
    myStage.getCaptureSelection().getAspect().changed(CaptureSelectionAspect.CURRENT_CAPTURE_ELAPSED_TIME);
    assertThat(stageView.getCaptureElapsedTimeLabel().getText()).isEqualTo(TimeFormatter.getSemiSimplifiedClockString(deltaUs));

    MemoryProfilerTestUtils
      .stopTrackingHelper(myStage, myUnifiedPipeline, myTransportService, myService, myTimer, startTimeNs, endTimeNs, Status.SUCCESS, true);
    assertThat(stageView.getCaptureElapsedTimeLabel().getText()).isEmpty();
  }

  @Ignore("b/158253502")
  @Test
  public void testLoadingTooltipViewWithStrongReference() throws Exception {
    MemoryProfilerStageView stageView = (MemoryProfilerStageView)myProfilersView.getStageView();
    myStage.setTooltip(new MemoryUsageTooltip(myStage));
    ReferenceWalker referenceWalker = new ReferenceWalker(stageView);
    referenceWalker.assertReachable(MemoryUsageTooltipView.class);
  }

  @Test
  public void testLoadingNewCaptureWithExistingLoadLegacy() {
    Map<Integer, String> heapIdMap = ImmutableMap.of(0, "heap1", 1, "heap2");

    FakeCaptureObject fakeCapture1 =
      new FakeCaptureObject.Builder().setCaptureName("SAMPLE_CAPTURE1").setHeapIdToNameMap(heapIdMap).setStartTime(5).setEndTime(10).build();
    FakeCaptureObject fakeCapture2 =
      new FakeCaptureObject.Builder().setCaptureName("SAMPLE_CAPTURE2").setHeapIdToNameMap(heapIdMap).setStartTime(10).setEndTime(15)
        .build();
    InstanceObject fakeInstance1 =
      new FakeInstanceObject.Builder(fakeCapture2, 1, "SAMPLE_CLASS").setName("SAMPLE_INSTANCE1").setDepth(4).setShallowSize(5)
        .setRetainedSize(6).build();
    fakeCapture2.addInstanceObjects(ImmutableSet.of(fakeInstance1));

    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> fakeCapture1)),
                             null);
    assertViewLegacy(fakeCapture1, null, null, null, true);
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0, 0);

    // Select a new capture before the first is loaded.
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> fakeCapture2)),
                             null);
    assertViewLegacy(fakeCapture2, null, null, null, true);
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0, 0);
    myMockLoader.runTask();
    assertViewLegacy(fakeCapture2, fakeCapture2.getHeapSet(0), null, null, false);
    myAspectObserver.assertAndResetCounts(0, 0, 1, 0, 1, 0, 0, 0);
  }

  @Test
  public void testLoadingNewCaptureWithExistingLoad() {
    myIdeProfilerServices.enableSeparateHeapDumpUi(true);
    Map<Integer, String> heapIdMap = ImmutableMap.of(0, "heap1", 1, "heap2");

    FakeCaptureObject fakeCapture1 =
      new FakeCaptureObject.Builder().setCaptureName("SAMPLE_CAPTURE1").setHeapIdToNameMap(heapIdMap).setStartTime(5).setEndTime(10).build();
    FakeCaptureObject fakeCapture2 =
      new FakeCaptureObject.Builder().setCaptureName("SAMPLE_CAPTURE2").setHeapIdToNameMap(heapIdMap).setStartTime(10).setEndTime(15)
        .build();
    InstanceObject fakeInstance1 =
      new FakeInstanceObject.Builder(fakeCapture2, 1, "SAMPLE_CLASS").setName("SAMPLE_INSTANCE1").setDepth(4).setShallowSize(5)
        .setRetainedSize(6).build();
    fakeCapture2.addInstanceObjects(ImmutableSet.of(fakeInstance1));

    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> fakeCapture1)),
                             null);
    assertViewLegacy(fakeCapture1, null, null, null, true);
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0, 0);

    // Select a new capture before the first is loaded.
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> fakeCapture2)),
                             null);
    assertViewLegacy(fakeCapture2, null, null, null, true);
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0, 0);
    myMockLoader.runTask();
    assertViewLegacy(fakeCapture2, fakeCapture2.getHeapSet(0), null, null, false);
    myAspectObserver.assertAndResetCounts(0, 0, 1, 0, 1, 0, 0, 0);
  }

  @Test
  public void testTooltipComponentIsFirstChild() {
    MemoryProfilerStageView stageView = (MemoryProfilerStageView)myProfilersView.getStageView();
    TreeWalker treeWalker = new TreeWalker(stageView.getComponent());
    Component tooltipComponent = treeWalker.descendantStream().filter(c -> c instanceof RangeTooltipComponent).findFirst().get();
    assertThat(tooltipComponent.getParent().getComponent(0)).isEqualTo(tooltipComponent);
  }

  @Test
  public void testLoadHeapDumpFromFile() throws Exception {
    // TODO b/136292864
    Assume.assumeFalse(myUnifiedPipeline);

    SessionsManager sessionsManager = myProfilers.getSessionsManager();

    // Create a temp file
    String data = "random_string_~!@#$%^&*()_+";
    // The '.' in the file name from the following line is useful to test we can handle file names with
    // multiple dots correctly.
    File file = FileUtil.createTempFile("fake.heap.dump", ".hprof", false);
    PrintWriter printWriter = new PrintWriter(file);
    printWriter.write(data);
    printWriter.close();

    // Import heap dump from file
    assertThat(sessionsManager.importSessionFromFile(file)).isTrue();
    Common.Session session = sessionsManager.getSelectedSession();
    long dumpTime = session.getStartTimestamp();
    Transport.BytesRequest request = Transport.BytesRequest.newBuilder()
      .setStreamId(session.getStreamId())
      .setId(Long.toString(dumpTime))
      .build();
    assertThat(myProfilers.getStage()).isInstanceOf(MemoryProfilerStage.class);
    Transport.BytesResponse response = myProfilers.getClient().getTransportClient().getBytes(request);

    assertThat(response.getContents()).isEqualTo(ByteString.copyFrom(data, Charset.defaultCharset()));
  }

  /**
   * The following is a regression test against implementation where 'myRangeSelectionComponent' in MemoryProfilerStageView is a null
   * pointer when profiler is importing a heap dump file. (Regression bug: b/117796712)
   */
  @Ignore("Scenario no longer possible or relevant for separate heap dump stage")
  @Test
  public void testLoadHeapDumpFromFileFinishLoading() throws Exception {
    myIdeProfilerServices.enableSeparateHeapDumpUi(true);
    // TODO b/136292864
    Assume.assumeFalse(myUnifiedPipeline);

    // Make sure the second loading runs after the first due to b/151245410
    testFirstLoadsCaptureThenStartSecond(
      () -> {
        try {
          SessionsManager sessionsManager = myProfilers.getSessionsManager();
          // Create a temp file
          String data = "random_string_~!@#$%^&*()_+";
          File file = FileUtil.createTempFile("fake_heap_dump", ".hprof", false);
          PrintWriter printWriter = new PrintWriter(file);
          printWriter.write(data);
          printWriter.close();
          // Import heap dump from file
          assertThat(sessionsManager.importSessionFromFile(file)).isTrue();
          assertThat(sessionsManager.getSelectedSessionMetaData().getType()).isEqualTo(MEMORY_CAPTURE);
          assertThat(myProfilers.getStage()).isInstanceOf(MemoryProfilerStage.class);
          MemoryProfilerStage stage = (MemoryProfilerStage)myProfilers.getStage();
          assertThat(stage.isMemoryCaptureOnly()).isTrue();
        } catch (IOException e) {
          throw new RuntimeException("IO");
        }
      },
      () -> {
        MemoryProfilerStage stage = (MemoryProfilerStage)myProfilers.getStage();
        // Create a FakeCaptureObject and then call selectCaptureDuration().
        // selectCaptureDuration() would indirectly fire CURRENT_LOADING_CAPTURE aspect which will
        // trigger captureObjectChanged().
        // Because isDoneLoading() returns true by default in the FakeCaptureObject,
        // captureObjectChanged() will call captureObjectFinishedLoading()
        // which would execute the logic that had a null pointer exception as reported by b/117796712.
        FakeCaptureObject captureObj =
          new FakeCaptureObject.Builder().setHeapIdToNameMap(ImmutableMap.of(0, "default", 1, "app")).build();
        FakeInstanceObject instanceObject = new FakeInstanceObject.Builder(captureObj, 1, "SAMPLE_CLASS1").setHeapId(0).build();
        captureObj.addInstanceObjects(ImmutableSet.of(instanceObject));
        stage.selectCaptureDuration(
          new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> captureObj)),
          null);
      }
    );
  }

  @Test
  public void testLoadHeapDumpFromFileFinishLoadingLegacy() throws Exception {
    // TODO b/136292864
    Assume.assumeFalse(myUnifiedPipeline);

    // Make sure the second loading runs after the first due to b/151245410
    testFirstLoadsCaptureThenStartSecond(
      () -> {
        try {
          SessionsManager sessionsManager = myProfilers.getSessionsManager();
          // Create a temp file
          String data = "random_string_~!@#$%^&*()_+";
          File file = FileUtil.createTempFile("fake_heap_dump", ".hprof", false);
          PrintWriter printWriter = new PrintWriter(file);
          printWriter.write(data);
          printWriter.close();
          // Import heap dump from file
          assertThat(sessionsManager.importSessionFromFile(file)).isTrue();
          assertThat(sessionsManager.getSelectedSessionMetaData().getType()).isEqualTo(MEMORY_CAPTURE);
          assertThat(myProfilers.getStage()).isInstanceOf(MemoryProfilerStage.class);
          MemoryProfilerStage stage = (MemoryProfilerStage)myProfilers.getStage();
          assertThat(stage.isMemoryCaptureOnly()).isTrue();
        } catch (IOException e) {
          throw new RuntimeException(e.getMessage());
        }
      },
      () -> {
        MemoryProfilerStage stage = (MemoryProfilerStage)myProfilers.getStage();
        // Create a FakeCaptureObject and then call selectCaptureDuration().
        // selectCaptureDuration() would indirectly fire CURRENT_LOADING_CAPTURE aspect which will trigger captureObjectChanged().
        // Because isDoneLoading() returns true by default in the FakeCaptureObject, captureObjectChanged() will call captureObjectFinishedLoading()
        // which would execute the logic that had a null pointer exception as reported by b/117796712.
        FakeCaptureObject captureObj = new FakeCaptureObject.Builder()
          .setHeapIdToNameMap(ImmutableMap.of(0, "default", 1, "app")).build();
        FakeInstanceObject instanceObject =
          new FakeInstanceObject.Builder(captureObj, 1, "SAMPLE_CLASS1").setHeapId(0).build();
        captureObj.addInstanceObjects(ImmutableSet.of(instanceObject));
        CaptureEntry<CaptureObject> entry = new CaptureEntry<>(new Object(), () -> captureObj);
        stage.selectCaptureDuration(new CaptureDurationData<>(1, false, false, entry),
                                    null);
      }
    );
  }

  private void testFirstLoadsCaptureThenStartSecond(Runnable first, Runnable second) throws InterruptedException {
    CountDownLatch firstFinished = new CountDownLatch(1);
    CountDownLatch secondStarted = new CountDownLatch(1);
    myProfilers.addDependency(myAspectObserver)
      .onChange(ProfilerAspect.STAGE, () -> {
        myProfilers.removeDependencies(myAspectObserver);
        MemoryProfilerStage stage = (MemoryProfilerStage)myProfilers.getStage();
        stage.getCaptureSelection().getAspect().addDependency(myAspectObserver)
          .onChange(CaptureSelectionAspect.CURRENT_LOADED_CAPTURE, () -> {
            try {
              assertThat(firstFinished.await(120, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
              throw new RuntimeException(e.getMessage());
            }
            stage.getAspect().removeDependencies(myAspectObserver);
            second.run();
            secondStarted.countDown();
          });
      });
    first.run();
    firstFinished.countDown();
    assertThat(secondStarted.await(120, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  public void testLoadLegacyAllocationRecordsFromFile() throws Exception {
    // TODO b/136292864
    Assume.assumeFalse(myUnifiedPipeline);

    SessionsManager sessionsManager = myProfilers.getSessionsManager();

    // Create and import a temp allocation records file
    File file = FileUtil.createTempFile("fake_allocation_records", ".alloc", true);
    assertThat(sessionsManager.importSessionFromFile(file)).isTrue();

    assertThat(myProfilers.getStage()).isInstanceOf(MemoryProfilerStage.class);
    MemoryProfilerStage stage = (MemoryProfilerStage)myProfilers.getStage();
    assertThat(stage.getCaptureSelection().getSelectedCapture()).isInstanceOf(LegacyAllocationCaptureObject.class);
  }

  @Test
  public void testContextMenu() {
    MemoryProfilerStageView stageView = (MemoryProfilerStageView)myProfilersView.getStageView();
    FakeIdeProfilerComponents ideProfilerComponents = (FakeIdeProfilerComponents)stageView.getIdeComponents();

    ideProfilerComponents.clearContextMenuItems();
    new MemoryProfilerStageView(myProfilersView, myStage);
    ContextMenuItem[] items = ideProfilerComponents.getAllContextMenuItems().toArray(new ContextMenuItem[0]);
    assertThat(items.length).isEqualTo(13);
    assertThat(items[0].getText()).isEqualTo("Export...");
    assertThat(items[1]).isEqualTo(ContextMenuItem.SEPARATOR);
    assertThat(items[2].getText()).isEqualTo("Record allocations");
    assertThat(items[3].getText()).isEqualTo("Stop recording");
    assertThat(items[4].getText()).isEqualTo("Force garbage collection");
    assertThat(items[5]).isEqualTo(ContextMenuItem.SEPARATOR);
    assertThat(items[6].getText()).isEqualTo("Dump Java heap");
    assertThat(items[7]).isEqualTo(ContextMenuItem.SEPARATOR);
    assertThat(items[8].getText()).isEqualTo(StudioProfilersView.ATTACH_LIVE);
    assertThat(items[9].getText()).isEqualTo(StudioProfilersView.DETACH_LIVE);
    assertThat(items[10]).isEqualTo(ContextMenuItem.SEPARATOR);
    assertThat(items[11].getText()).isEqualTo(StudioProfilersView.ZOOM_IN);
    assertThat(items[12].getText()).isEqualTo(StudioProfilersView.ZOOM_OUT);

    // Adding AllocationSamplingRateEvent to make getStage().useLiveAllocationTracking() return true;
    if (myUnifiedPipeline) {
      myTransportService.addEventToStream(
        FAKE_DEVICE_ID, ProfilersTestData.generateMemoryAllocSamplingData(FAKE_PROCESS.getPid(), 0, 0).build());
    }
    else {
      myService.setMemoryData(MemoryData.newBuilder().addAllocSamplingRateEvents(AllocationSamplingRateEvent.newBuilder()).build());
    }
    ideProfilerComponents.clearContextMenuItems();
    new MemoryProfilerStageView(myProfilersView, myStage);
    items = ideProfilerComponents.getAllContextMenuItems().toArray(new ContextMenuItem[0]);
    assertThat(items.length).isEqualTo(11);
    assertThat(items[0].getText()).isEqualTo("Export...");
    assertThat(items[1]).isEqualTo(ContextMenuItem.SEPARATOR);
    assertThat(items[2].getText()).isEqualTo("Force garbage collection");
    assertThat(items[3]).isEqualTo(ContextMenuItem.SEPARATOR);
    assertThat(items[4].getText()).isEqualTo("Dump Java heap");
    assertThat(items[5]).isEqualTo(ContextMenuItem.SEPARATOR);
    assertThat(items[6].getText()).isEqualTo(StudioProfilersView.ATTACH_LIVE);
    assertThat(items[7].getText()).isEqualTo(StudioProfilersView.DETACH_LIVE);
    assertThat(items[8]).isEqualTo(ContextMenuItem.SEPARATOR);
    assertThat(items[9].getText()).isEqualTo(StudioProfilersView.ZOOM_IN);
    assertThat(items[10].getText()).isEqualTo(StudioProfilersView.ZOOM_OUT);
  }


  @Test
  public void testNativeAllocationContextMenu() {
    assumeTrue(myUnifiedPipeline);
    myIdeProfilerServices.enableNativeMemorySampling(true);
    int deviceId = "Test".hashCode();
    // Setup Q Device.
    Common.Device device = Common.Device.newBuilder()
      .setDeviceId(deviceId)
      .setFeatureLevel(AndroidVersion.VersionCodes.Q)
      .setSerial("Test")
      .setState(Common.Device.State.ONLINE)
      .build();
    myTransportService.addDevice(device);
    // Adding AllocationSamplingRateEvent to make getStage().useLiveAllocationTracking() return true;
    myTransportService.addEventToStream(
      deviceId, ProfilersTestData.generateMemoryAllocSamplingData(FAKE_PROCESS.getPid(), 0, 0).build());
    startSessionHelper(device, FAKE_PROCESS);

    // Clear the pre-setup context menu items.
    MemoryProfilerStageView stageView = (MemoryProfilerStageView)myProfilersView.getStageView();
    FakeIdeProfilerComponents ideProfilerComponents = (FakeIdeProfilerComponents)stageView.getIdeComponents();
    ideProfilerComponents.clearContextMenuItems();
    // Create a new stage to reinitialize the context menu items.
    new MemoryProfilerStageView(myProfilersView, myStage);

    // Validate items are as expected.
    ContextMenuItem[] items = ideProfilerComponents.getAllContextMenuItems().toArray(new ContextMenuItem[0]);
    assertThat(items.length).isEqualTo(13);
    assertThat(items[0].getText()).isEqualTo("Export...");
    assertThat(items[1]).isEqualTo(ContextMenuItem.SEPARATOR);
    assertThat(items[2].getText()).isEqualTo(MemoryProfilerStageView.RECORD_NATIVE_TEXT);
    assertThat(items[3].getText()).isEqualTo(MemoryProfilerStageView.STOP_NATIVE_TEXT);
    assertThat(items[4].getText()).isEqualTo("Force garbage collection");
    assertThat(items[5]).isEqualTo(ContextMenuItem.SEPARATOR);
    assertThat(items[6].getText()).isEqualTo("Dump Java heap");
    assertThat(items[7]).isEqualTo(ContextMenuItem.SEPARATOR);
    assertThat(items[8].getText()).isEqualTo(StudioProfilersView.ATTACH_LIVE);
    assertThat(items[9].getText()).isEqualTo(StudioProfilersView.DETACH_LIVE);
    assertThat(items[10]).isEqualTo(ContextMenuItem.SEPARATOR);
    assertThat(items[11].getText()).isEqualTo(StudioProfilersView.ZOOM_IN);
    assertThat(items[12].getText()).isEqualTo(StudioProfilersView.ZOOM_OUT);
  }

  @Test
  public void testNativeAllocationTooltipForX86() {
    assumeTrue(myUnifiedPipeline);
    myIdeProfilerServices.enableNativeMemorySampling(true);
    // Test toolbar configuration for O+;
    // Adding AllocationSamplingRateEvent to make getStage().useLiveAllocationTracking() return true;
    if (myUnifiedPipeline) {
      myTransportService.addEventToStream(
        FAKE_DEVICE_ID, ProfilersTestData.generateMemoryAllocSamplingData(FAKE_PROCESS.getPid(), 0, 0).build());
    }
    else {
      myService.setMemoryData(MemoryData.newBuilder().addAllocSamplingRateEvents(AllocationSamplingRateEvent.newBuilder()).build());
    }

    myProfilers.getSessionsManager().endCurrentSession();
    myTransportService.updateDevice(FAKE_DEVICE, FAKE_DEVICE.toBuilder().setCpuAbi("x86").build());
    Common.Process process = FAKE_PROCESS.toBuilder().setPid(4321).setAbiCpuArch("x86").build();
    myTransportService.addProcess(FAKE_DEVICE, process);
    myProfilers.getSessionsManager().beginSession(FAKE_DEVICE.getDeviceId(), FAKE_DEVICE, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    assertThat(myProfilers.getSessionsManager().getSelectedSessionMetaData().getProcessAbi()).isEqualTo("x86");
    MemoryProfilerStageView view = new MemoryProfilerStageView(myProfilersView, myStage);
    assertThat(view.getNativeAllocationButton().getToolTipText()).isEqualTo(view.X86_RECORD_NATIVE_TOOLTIP);
    assertThat(view.getNativeAllocationButton().isEnabled()).isFalse();
  }

  @Test
  public void testButtonMashForNativeAllocations() {
    assumeTrue(myUnifiedPipeline);
    myIdeProfilerServices.enableNativeMemorySampling(true);
    MemoryProfilerStageView view = new MemoryProfilerStageView(myProfilersView, myStage);
    view.getNativeAllocationButton().doClick();
    assertThat(view.getNativeAllocationButton().isEnabled()).isFalse();
  }
  @Test
  public void testHeapDumpButtonDisabledWhenRecording() {
    assumeTrue(myUnifiedPipeline);
    myIdeProfilerServices.enableNativeMemorySampling(true);
    MemoryProfilerStageView view = new MemoryProfilerStageView(myProfilersView, myStage);
    view.getNativeAllocationButton().doClick();
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(view.getHeapDumpButton().isEnabled()).isFalse();
    view.getNativeAllocationButton().doClick();
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(view.getHeapDumpButton().isEnabled()).isTrue();
  }

  @Test
  public void testWhenSessionDiesNativeAllocationButtonIsDisabled() {
    assumeTrue(myUnifiedPipeline);
    myIdeProfilerServices.enableNativeMemorySampling(true);
    MemoryProfilerStageView view = new MemoryProfilerStageView(myProfilersView, myStage);
    myStage.toggleNativeAllocationTracking();
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(view.getNativeAllocationButton().isEnabled()).isTrue();
    myProfilers.getSessionsManager().endCurrentSession();
    // First tick sends the END_SESSION command and triggers the stop recording
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    // Second tick sends the STOP_NATIVE_HEAP_SAMPLE command and updates state
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(view.getNativeAllocationButton().isEnabled()).isFalse();
  }

  @Test
  public void testToolbarForNativeAllocations() {
    assumeTrue(myUnifiedPipeline);
    myIdeProfilerServices.enableLiveAllocationTracking(true);
    myIdeProfilerServices.enableNativeMemorySampling(true);
    MemoryProfilerStageView view1 = new MemoryProfilerStageView(myProfilersView, myStage);
    JPanel toolbar = (JPanel)view1.getToolbar().getComponent(0);
    // Test toolbar configuration for pre-Q (FAKE_DEVICE is O by default).
    assertThat(toolbar.getComponents()).asList().containsExactly(
      view1.getGarbageCollectionButtion(),
      view1.getHeapDumpButton(),
      view1.getAllocationSamplingRateLabel(),
      view1.getAllocationSamplingRateDropDown()
    );

    Common.Device device = Common.Device.newBuilder()
      .setDeviceId("Test".hashCode())
      .setFeatureLevel(AndroidVersion.VersionCodes.Q)
      .setSerial("Test")
      .setState(Common.Device.State.ONLINE)
      .build();
    myTransportService.addDevice(device);
    startSessionHelper(device, FAKE_PROCESS);

    MemoryProfilerStageView view2 = new MemoryProfilerStageView(myProfilersView, myStage);
    toolbar = (JPanel)view2.getToolbar().getComponent(0);

    // The separator is not exposed as such we assume its position and validate its existence.
    assertThat(toolbar.getComponent(4)).isInstanceOf(FlatSeparator.class);
    assertThat(toolbar.getComponents()).asList().containsExactly(
      view2.getGarbageCollectionButtion(),
      view2.getHeapDumpButton(),
      view2.getAllocationSamplingRateLabel(),
      view2.getAllocationSamplingRateDropDown(),
      toolbar.getComponent(4), // FlatSeparator
      view2.getNativeAllocationButton(),
      view2.getAllocationCaptureElaspedTimeLabel()
    );
  }

  @Test
  public void testToolbar() {
    assumeTrue(myUnifiedPipeline);
    myIdeProfilerServices.enableLiveAllocationTracking(true);

    // Test toolbar configuration for pre-O.
    Common.Device device = Common.Device.newBuilder()
      .setDeviceId("PreO".hashCode())
      .setFeatureLevel(AndroidVersion.VersionCodes.N)
      .setSerial("Test")
      .setState(Common.Device.State.ONLINE)
      .build();
    myTransportService.addDevice(device);
    startSessionHelper(device, FAKE_PROCESS);
    MemoryProfilerStageView view1 = new MemoryProfilerStageView(myProfilersView, myStage);
    JPanel toolbar = (JPanel)view1.getToolbar().getComponent(0);
    assertThat(toolbar.getComponents()).asList().containsExactly(
      view1.getGarbageCollectionButtion(),
      view1.getHeapDumpButton(),
      view1.getAllocationButton(),
      view1.getAllocationCaptureElaspedTimeLabel()
    );

    // Test toolbar configuration for O+;
    device = Common.Device.newBuilder()
      .setDeviceId("OPlus".hashCode())
      .setFeatureLevel(AndroidVersion.VersionCodes.O)
      .setSerial("Test")
      .setState(Common.Device.State.ONLINE)
      .build();
    myTransportService.addDevice(device);
    startSessionHelper(device, FAKE_PROCESS);
    MemoryProfilerStageView view2 = new MemoryProfilerStageView(myProfilersView, myStage);
    toolbar = (JPanel)view2.getToolbar().getComponent(0);
    assertThat(toolbar.getComponents()).asList().containsExactly(
      view2.getGarbageCollectionButtion(),
      view2.getHeapDumpButton(),
      view2.getAllocationSamplingRateLabel(),
      view2.getAllocationSamplingRateDropDown()
    );

  }

  @Test
  public void testAllocationSamplingRateDropdownEnabledByData() {
    assumeTrue(myUnifiedPipeline);
    myIdeProfilerServices.enableLiveAllocationTracking(true);
    startSessionHelper(FAKE_DEVICE, FAKE_PROCESS);
    // We need to build a new stage to recognize the session is alive.
    myStage = new MemoryProfilerStage(myProfilers);
    MemoryProfilerStageView view = new MemoryProfilerStageView(myProfilersView, myStage);
    JPanel toolbar = (JPanel)view.getToolbar().getComponent(0);
    assertThat(toolbar.getComponents()).asList().containsExactly(
      view.getGarbageCollectionButtion(),
      view.getHeapDumpButton(),
      view.getAllocationSamplingRateLabel(),
      view.getAllocationSamplingRateDropDown()
    );
    assertThat(view.getAllocationSamplingRateLabel().isEnabled()).isFalse();
    assertThat(view.getAllocationSamplingRateDropDown().isEnabled()).isFalse();

    // Adding AllocationSamplingRateEvent to fire the aspect and make getStage().isLiveAllocationTrackingReady()
    // return true;
    long timestampUs = (long)myProfilers.getTimeline().getDataRange().getMax();
    myTransportService.addEventToStream(
      FAKE_DEVICE_ID, ProfilersTestData.generateMemoryAllocSamplingData(FAKE_PROCESS.getPid(), timestampUs, 0)
        .build());
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(view.getAllocationSamplingRateLabel().isEnabled()).isTrue();
    assertThat(view.getAllocationSamplingRateDropDown().isEnabled()).isTrue();
  }

  @Test
  public void testGcDurationAttachment() {
    Common.Device device =
      Common.Device.newBuilder().setDeviceId(1).setFeatureLevel(AndroidVersion.VersionCodes.O).setState(Common.Device.State.ONLINE).build();
    Common.Process process = Common.Process.newBuilder().setDeviceId(1).setPid(2).setState(Common.Process.State.ALIVE).build();

    // Set up test data from range 0us-10us. Note that the proto timestamps are in nanoseconds.
    if (myUnifiedPipeline) {
      myTransportService.addEventToStream(
        device.getDeviceId(), ProfilersTestData.generateMemoryAllocStatsData(process.getPid(), 0, 0).build());
      myTransportService.addEventToStream(
        device.getDeviceId(), ProfilersTestData.generateMemoryAllocStatsData(process.getPid(), 10, 100).build());

      myTransportService.addEventToStream(
        device.getDeviceId(), ProfilersTestData.generateMemoryGcData(process.getPid(), 1, Memory.MemoryGcData.newBuilder()
          .setDuration(TimeUnit.MICROSECONDS.toNanos(1)).build()).build());
      myTransportService.addEventToStream(
        device.getDeviceId(), ProfilersTestData.generateMemoryGcData(process.getPid(), 6, Memory.MemoryGcData.newBuilder()
          .setDuration(TimeUnit.MICROSECONDS.toNanos(1)).build()).build());
      myTransportService.addEventToStream(
        device.getDeviceId(), ProfilersTestData.generateMemoryGcData(process.getPid(), 8, Memory.MemoryGcData.newBuilder()
          .setDuration(TimeUnit.MICROSECONDS.toNanos(1)).build()).build());
      myTransportService.addEventToStream(
        device.getDeviceId(), ProfilersTestData.generateMemoryGcData(process.getPid(), 10, Memory.MemoryGcData.newBuilder()
          .setDuration(TimeUnit.MICROSECONDS.toNanos(1)).build()).build());

      myTransportService.addEventToStream(
        device.getDeviceId(), ProfilersTestData
          .generateMemoryAllocSamplingData(process.getPid(), 1, MemoryProfilerStage.LiveAllocationSamplingMode.FULL.getValue()).build());
      myTransportService.addEventToStream(
        device.getDeviceId(), ProfilersTestData
          .generateMemoryAllocSamplingData(process.getPid(), 5, MemoryProfilerStage.LiveAllocationSamplingMode.SAMPLED.getValue()).build());
      myTransportService.addEventToStream(
        device.getDeviceId(), ProfilersTestData
          .generateMemoryAllocSamplingData(process.getPid(), 8, MemoryProfilerStage.LiveAllocationSamplingMode.NONE.getValue()).build());
      myTransportService.addEventToStream(
        device.getDeviceId(), ProfilersTestData
          .generateMemoryAllocSamplingData(process.getPid(), 10, MemoryProfilerStage.LiveAllocationSamplingMode.FULL.getValue()).build());
    }
    else {
      MemoryData data = MemoryData.newBuilder()
        .addAllocStatsSamples(MemoryData.AllocStatsSample.newBuilder().setTimestamp(0)
                                .setAllocStats(Memory.MemoryAllocStatsData.newBuilder().setJavaAllocationCount(0)))
        .addAllocStatsSamples(MemoryData.AllocStatsSample.newBuilder().setTimestamp(10000)
                                .setAllocStats(Memory.MemoryAllocStatsData.newBuilder().setJavaAllocationCount(100)))
        .addGcStatsSamples(MemoryData.GcStatsSample.newBuilder().setStartTime(1000).setEndTime(2000))
        .addGcStatsSamples(MemoryData.GcStatsSample.newBuilder().setStartTime(6000).setEndTime(7000))
        .addGcStatsSamples(MemoryData.GcStatsSample.newBuilder().setStartTime(8000).setEndTime(9000))
        .addGcStatsSamples(MemoryData.GcStatsSample.newBuilder().setStartTime(10000).setEndTime(11000))
        .addAllocSamplingRateEvents(AllocationSamplingRateEvent.newBuilder()
                                      .setTimestamp(1000)
                                      .setSamplingRate(MemoryAllocSamplingData.newBuilder()
                                                         .setSamplingNumInterval(
                                                           MemoryProfilerStage.LiveAllocationSamplingMode.FULL.getValue())))
        .addAllocSamplingRateEvents(AllocationSamplingRateEvent.newBuilder()
                                      .setTimestamp(5000)
                                      .setSamplingRate(MemoryAllocSamplingData.newBuilder()
                                                         .setSamplingNumInterval(
                                                           MemoryProfilerStage.LiveAllocationSamplingMode.SAMPLED.getValue())))
        .addAllocSamplingRateEvents(AllocationSamplingRateEvent.newBuilder()
                                      .setTimestamp(8000)
                                      .setSamplingRate(MemoryAllocSamplingData.newBuilder()
                                                         .setSamplingNumInterval(
                                                           MemoryProfilerStage.LiveAllocationSamplingMode.NONE.getValue())))
        .addAllocSamplingRateEvents(AllocationSamplingRateEvent.newBuilder()
                                      .setTimestamp(10000)
                                      .setSamplingRate(MemoryAllocSamplingData.newBuilder()
                                                         .setSamplingNumInterval(
                                                           MemoryProfilerStage.LiveAllocationSamplingMode.FULL.getValue())))
        .build();
      myService.setMemoryData(data);
    }

    // Set up the correct agent and session state so that the MemoryProfilerStageView can be initialized properly.
    myTransportService.setAgentStatus(Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.ATTACHED).build());
    startSessionHelper(device, process);

    // Reset the timeline so that both data range and view range stays at (0,10) on the next tick.
    myStage.getTimeline().reset(0, TimeUnit.MICROSECONDS.toNanos(10));
    myStage.getTimeline().getViewRange().set(0, 10);
    MemoryProfilerStageView view = new MemoryProfilerStageView(myProfilersView, myStage);
    // Tick a large enough time so that the renders interpolates to the final positions
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS * 10);

    DurationDataRenderer<GcDurationData> durationDataRenderer = view.getTimelineComponent().getGcDurationDataRenderer();
    java.util.List<Rectangle2D.Float> renderedRegions = durationDataRenderer.getClickRegionCache();
    assertThat(renderedRegions.size()).isEqualTo(4);
    int iconWidth = StudioIcons.Profiler.Events.GARBAGE_EVENT.getIconWidth();
    int iconHeight = StudioIcons.Profiler.Events.GARBAGE_EVENT.getIconHeight();
    // Point should be attached due to start of FULL mode
    validateRegion(renderedRegions.get(0), 0.1f, 0.9f, iconWidth, iconHeight);
    // Point should be detached due to SAMPLED mode
    validateRegion(renderedRegions.get(1), 0.6f, 1f, iconWidth, iconHeight);
    // Point should be detached due to NONE mode
    validateRegion(renderedRegions.get(2), 0.8f, 1f, iconWidth, iconHeight);
    // Point should be attached due to start of FULL mode
    validateRegion(renderedRegions.get(3), 1f, 0f, iconWidth, iconHeight);
  }

  @Test
  public void testAllocationSamplingRateAttachment() {
    Common.Device device =
      Common.Device.newBuilder().setDeviceId(1).setFeatureLevel(AndroidVersion.VersionCodes.O).setState(Common.Device.State.ONLINE).build();
    Common.Process process = Common.Process.newBuilder().setDeviceId(1).setPid(2).setState(Common.Process.State.ALIVE).build();

    // Set up test data from range 0us-10us. Note that the proto timestamps are in nanoseconds.
    if (myUnifiedPipeline) {
      myTransportService.addEventToStream(
        device.getDeviceId(), ProfilersTestData.generateMemoryAllocStatsData(process.getPid(), 0, 0).build());
      myTransportService.addEventToStream(
        device.getDeviceId(), ProfilersTestData.generateMemoryAllocStatsData(process.getPid(), 10, 100).build());

      myTransportService.addEventToStream(
        device.getDeviceId(), ProfilersTestData
          .generateMemoryAllocSamplingData(process.getPid(), 1, MemoryProfilerStage.LiveAllocationSamplingMode.FULL.getValue()).build());
      myTransportService.addEventToStream(
        device.getDeviceId(), ProfilersTestData
          .generateMemoryAllocSamplingData(process.getPid(), 5, MemoryProfilerStage.LiveAllocationSamplingMode.SAMPLED.getValue()).build());
      myTransportService.addEventToStream(
        device.getDeviceId(), ProfilersTestData
          .generateMemoryAllocSamplingData(process.getPid(), 8, MemoryProfilerStage.LiveAllocationSamplingMode.NONE.getValue()).build());
      myTransportService.addEventToStream(
        device.getDeviceId(), ProfilersTestData
          .generateMemoryAllocSamplingData(process.getPid(), 10, MemoryProfilerStage.LiveAllocationSamplingMode.FULL.getValue()).build());
    }
    else {
      MemoryData data = MemoryData.newBuilder()
        .addAllocStatsSamples(MemoryData.AllocStatsSample.newBuilder().setTimestamp(0)
                                .setAllocStats(Memory.MemoryAllocStatsData.newBuilder().setJavaAllocationCount(0)))
        .addAllocStatsSamples(MemoryData.AllocStatsSample.newBuilder().setTimestamp(10000)
                                .setAllocStats(Memory.MemoryAllocStatsData.newBuilder().setJavaAllocationCount(100)))
        .addAllocSamplingRateEvents(AllocationSamplingRateEvent.newBuilder()
                                      .setTimestamp(1000)
                                      .setSamplingRate(MemoryAllocSamplingData.newBuilder()
                                                         .setSamplingNumInterval(
                                                           MemoryProfilerStage.LiveAllocationSamplingMode.FULL.getValue())))
        .addAllocSamplingRateEvents(AllocationSamplingRateEvent.newBuilder()
                                      .setTimestamp(5000)
                                      .setSamplingRate(MemoryAllocSamplingData.newBuilder()
                                                         .setSamplingNumInterval(
                                                           MemoryProfilerStage.LiveAllocationSamplingMode.SAMPLED.getValue())))
        .addAllocSamplingRateEvents(AllocationSamplingRateEvent.newBuilder()
                                      .setTimestamp(8000)
                                      .setSamplingRate(MemoryAllocSamplingData.newBuilder()
                                                         .setSamplingNumInterval(
                                                           MemoryProfilerStage.LiveAllocationSamplingMode.NONE.getValue())))
        .addAllocSamplingRateEvents(AllocationSamplingRateEvent.newBuilder()
                                      .setTimestamp(10000)
                                      .setSamplingRate(MemoryAllocSamplingData.newBuilder()
                                                         .setSamplingNumInterval(
                                                           MemoryProfilerStage.LiveAllocationSamplingMode.FULL.getValue())))
        .build();
      myService.setMemoryData(data);
    }

    // Set up the correct agent and session state so that the MemoryProfilerStageView can be initialized properly.
    myTransportService.setAgentStatus(Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.ATTACHED).build());
    startSessionHelper(device, process);

    // Reset the timeline so that both data range and view range stays at (0,10) on the next tick.
    myStage.getTimeline().reset(0, TimeUnit.MICROSECONDS.toNanos(10));
    myStage.getTimeline().getViewRange().set(0, 10);
    MemoryProfilerStageView view = new MemoryProfilerStageView(myProfilersView, myStage);
    // Tick a large enough time so that the renders interpolates to the final positions
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS * 10);

    DurationDataRenderer<AllocationSamplingRateDurationData> durationDataRenderer =
      view.getTimelineComponent().getAllocationSamplingRateRenderer();
    java.util.List<Rectangle2D.Float> renderedRegions = durationDataRenderer.getClickRegionCache();
    assertThat(renderedRegions.size()).isEqualTo(4);
    int iconWidth = StudioIcons.Profiler.Events.ALLOCATION_TRACKING_NONE.getIconWidth();
    int iconHeight = StudioIcons.Profiler.Events.ALLOCATION_TRACKING_NONE.getIconHeight();
    // Point should be attached due to start of FULL mode
    validateRegion(renderedRegions.get(0), 0.1f, 0.9f, iconWidth, iconHeight);
    // Point should be attached due to end of FULL mode
    validateRegion(renderedRegions.get(1), 0.5f, 0.5f, iconWidth, iconHeight);
    // Point should be detached because it's between SAMPLED and NONE modes
    validateRegion(renderedRegions.get(2), 0.8f, 1f, iconWidth, iconHeight);
    // Point should be attached due to start of FULL mode
    validateRegion(renderedRegions.get(3), 1f, 0f, iconWidth, iconHeight);
  }

  @Test
  public void testCaptureInfoMessage_showsWhenLoadingCaptureWithMessage_hiddenWhenLoadingHeapDump() {
    MemoryProfilerStageView stageView = (MemoryProfilerStageView)myProfilersView.getStageView();
    Executor joiner = MoreExecutors.directExecutor();
    myMockLoader.setReturnImmediateFuture(true);

    // Load a fake capture with a non-null info message and verify the message is displayed.
    FakeCaptureObject fakeCapture =
      new FakeCaptureObject.Builder().setCaptureName("SAMPLE_CAPTURE1").setStartTime(0).setEndTime(10).setInfoMessage("Foo").build();
    myStage.selectCaptureDuration(
      new CaptureDurationData<>(1, false, false, new CaptureEntry<>(new Object(), () -> fakeCapture)), joiner);
    // FakeCaptureObject's load() is a no-op, so force refresh here.
    myStage.getCaptureSelection().refreshSelectedHeap();
    assertThat(stageView.getCaptureInfoMessage().isVisible()).isTrue();

    // Load a heap dump capture and verify the message is hidden.
    HeapDumpInfo heapDumpInfo = HeapDumpInfo.newBuilder()
      .setStartTime(TimeUnit.MICROSECONDS.toNanos(3))
      .setEndTime(TimeUnit.MICROSECONDS.toNanos(4))
      .build();
    HeapDumpCaptureObject heapDumpCapture = new HeapDumpCaptureObject(new ProfilerClient(getGrpcChannel().getChannel()),
                                                                      ProfilersTestData.SESSION_DATA,
                                                                      heapDumpInfo,
                                                                      null,
                                                                      myIdeProfilerServices.getFeatureTracker(),
                                                                      myStage.getStudioProfilers().getIdeServices());
    myStage.selectCaptureDuration(
      new CaptureDurationData<>(1, false, false, new CaptureEntry<>(new Object(), () -> heapDumpCapture)), joiner);
    assertThat(stageView.getCaptureInfoMessage().isVisible()).isFalse();
  }

  @Test
  public void buttonTextChangesWhenAllocationTrackingChanges() {
    assumeTrue(myUnifiedPipeline);
    MemoryProfilerStageView stageView = (MemoryProfilerStageView)myProfilersView.getStageView();
    assertThat(stageView.getNativeAllocationButton().getText()).isEqualTo(MemoryProfilerStageView.RECORD_NATIVE_TEXT);
    assertThat(stageView.getNativeAllocationButton().getDisabledIcon())
      .isEqualTo(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.RECORD));
    myStage.toggleNativeAllocationTracking();
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(stageView.getNativeAllocationButton().getText()).isEqualTo(MemoryProfilerStageView.STOP_NATIVE_TEXT);
    assertThat(stageView.getNativeAllocationButton().getDisabledIcon())
      .isEqualTo(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.STOP_RECORDING));
    myStage.toggleNativeAllocationTracking();
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(stageView.getNativeAllocationButton().getText()).isEqualTo(MemoryProfilerStageView.RECORD_NATIVE_TEXT);
    assertThat(stageView.getNativeAllocationButton().getDisabledIcon())
      .isEqualTo(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.RECORD));
  }

  @Test
  public void loadingPanelAvailabilityAndVisibility() {
    MemoryProfilerStageLayout layout = ((MemoryProfilerStageView)myProfilersView.getStageView()).getLayout();
    layout.setLoadingUiVisible(true);
    assertThat(layout.getLoadingPanel()).isNotNull();
    assertThat(layout.getLoadingPanel().getComponent().isVisible()).isTrue();
    layout.setLoadingUiVisible(false);
    assertThat(layout.getLoadingPanel()).isNull();
  }

  private static void validateRegion(Rectangle2D.Float rect, float xStart, float yStart, float width, float height) {
    final float EPSILON = 1e-6f;
    assertThat(rect.x).isWithin(EPSILON).of(xStart);
    assertThat(rect.y).isWithin(EPSILON).of(yStart);
    assertThat(rect.width).isWithin(EPSILON).of(width);
    assertThat(rect.height).isWithin(EPSILON).of(height);
  }

  private void assertSelection(@Nullable CaptureObject expectedCaptureObject,
                               @Nullable HeapSet expectedHeapSet,
                               @Nullable ClassSet expectedClassSet,
                               @Nullable InstanceObject expectedInstanceObject) {
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isEqualTo(expectedCaptureObject);
    assertThat(myStage.getCaptureSelection().getSelectedHeapSet()).isEqualTo(expectedHeapSet);
    assertThat(myStage.getCaptureSelection().getSelectedClassSet()).isEqualTo(expectedClassSet);
    assertThat(myStage.getCaptureSelection().getSelectedInstanceObject()).isEqualTo(expectedInstanceObject);
  }


  private void assertView(@Nullable CaptureObject expectedCaptureObject,
                          @Nullable HeapSet expectedHeapSet,
                          @Nullable ClassSet expectedClassSet,
                          @Nullable InstanceObject expectedInstanceObject,
                          boolean isCaptureLoading) {
    MemoryProfilerStageView stageView = (MemoryProfilerStageView)myProfilersView.getStageView();

    ComboBoxModel<HeapSet> heapObjectComboBoxModel = stageView.getHeapView().getHeapComboBox().getModel();
    MemoryProfilerStageLayout layout = stageView.getLayout();

    if (expectedCaptureObject == null) {
      assertThat(layout.isLoadingUiVisible()).isFalse();
      assertThat(layout.isShowingCaptureUi()).isFalse();
      assertThat(heapObjectComboBoxModel.getSize()).isEqualTo(0);
      assertThat(stageView.getClassifierView().getTree()).isNull();
      assertThat(stageView.getClassSetView().getComponent().isVisible()).isFalse();
      assertThat(stageView.getInstanceDetailsView().getComponent().isVisible()).isFalse();
      return;
    }

    if (isCaptureLoading) {
      assertThat(heapObjectComboBoxModel.getSize()).isEqualTo(0);
    }
    else {
      assertThat(layout.isShowingCaptureUi()).isTrue();
      assertThat(IntStream.range(0, heapObjectComboBoxModel.getSize()).mapToObj(heapObjectComboBoxModel::getElementAt)
                   .collect(Collectors.toSet())).isEqualTo(new HashSet<>(expectedCaptureObject.getHeapSets()));
      assertThat(heapObjectComboBoxModel.getSelectedItem()).isEqualTo(expectedHeapSet);
    }

    if (expectedHeapSet == null) {
      assertThat(stageView.getClassifierView().getTree()).isNull();
      return;
    }

    JTree classifierTree = stageView.getClassifierView().getTree();
    assertThat(classifierTree).isNotNull();

    if (expectedClassSet == null) {
      assertThat(classifierTree.getLastSelectedPathComponent()).isNull();
      assertThat(stageView.getClassSetView().getComponent().isVisible()).isFalse();
      assertThat(stageView.getInstanceDetailsView().getComponent().isVisible()).isFalse();
      return;
    }

    Object selectedClassNode = classifierTree.getLastSelectedPathComponent();
    assertThat(selectedClassNode).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)selectedClassNode).getAdapter()).isInstanceOf(ClassSet.class);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassSet> selectedClassObject = (MemoryObjectTreeNode<ClassSet>)selectedClassNode;
    assertThat(selectedClassObject.getAdapter()).isEqualTo(expectedClassSet);

    assertThat(stageView.getClassSetView().getComponent().isVisible()).isTrue();
    JTree classSetTree = stageView.getClassSetView().getTree();
    assertThat(classSetTree).isNotNull();

    if (expectedInstanceObject == null) {
      assertThat(classSetTree.getLastSelectedPathComponent()).isNull();
      assertThat(stageView.getInstanceDetailsView().getComponent().isVisible()).isFalse();
      return;
    }

    Object selectedInstanceNode = classSetTree.getLastSelectedPathComponent();
    assertThat(selectedInstanceNode).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)selectedInstanceNode).getAdapter()).isInstanceOf(InstanceObject.class);
    //noinspection unchecked
    MemoryObjectTreeNode<InstanceObject> selectedInstanceObject = (MemoryObjectTreeNode<InstanceObject>)selectedInstanceNode;
    assertThat(selectedInstanceObject.getAdapter()).isEqualTo(expectedInstanceObject);

    boolean detailsViewVisible = expectedInstanceObject.getCallStackDepth() > 0 || !expectedInstanceObject.getReferences().isEmpty();
    assertThat(stageView.getInstanceDetailsView().getComponent().isVisible()).isEqualTo(detailsViewVisible);
  }

  private void assertViewLegacy(@Nullable CaptureObject expectedCaptureObject,
                                @Nullable HeapSet expectedHeapSet,
                                @Nullable ClassSet expectedClassSet,
                                @Nullable InstanceObject expectedInstanceObject,
                                boolean isCaptureLoading) {
    MemoryProfilerStageView stageView = (MemoryProfilerStageView)myProfilersView.getStageView();

    ComboBoxModel<HeapSet> heapObjectComboBoxModel = stageView.getHeapView().getHeapComboBox().getModel();

    if (expectedCaptureObject == null) {
      assertThat(stageView.getChartCaptureSplitter().getSecondComponent()).isNull();
      assertThat(stageView.getCaptureView().getLabel().getText()).isEmpty();
      assertThat(heapObjectComboBoxModel.getSize()).isEqualTo(0);
      assertThat(stageView.getClassifierView().getTree()).isNull();
      assertThat(stageView.getClassSetView().getComponent().isVisible()).isFalse();
      assertThat(stageView.getInstanceDetailsView().getComponent().isVisible()).isFalse();
      return;
    }

    assertThat(stageView.getChartCaptureSplitter().getSecondComponent()).isNotNull();
    if (isCaptureLoading) {
      assertThat(stageView.getCaptureView().getLabel().getText()).isEmpty();
      assertThat(heapObjectComboBoxModel.getSize()).isEqualTo(0);
    }
    else {
      assertThat(stageView.getChartCaptureSplitter().getSecondComponent()).isEqualTo(stageView.getCapturePanel());
      assertThat(stageView.getCaptureView().getLabel().getText()).isEqualTo(expectedCaptureObject.getName());
      assertThat(IntStream.range(0, heapObjectComboBoxModel.getSize()).mapToObj(heapObjectComboBoxModel::getElementAt)
                   .collect(Collectors.toSet())).isEqualTo(new HashSet<>(expectedCaptureObject.getHeapSets()));
      assertThat(heapObjectComboBoxModel.getSelectedItem()).isEqualTo(expectedHeapSet);
    }

    if (expectedHeapSet == null) {
      assertThat(stageView.getClassifierView().getTree()).isNull();
      return;
    }

    JTree classifierTree = stageView.getClassifierView().getTree();
    assertThat(classifierTree).isNotNull();

    if (expectedClassSet == null) {
      assertThat(classifierTree.getLastSelectedPathComponent()).isNull();
      assertThat(stageView.getClassSetView().getComponent().isVisible()).isFalse();
      assertThat(stageView.getInstanceDetailsView().getComponent().isVisible()).isFalse();
      return;
    }

    Object selectedClassNode = classifierTree.getLastSelectedPathComponent();
    assertThat(selectedClassNode).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)selectedClassNode).getAdapter()).isInstanceOf(ClassSet.class);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassSet> selectedClassObject = (MemoryObjectTreeNode<ClassSet>)selectedClassNode;
    assertThat(selectedClassObject.getAdapter()).isEqualTo(expectedClassSet);

    assertThat(stageView.getClassSetView().getComponent().isVisible()).isTrue();
    JTree classSetTree = stageView.getClassSetView().getTree();
    assertThat(classSetTree).isNotNull();

    if (expectedInstanceObject == null) {
      assertThat(classSetTree.getLastSelectedPathComponent()).isNull();
      assertThat(stageView.getInstanceDetailsView().getComponent().isVisible()).isFalse();
      return;
    }

    Object selectedInstanceNode = classSetTree.getLastSelectedPathComponent();
    assertThat(selectedInstanceNode).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)selectedInstanceNode).getAdapter()).isInstanceOf(InstanceObject.class);
    //noinspection unchecked
    MemoryObjectTreeNode<InstanceObject> selectedInstanceObject = (MemoryObjectTreeNode<InstanceObject>)selectedInstanceNode;
    assertThat(selectedInstanceObject.getAdapter()).isEqualTo(expectedInstanceObject);

    boolean detailsViewVisible = expectedInstanceObject.getCallStackDepth() > 0 || !expectedInstanceObject.getReferences().isEmpty();
    assertThat(stageView.getInstanceDetailsView().getComponent().isVisible()).isEqualTo(detailsViewVisible);
  }

  private void startSessionHelper(Common.Device device, Common.Process process) {
    myProfilers.getSessionsManager().endCurrentSession();
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.getSessionsManager().beginSession(device.getDeviceId(), device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setStage(myStage);
  }
}
