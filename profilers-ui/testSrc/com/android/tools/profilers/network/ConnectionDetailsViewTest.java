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
package com.android.tools.profilers.network;

import com.android.testutils.TestUtils;
import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.TreeWalker;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.legend.Legend;
import com.android.tools.profilers.*;
import com.android.tools.profilers.stacktrace.StackTraceModel;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

public class ConnectionDetailsViewTest {
  private static final String dumbTrace = "com.google.downloadUrlToStream(ImageFetcher.java:274)";
  private static final HttpData DEFAULT_DATA = new HttpData.Builder(1, 10000, 25000, 50000, 100000)
    .setUrl("dumbUrl").setTrace(dumbTrace).setMethod("GET")
    .addJavaThread(new HttpData.JavaThread(0, "thread1"))
    .build();

  private static final String RESPONSE_HEADERS = "null =  HTTP/1.1 302 Found \n Content-Type = 111 \n Content-Length = 222 \n";
  private static final String TEST_HEADERS = "car = value \n border = value \n apple = value \n 123 = value \n";
  private static final String TEST_RESOURCE_DIR = "tools/adt/idea/profilers-ui/testData/visualtests/";

  private ConnectionDetailsView myView;

  private NetworkProfilerStage myStage;
  private NetworkProfilerStageView myStageView;
  private FakeIdeProfilerServices myIdeProfilerServices;

  @Rule public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("StudioProfilerTestChannel", new FakeProfilerService(false),
                        FakeNetworkService.newBuilder().setHttpDataList(Collections.singletonList(DEFAULT_DATA)).build());

  private static boolean hasDescendantWithName(Component root, String name) {
    return new TreeWalker(root).descendantStream().anyMatch(c -> name.equals(c.getName()));
  }

  /**
   * Will throw an exception if no match is found.
   */
  @NotNull
  private static Component firstDescendantWithName(Component root, String name) {
    return new TreeWalker(root).descendantStream().filter(c -> name.equals(c.getName())).findFirst().get();
  }

  /**
   * Will throw an exception if no match is found.
   */
  @SuppressWarnings("unchecked") // Cast is safe as filter + findFirst guarantees a match
  @NotNull
  private static <C extends Component> C firstDescendantWithType(Component root, Class<C> type) {
    return (C)new TreeWalker(root).descendantStream().filter(type::isInstance).findFirst().get();
  }

