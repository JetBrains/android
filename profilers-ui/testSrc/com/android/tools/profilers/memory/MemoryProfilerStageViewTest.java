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

import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import com.android.tools.profilers.*;
import com.android.tools.profilers.cpu.FakeCpuService;
import com.android.tools.profilers.memory.adapters.*;
import com.android.tools.profilers.sessions.SessionsManager;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.android.tools.profilers.memory.MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_CLASS;
import static com.android.tools.profilers.memory.MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_PACKAGE;
import static com.android.tools.profilers.memory.MemoryProfilerTestUtils.*;
import static com.google.common.truth.Truth.assertThat;

public class MemoryProfilerStageViewTest extends MemoryProfilerTestBase {
  @NotNull private final FakeProfilerService myProfilerService = new FakeProfilerService();
  @NotNull private final FakeMemoryService myService = new FakeMemoryService();
  private StudioProfilersView myProfilersView;

  @Rule
  public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("MemoryProfilerStageViewTestChannel", myProfilerService, myService, new FakeCpuService());

  @Override
  protected FakeGrpcChannel getGrpcChannel() {
    return myGrpcChannel;
  }

  @Override
  protected void onProfilersCreated(StudioProfilers profilers) {
    myProfilersView = new StudioProfilersView(profilers, new FakeIdeProfilerComponents());
  }