  @Before
  public void before() {
    FakeTimer timer = new FakeTimer();
    myIdeProfilerServices = new FakeIdeProfilerServices();
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), myIdeProfilerServices, timer);
    NetworkProfilerStage stage = new NetworkProfilerStage(profilers);
    StudioProfilersView view = new StudioProfilersView(profilers, new FakeIdeProfilerComponents());
    profilers.setStage(stage);
    myStageView = (NetworkProfilerStageView)view.getStageView();
    myStage = myStageView.getStage();
    myView = new ConnectionDetailsView(myStageView);
  }

  @Test
  public void requestTabIsOnlyPresentWhenEnabled() {
    assertThat(hasDescendantWithName(myView, "Headers")).isTrue();
    assertThat(hasDescendantWithName(myView, "Request")).isFalse();

    myIdeProfilerServices.enableRequestPayload(true);
    myView = new ConnectionDetailsView(myStageView);
    assertThat(hasDescendantWithName(myView, "Headers")).isFalse();
    assertThat(hasDescendantWithName(myView, "Request")).isTrue();
  }

  @Test
  public void fileViewerForRequestPayloadIsPresentWhenRequestPayloadIsNotNull() {
    myIdeProfilerServices.enableRequestPayload(true);
    myView = new ConnectionDetailsView(myStageView);
    File file = new File("temp");
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();
    data.setRequestPayloadFile(file);
    assertThat(hasDescendantWithName(myView, "FileViewer")).isFalse();

    myView.setHttpData(data);
    Component requestBody = firstDescendantWithName(myView, "REQUEST_BODY");
    assertThat(hasDescendantWithName(requestBody, "FileViewer")).isTrue();
  }

  @Test
  public void fileViewerForRequestPayloadIsAbsentWhenRequestPayloadIsNull() {
    myIdeProfilerServices.enableRequestPayload(true);
    myView = new ConnectionDetailsView(myStageView);
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();

    myView.setHttpData(data);
    Component requestBody = firstDescendantWithName(myView, "REQUEST_BODY");
    assertThat(hasDescendantWithName(requestBody, "FileViewer")).isFalse();
  }

  @Test
  public void viewIsVisibleWhenDataIsNotNull() {
    myView.setVisible(false);
    myView.setHttpData(DEFAULT_DATA);
    assertThat(myView.isVisible()).isTrue();
  }

  @Test
  public void viewIsNotVisibleWhenDataIsNull() {
    myView.setVisible(true);
    myView.setHttpData(null);
    assertThat(myView.isVisible()).isFalse();
  }

  @NotNull
  private static HttpData.Builder getBuilderFromHttpData(@NotNull HttpData data) {
    HttpData.Builder builder =
      new HttpData.Builder(data.getId(), data.getStartTimeUs(), data.getUploadedTimeUs(), data.getDownloadingTimeUs(), data.getEndTimeUs())
        .setUrl(data.getUrl())
        .setMethod(data.getMethod())
        .setTrace(data.getStackTrace().getTrace());
    data.getJavaThreads().forEach(builder::addJavaThread);
    return builder;
  }

  @Test
  public void contentsAreEmptyWhenDataIsNull() {
    AspectObserver observer = new AspectObserver();
    final int[] stackFramesChangedCount = {0};
    myView.getStackTraceView().getModel().addDependency(observer)
      .onChange(StackTraceModel.Aspect.STACK_FRAMES, () -> stackFramesChangedCount[0]++);

    File file = TestUtils.getWorkspaceFile(TEST_RESOURCE_DIR + "cpu_trace.trace");
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).build();
    data.setResponsePayloadFile(file);

    assertThat(stackFramesChangedCount[0]).isEqualTo(0);
    myView.setHttpData(data);
    assertThat(stackFramesChangedCount[0]).isEqualTo(1);

    JComponent response = (JComponent)firstDescendantWithName(myView, "Response");
    assertThat(response.getComponentCount()).isNotEqualTo(0);
    assertThat(myView.getStackTraceView().getModel().getCodeLocations()).isNotEmpty();

    myView.setHttpData(null);
    assertThat(stackFramesChangedCount[0]).isEqualTo(2);

    response = (JComponent)firstDescendantWithName(myView, "Response");
    assertThat(response.getComponentCount()).isEqualTo(0);
    assertThat(myView.getStackTraceView().getModel().getCodeLocations()).isEmpty();
  }

  @Test
  public void fileViewerExistWhenPayloadFileIsNotNull() {
    File file = new File("temp");
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();
    data.setResponsePayloadFile(file);
    assertThat(hasDescendantWithName(myView, "FileViewer")).isFalse();

    myView.setHttpData(data);
    assertThat(hasDescendantWithName(myView, "FileViewer")).isTrue();
  }

  @Test
  public void contentTypeHasProperValueFromData() {
    String valueName = "Content type";
    assertThat(hasDescendantWithName(myView, valueName)).isFalse();
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();

    myView.setHttpData(data);
    JLabel value = (JLabel)firstDescendantWithName(myView, valueName);
    assertThat(value.getText()).isEqualTo("111");
  }

  @Test
  public void contentTypeIsAbsentWhenDataHasNoContentTypeValue() {
    myView.setHttpData(DEFAULT_DATA);
    assertThat(hasDescendantWithName(myView, "Content type")).isFalse();
  }

  @Test
  public void initiatingThreadFieldIsPresent() {
    myView.setHttpData(DEFAULT_DATA);
    assertThat(hasDescendantWithName(myView, "Initiating thread")).isTrue();
  }

  @Test
  public void otherThreadsFieldIsPresent() {
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).addJavaThread(new HttpData.JavaThread(1, "thread2")).build();
    myView.setHttpData(data);
    assertThat(hasDescendantWithName(myView, "Other threads")).isTrue();
  }

  @Test
  public void otherThreadsFieldIsAbsentWhenOnlyOneThread() {
    myView.setHttpData(DEFAULT_DATA);
    assertThat(hasDescendantWithName(myView, "Other threads")).isFalse();
  }

  @Test
  public void urlHasProperValueFromData() {
    assertThat(hasDescendantWithName(myView, "URL")).isFalse();

    myView.setHttpData(DEFAULT_DATA);
    JTextArea value = (JTextArea)firstDescendantWithName(myView, "URL");
    assertThat(value.getText()).isEqualTo("dumbUrl");
  }

  @Test
  public void sizeHasProperValueFromData() {
    String valueName = "Size";
    assertThat(hasDescendantWithName(myView, valueName)).isFalse();

    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();
    myView.setHttpData(data);
    JLabel value = (JLabel)firstDescendantWithName(myView, valueName);
    assertThat(value.getText()).isEqualTo("222B");
  }

  @Test
  public void contentLengthIsAbsentWhenDataHasNoContentLengthValue() {
    myView.setHttpData(DEFAULT_DATA);
    assertThat(hasDescendantWithName(myView, "Content Length")).isFalse();
  }

  @Test
  public void timingFieldIsPresent() {
    myView.setHttpData(DEFAULT_DATA);
    assertThat(hasDescendantWithName(myView, "Timing")).isTrue();
  }

  @Test
  public void headersIsUpdated() {
    JPanel headers = (JPanel)firstDescendantWithName(myView, "Headers");
    assertThat(headers.getComponentCount()).isEqualTo(0);
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();
    myView.setHttpData(data);
    assertThat(headers.getComponentCount()).isNotEqualTo(0);
  }

  @Test
  public void headerSectionIsSortedAndFormatted() {
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).setRequestFields(TEST_HEADERS).build();
    myView.setHttpData(data);
    JPanel headers = (JPanel)firstDescendantWithName(myView, "Headers");
    JPanel responseHeaders = (JPanel)firstDescendantWithName(headers, "Request Headers");

    String text = ((JTextPane)responseHeaders.getComponent(1)).getText();
    String idealBody = "<body>" +
                       "  <p>" +
                       "    <nobr><b>123:&#160;&#160;</b></nobr><span>value</span>" +
                       "  </p>" +
                       "  <p>" +
                       "    <nobr><b>apple:&#160;&#160;</b></nobr><span>value</span>" +
                       "  </p>" +
                       "  <p>" +
                       "    <nobr><b>border:&#160;&#160;</b></nobr><span>value</span>" +
                       "  </p>" +
                       "  <p>" +
                       "    <nobr><b>car:&#160;&#160;</b></nobr><span>value</span>" +
                       "  </p>" +
                       "</body>";
    text = text.replaceAll("\\s", "");
    idealBody = idealBody.replaceAll("\\s", "");
    assertThat(text).contains(idealBody);
  }

  @Test
  public void callStackViewHasProperValueFromData() {
    AspectObserver observer = new AspectObserver();
    final int[] stackFramesChangedCount = {0};
    myView.getStackTraceView().getModel().addDependency(observer)
      .onChange(StackTraceModel.Aspect.STACK_FRAMES, () -> stackFramesChangedCount[0]++);

    assertThat(stackFramesChangedCount[0]).isEqualTo(0);
    assertThat(myView.getStackTraceView().getModel().getCodeLocations().size()).isEqualTo(0);

    myView.setHttpData(DEFAULT_DATA);
    assertThat(stackFramesChangedCount[0]).isEqualTo(1);
    assertThat(myView.getStackTraceView().getModel().getCodeLocations()).isEqualTo(DEFAULT_DATA.getStackTrace().getCodeLocations());
  }

  @Test
  public void callStackNavigationChangesProfilerMode() {
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).setTrace(FakeNetworkService.FAKE_STACK_TRACE).build();
    myView.setHttpData(data);
    assertThat(data.getStackTrace().getCodeLocations().size()).isEqualTo(2);

    // Expands Profiler Mode
    myStage.getStudioProfilers().getTimeline().getSelectionRange().set(0, 10);

    boolean[] modeChanged = {false};
    AspectObserver observer = new AspectObserver();
    myStage.getStudioProfilers().addDependency(observer).onChange(ProfilerAspect.MODE, () -> modeChanged[0] = true);

    assertThat(modeChanged[0]).isFalse();
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);
    myView.getStackTraceView().getModel().setSelectedIndex(0);
    assertThat(modeChanged[0]).isTrue();
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
  }

  @Test
  public void sentReceivedLegendRendersCorrectly() {
    assertExpectedTimingLegends(TimeUnit.MILLISECONDS.toMicros(1000), 0, 0, "*", "*");

    assertExpectedTimingLegends(TimeUnit.MILLISECONDS.toMicros(1000), TimeUnit.MILLISECONDS.toMicros(1000), 0, "0ms", "*");
    assertExpectedTimingLegends(TimeUnit.MILLISECONDS.toMicros(1000), TimeUnit.MILLISECONDS.toMicros(2500), 0, "1s 500ms", "*");

    assertExpectedTimingLegends(TimeUnit.MILLISECONDS.toMicros(1000), TimeUnit.MILLISECONDS.toMicros(3000),
                                TimeUnit.MILLISECONDS.toMicros(3000), "2s", "0ms");
    assertExpectedTimingLegends(TimeUnit.MILLISECONDS.toMicros(1000), TimeUnit.MILLISECONDS.toMicros(3000),
                                TimeUnit.MILLISECONDS.toMicros(4234), "2s", "1s 234ms");

    assertExpectedTimingLegends(TimeUnit.MILLISECONDS.toMicros(1000), 0, TimeUnit.MILLISECONDS.toMicros(1000), "0ms", "0ms");
    assertExpectedTimingLegends(TimeUnit.MILLISECONDS.toMicros(1000), 0, TimeUnit.MILLISECONDS.toMicros(2000), "1s", "0ms");
  }

  private void assertExpectedTimingLegends(long startTimeUs,
                                           long downloadingTimeUs,
                                           long endTimeUs,
                                           String sentLegend,
                                           String receivedLegend) {
    // uploadedTime isn't used in legends (at the moment anyway) so just stub it for now
    long uploadedTimeUs = startTimeUs;
    HttpData data = new HttpData.Builder(0, startTimeUs, uploadedTimeUs, downloadingTimeUs, endTimeUs).setUrl("unusedUrl").setMethod("GET")
      .addJavaThread(new HttpData.JavaThread(0, "thread1")).build();
    myView.setHttpData(data);

    LegendComponent legendComponent = firstDescendantWithType(myView, LegendComponent.class);
    List<Legend> legends = legendComponent.getModel().getLegends();

    assertThat(legends.get(0).getValue()).isEqualTo(sentLegend);
    assertThat(legends.get(1).getValue()).isEqualTo(receivedLegend);
  }
}