  @Test
  public void testCaptureAndHeapView() throws Exception {
    final String dummyClassName1 = "DUMMY_CLASS1";
    final String dummyClassName2 = "DUMMY_CLASS2";

    Map<Integer, String> heapIdMap = ImmutableMap.of(0, "heap1", 1, "heap2");

    MemoryProfilerStageView stageView = (MemoryProfilerStageView)myProfilersView.getStageView();

    FakeCaptureObject fakeCapture1 =
      new FakeCaptureObject.Builder().setCaptureName("DUMMY_CAPTURE1").setHeapIdToNameMap(heapIdMap).setStartTime(5).setEndTime(10).build();
    InstanceObject fakeInstance1 =
      new FakeInstanceObject.Builder(fakeCapture1, dummyClassName1).setName("DUMMY_INSTANCE1").setHeapId(0).setDepth(4).setShallowSize(5)
        .setRetainedSize(6).build();
    InstanceObject fakeInstance2 =
      new FakeInstanceObject.Builder(fakeCapture1, dummyClassName2).setName("DUMMY_INSTANCE2").setDepth(1).setShallowSize(2)
        .setRetainedSize(3).build();
    fakeCapture1.addInstanceObjects(ImmutableSet.of(fakeInstance1, fakeInstance2));

    FakeCaptureObject fakeCapture2 =
      new FakeCaptureObject.Builder().setCaptureName("DUMMY_CAPTURE2").setHeapIdToNameMap(heapIdMap).setStartTime(5).setEndTime(10).build();
    InstanceObject fakeInstance3 =
      new FakeInstanceObject.Builder(fakeCapture2, dummyClassName1).setName("DUMMY_INSTANCE1").setHeapId(0).setDepth(4).setShallowSize(5)
        .setRetainedSize(6).build();
    InstanceObject fakeInstance4 =
      new FakeInstanceObject.Builder(fakeCapture2, dummyClassName2).setName("DUMMY_INSTANCE2").setDepth(1).setShallowSize(2)
        .setRetainedSize(3).build();
    fakeCapture2.addInstanceObjects(ImmutableSet.of(fakeInstance3, fakeInstance4));

    MemoryClassifierView classifierView = stageView.getClassifierView();

    JComponent captureComponent = stageView.getChartCaptureSplitter().getSecondComponent();
    assertThat(captureComponent).isNull();
    JComponent instanceComponent = stageView.getMainSplitter().getSecondComponent();
    assertThat(instanceComponent.isVisible()).isFalse();

    assertView(null, null, null, null, false);

    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> fakeCapture1)),
                             null);
    assertView(fakeCapture1, null, null, null, true);
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0, 0);
    myMockLoader.runTask();

    JTree classifierTree = classifierView.getTree();
    assertThat(classifierTree).isNotNull();
    HeapSet selectedHeap = myStage.getSelectedHeapSet();
    assertThat(selectedHeap).isNotNull();
    assertView(fakeCapture1, selectedHeap, null, null, false);
    myAspectObserver.assertAndResetCounts(0, 0, 1, 0, 1, 0, 0, 0);

    // Tests selecting a capture which loads immediately.
    myMockLoader.setReturnImmediateFuture(true);
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> fakeCapture2)),
                             null);
    classifierTree = classifierView.getTree();
    assertThat(classifierTree).isNotNull();
    selectedHeap = myStage.getSelectedHeapSet();
    // 2 heap changes: 1 from changing the capture, the other from the auto-selection after the capture is loaded.
    assertView(fakeCapture2, selectedHeap, null, null, false);
    myAspectObserver.assertAndResetCounts(0, 1, 1, 0, 2, 0, 0, 0);

    stageView.getHeapView().getComponent().setSelectedItem(fakeCapture2.getHeapSet(0));
    assertSelection(fakeCapture2, fakeCapture2.getHeapSet(0), null, null);
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0, 0);

    ClassSet selectedClassSet = findDescendantClassSetNodeWithInstance(getRootClassifierSet(classifierTree).getAdapter(), fakeInstance3);
    assertThat(selectedClassSet).isNotNull();
    myStage.selectClassSet(selectedClassSet);
    assertView(fakeCapture2, fakeCapture2.getHeapSet(0), selectedClassSet, null, false);
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 1, 0, 0);

    assertThat(myStage.getConfiguration().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_PACKAGE);
    assertThat(stageView.getClassGrouping().getComponent().getSelectedItem()).isEqualTo(ARRANGE_BY_PACKAGE);
    myAspectObserver.assertAndResetCounts(0, 0, 0, 1, 0, 1, 0, 0);

    //noinspection unchecked
    MemoryObjectTreeNode<ClassifierSet> memoryClassRoot = getRootClassifierSet(classifierTree);
    MemoryObjectTreeNode<ClassSet> targetSet = findChildClassSetNodeWithClassName(memoryClassRoot, dummyClassName1);
    classifierTree.setSelectionPath(new TreePath(new Object[]{memoryClassRoot, targetSet}));
    assertSelection(fakeCapture2, fakeCapture2.getHeapSet(0), targetSet.getAdapter(), null);

    myStage.selectInstanceObject(fakeInstance3);
    assertSelection(fakeCapture2, fakeCapture2.getHeapSet(0), targetSet.getAdapter(), fakeInstance3);
    assertView(fakeCapture2, fakeCapture2.getHeapSet(0), targetSet.getAdapter(), fakeInstance3, false);

    myStage.selectCaptureDuration(null, null);
    assertView(null, null, null, null, false);
  }

  @Test
  public void testCaptureElapsedTime() throws Exception {
    final int invalidTime = -1;
    final int startTime = 1;
    final int endTime = 5;
    long deltaUs = TimeUnit.SECONDS.toMicros(endTime - startTime);

    assertThat(myStage.isTrackingAllocations()).isFalse();

    MemoryProfilerStageView stageView = (MemoryProfilerStageView)myProfilersView.getStageView();
    myProfilerService.setTimestampNs(TimeUnit.SECONDS.toNanos(startTime));
    assertThat(stageView.getCaptureElapsedTimeLabel().getText()).isEmpty();

    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.SUCCESS);
    myService.setExplicitAllocationsInfo(AllocationsInfo.Status.IN_PROGRESS, TimeUnit.SECONDS.toNanos(startTime),
                                         TimeUnit.SECONDS.toNanos(Long.MAX_VALUE), true);

    myStage.trackAllocations(true);
    assertThat(stageView.getCaptureElapsedTimeLabel().getText())
      .isEqualTo("Recording - " + TimeAxisFormatter.DEFAULT.getFormattedString(0, 0, true));

    myProfilerService.setTimestampNs(TimeUnit.SECONDS.toNanos(endTime));
    myStage.getAspect().changed(MemoryProfilerAspect.CURRENT_CAPTURE_ELAPSED_TIME);
    assertThat(stageView.getCaptureElapsedTimeLabel().getText())
      .isEqualTo("Recording - " + TimeAxisFormatter.DEFAULT.getFormattedString(deltaUs, deltaUs, true));

    // Triggering a heap dump should not affect the allocation recording duration
    myService.setExplicitHeapDumpStatus(TriggerHeapDumpResponse.Status.SUCCESS);
    myService.setExplicitHeapDumpInfo(TimeUnit.SECONDS.toNanos(invalidTime), TimeUnit.SECONDS.toNanos(Long.MAX_VALUE));
    myStage.requestHeapDump();
    myStage.getAspect().changed(MemoryProfilerAspect.CURRENT_CAPTURE_ELAPSED_TIME);
    assertThat(stageView.getCaptureElapsedTimeLabel().getText())
      .isEqualTo("Recording - " + TimeAxisFormatter.DEFAULT.getFormattedString(deltaUs, deltaUs, true));

    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.SUCCESS);
    myService.setExplicitAllocationsInfo(AllocationsInfo.Status.IN_PROGRESS, TimeUnit.SECONDS.toNanos(startTime),
                                         TimeUnit.SECONDS.toNanos(endTime), true);
    myStage.trackAllocations(false);
    assertThat(stageView.getCaptureElapsedTimeLabel().getText()).isEmpty();
  }

  @Test
  public void testLoadingTooltipViewWithStrongReference() throws Exception {
    MemoryProfilerStageView stageView = (MemoryProfilerStageView)myProfilersView.getStageView();
    myStage.setTooltip(new MemoryUsageTooltip(myStage));
    ReferenceWalker referenceWalker = new ReferenceWalker(stageView);
    referenceWalker.assertReachable(MemoryUsageTooltipView.class);
  }

  @Test
  public void testLoadingNewCaptureWithExistingLoad() throws Exception {
    Map<Integer, String> heapIdMap = ImmutableMap.of(0, "heap1", 1, "heap2");

    FakeCaptureObject fakeCapture1 =
      new FakeCaptureObject.Builder().setCaptureName("DUMMY_CAPTURE1").setHeapIdToNameMap(heapIdMap).setStartTime(5).setEndTime(10).build();
    FakeCaptureObject fakeCapture2 =
      new FakeCaptureObject.Builder().setCaptureName("DUMMY_CAPTURE2").setHeapIdToNameMap(heapIdMap).setStartTime(10).setEndTime(15)
        .build();
    InstanceObject fakeInstance1 =
      new FakeInstanceObject.Builder(fakeCapture2, "DUMMY_CLASS").setName("DUMMY_INSTANCE1").setDepth(4).setShallowSize(5)
        .setRetainedSize(6).build();
    fakeCapture2.addInstanceObjects(ImmutableSet.of(fakeInstance1));

    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> fakeCapture1)),
                             null);
    assertView(fakeCapture1, null, null, null, true);
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0, 0);

    // Select a new capture before the first is loaded.
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> fakeCapture2)),
                             null);
    assertView(fakeCapture2, null, null, null, true);
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0, 0);
    myMockLoader.runTask();
    assertView(fakeCapture2, fakeCapture2.getHeapSet(0), null, null, false);
    myAspectObserver.assertAndResetCounts(0, 0, 1, 0, 1, 0, 0, 0);
  }

  @Test
  public void testLoadHeapDumpFromFile() throws Exception {
    SessionsManager sessionsManager = myProfilers.getSessionsManager();

    // Create a temp file
    String data = "random_string_~!@#$%^&*()_+";
    File file = FileUtil.createTempFile("fake_heap_dump", ".hprof", false);
    PrintWriter printWriter = new PrintWriter(file);
    printWriter.write(data);
    printWriter.close();

    // Import heap dump from file
    assertThat(sessionsManager.importSessionFromFile(file)).isTrue();
    Common.Session session = sessionsManager.getSelectedSession();
    long dumpTime = session.getStartTimestamp();
    DumpDataRequest request = DumpDataRequest.newBuilder()
      .setDumpTime(dumpTime)
      .setSession(session)
      .build();
    assertThat(myProfilers.getStage()).isInstanceOf(MemoryProfilerStage.class);
    DumpDataResponse response = myProfilers.getClient().getMemoryClient().getHeapDump(request);

    assertThat(response.getData()).isEqualTo(ByteString.copyFrom(data, Charset.defaultCharset()));
  }

  private void assertSelection(@Nullable CaptureObject expectedCaptureObject,
                               @Nullable HeapSet expectedHeapSet,
                               @Nullable ClassSet expectedClassSet,
                               @Nullable InstanceObject expectedInstanceObject) {
    assertThat(myStage.getSelectedCapture()).isEqualTo(expectedCaptureObject);
    assertThat(myStage.getSelectedHeapSet()).isEqualTo(expectedHeapSet);
    assertThat(myStage.getSelectedClassSet()).isEqualTo(expectedClassSet);
    assertThat(myStage.getSelectedInstanceObject()).isEqualTo(expectedInstanceObject);
  }

  private void assertView(@Nullable CaptureObject expectedCaptureObject,
                          @Nullable HeapSet expectedHeapSet,
                          @Nullable ClassSet expectedClassSet,
                          @Nullable InstanceObject expectedInstanceObject,
                          boolean isCaptureLoading) {
    MemoryProfilerStageView stageView = (MemoryProfilerStageView)myProfilersView.getStageView();

    ComboBoxModel<HeapSet> heapObjectComboBoxModel = stageView.getHeapView().getComponent().getModel();

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
}
