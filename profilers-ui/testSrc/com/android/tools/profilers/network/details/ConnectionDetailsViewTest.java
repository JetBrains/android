/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.network.details;

import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.TreeWalker;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.legend.Legend;
import com.android.tools.profilers.*;
import com.android.tools.profilers.network.FakeNetworkService;
import com.android.tools.profilers.network.NetworkProfilerStage;
import com.android.tools.profilers.network.NetworkProfilerStageView;
import com.android.tools.profilers.network.TestHttpData;
import com.android.tools.profilers.network.httpdata.HttpData;
import com.android.tools.profilers.network.httpdata.StackTrace;
import com.android.tools.profilers.stacktrace.StackTraceModel;
import com.android.tools.profilers.stacktrace.StackTraceView;
import com.google.protobuf3jarjar.ByteString;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

public class ConnectionDetailsViewTest {
  private static final String fakeTraceId = "fakeTraceId";
  private static final String fakeTrace = "com.google.downloadUrlToStream(ImageFetcher.java:274)";
  private static final HttpData DEFAULT_DATA =
    new HttpData.Builder(1, 10000, 25000, 50000, 100000, TestHttpData.FAKE_THREAD_LIST)
      .setUrl("dumbUrl").setTraceId(fakeTraceId).setMethod("GET")
    .build();

  private static final String RESPONSE_HEADERS = "null =  HTTP/1.1 302 Found \n Content-Type = 111 \n Content-Length = 222 \n";
  private static final String TEST_HEADERS = "car = value \n border = value \n apple = value \n 123 = value \n";
  private static final String TEST_REQUEST_PAYLOAD_ID = "Request Payload";
  private static final String TEST_RESPONSE_PAYLOAD_ID = "Response Payload";

  private ConnectionDetailsView myView;

  private NetworkProfilerStage myStage;
  private NetworkProfilerStageView myStageView;
  private FakeIdeProfilerServices myIdeProfilerServices;

  private final FakeProfilerService myProfilerService = new FakeProfilerService(false);
  @Rule public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("StudioProfilerTestChannel", myProfilerService,
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

    myProfilerService.addFile(fakeTraceId, ByteString.copyFromUtf8(fakeTrace));
  }

  @Test
  public void requestTabIsOnlyPresentWhenEnabled() {
    assertThat(hasDescendantWithName(myView, "TAB_HEADERS")).isTrue();
    assertThat(hasDescendantWithName(myView, "TAB_REQUEST")).isFalse();

    myIdeProfilerServices.enableRequestPayload(true);
    myView = new ConnectionDetailsView(myStageView);
    assertThat(hasDescendantWithName(myView, "TAB_HEADERS")).isFalse();
    assertThat(hasDescendantWithName(myView, "TAB_REQUEST")).isTrue();
  }

  @Test
  public void fileViewerForRequestPayloadIsPresentWhenRequestPayloadIsNotNull() {
    myProfilerService.addFile(TEST_REQUEST_PAYLOAD_ID, ByteString.copyFromUtf8("Dummy Content"));

    myIdeProfilerServices.enableRequestPayload(true);
    myView = new ConnectionDetailsView(myStageView);
    HttpData data =
      new HttpData.Builder(DEFAULT_DATA).setRequestPayloadId(TEST_REQUEST_PAYLOAD_ID).setResponseFields(RESPONSE_HEADERS).build();
    assertThat(hasDescendantWithName(myView, "FILE_VIEWER")).isFalse();

    myView.setHttpData(data);
    Component requestBody = firstDescendantWithName(myView, "REQUEST_BODY");
    assertThat(hasDescendantWithName(requestBody, "FILE_VIEWER")).isTrue();
  }

  @Test
  public void fileViewerForRequestPayloadIsAbsentWhenRequestPayloadIsNull() {
    myIdeProfilerServices.enableRequestPayload(true);
    myView = new ConnectionDetailsView(myStageView);
    HttpData data = new HttpData.Builder(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();

    myView.setHttpData(data);
    Component requestBody = firstDescendantWithName(myView, "REQUEST_BODY");
    assertThat(hasDescendantWithName(requestBody, "FILE_VIEWER")).isFalse();
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

  @Test
  public void fileViewerExistsWhenPayloadIsPresent() {
    myProfilerService.addFile(TEST_RESPONSE_PAYLOAD_ID, ByteString.copyFromUtf8("Dummy Content"));

    HttpData data = new HttpData.Builder(DEFAULT_DATA)
      .setResponseFields(RESPONSE_HEADERS)
      .setResponsePayloadId(TEST_RESPONSE_PAYLOAD_ID)
      .build();
    assertThat(hasDescendantWithName(myView, "FILE_VIEWER")).isFalse();

    myView.setHttpData(data);
    assertThat(hasDescendantWithName(myView, "FILE_VIEWER")).isTrue();
  }

  @Test
  public void contentTypeHasProperValueFromData() {
    assertThat(hasDescendantWithName(myView, "CONTENT_TYPE")).isFalse();
    HttpData data = new HttpData.Builder(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();

    myView.setHttpData(data);
    JLabel value = (JLabel)firstDescendantWithName(myView, "CONTENT_TYPE");
    assertThat(value.getText()).isEqualTo("111");
  }

  @Test
  public void contentTypeIsAbsentWhenDataHasNoContentTypeValue() {
    myView.setHttpData(DEFAULT_DATA);
    assertThat(hasDescendantWithName(myView, "CONTENT_TYPE")).isFalse();
  }

  @Test
  public void initiatingThreadFieldIsPresent() {
    myView.setHttpData(DEFAULT_DATA);
    assertThat(hasDescendantWithName(myView, "INITIATING_THREAD")).isTrue();
  }

  @Test
  public void otherThreadsFieldIsPresent() {
    HttpData data = new HttpData.Builder(DEFAULT_DATA).addJavaThread(new HttpData.JavaThread(1, "thread2")).build();
    myView.setHttpData(data);
    assertThat(hasDescendantWithName(myView, "OTHER_THREADS")).isTrue();
  }

  @Test
  public void otherThreadsFieldIsAbsentWhenOnlyOneThread() {
    myView.setHttpData(DEFAULT_DATA);
    assertThat(hasDescendantWithName(myView, "OTHER_THREADS")).isFalse();
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
    assertThat(hasDescendantWithName(myView, "SIZE")).isFalse();

    HttpData data = new HttpData.Builder(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();
    myView.setHttpData(data);
    JLabel value = (JLabel)firstDescendantWithName(myView, "SIZE");
    assertThat(value.getText()).isEqualTo("222B");
  }

  @Test
  public void contentLengthIsAbsentWhenDataHasNoContentLengthValue() {
    myView.setHttpData(DEFAULT_DATA);
    assertThat(hasDescendantWithName(myView, "CONTENT_LENGTH")).isFalse();
  }

  @Test
  public void timingFieldIsPresent() {
    myView.setHttpData(DEFAULT_DATA);
    assertThat(hasDescendantWithName(myView, "TIMING")).isTrue();
  }

  @Test
  public void headersIsUpdated() {
    JBScrollPane headersTab = (JBScrollPane)firstDescendantWithName(myView, "TAB_HEADERS");
    assertThat(hasDescendantWithName(headersTab, "REQUEST_HEADERS")).isFalse();

    HttpData data = new HttpData.Builder(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();
    myView.setHttpData(data);
    assertThat(hasDescendantWithName(headersTab, "REQUEST_HEADERS")).isTrue();
  }

  @Test
  public void headerSectionIsSortedAndFormatted() {
    HttpData data = new HttpData.Builder(DEFAULT_DATA).setRequestFields(TEST_HEADERS).build();
    myView.setHttpData(data);
    JBScrollPane headers = (JBScrollPane)firstDescendantWithName(myView, "TAB_HEADERS");
    JPanel responseHeaders = (JPanel)firstDescendantWithName(headers, "REQUEST_HEADERS");

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
    getStackTraceView(myView).getModel().addDependency(observer)
      .onChange(StackTraceModel.Aspect.STACK_FRAMES, () -> stackFramesChangedCount[0]++);

    assertThat(stackFramesChangedCount[0]).isEqualTo(0);
    assertThat(getStackTraceView(myView).getModel().getCodeLocations()).hasSize(0);

    myView.setHttpData(DEFAULT_DATA);
    assertThat(stackFramesChangedCount[0]).isEqualTo(1);

    StackTrace stackTrace = new StackTrace(myStageView.getStage().getConnectionsModel(), DEFAULT_DATA);
    assertThat(getStackTraceView(myView).getModel().getCodeLocations()).isEqualTo(stackTrace.getCodeLocations());
  }

  @Test
  public void callStackNavigationChangesProfilerMode() {
    HttpData data = new HttpData.Builder(DEFAULT_DATA).build();
    myView.setHttpData(data);

    StackTrace stackTrace = new StackTrace(myStageView.getStage().getConnectionsModel(), data);
    assertThat(stackTrace.getTrace()).isEqualTo(fakeTrace);
    assertThat(stackTrace.getCodeLocations()).hasSize(1);

    // Expands Profiler Mode
    myStage.getStudioProfilers().getTimeline().getSelectionRange().set(0, 10);

    boolean[] modeChanged = {false};
    AspectObserver observer = new AspectObserver();
    myStage.getStudioProfilers().addDependency(observer).onChange(ProfilerAspect.MODE, () -> modeChanged[0] = true);

    assertThat(modeChanged[0]).isFalse();
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);
    getStackTraceView(myView).getModel().setSelectedIndex(0);
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

  private static StackTraceView getStackTraceView(@NotNull ConnectionDetailsView view) {
    CallStackTabContent callStackContent =
      (CallStackTabContent)view.getTabs().stream().filter(tab -> tab instanceof CallStackTabContent).findFirst().get();

    return callStackContent.getStackTraceView();
  }

  private void assertExpectedTimingLegends(long startTimeUs,
                                           long downloadingTimeUs,
                                           long endTimeUs,
                                           String sentLegend,
                                           String receivedLegend) {
    // uploadedTime isn't used in legends (at the moment anyway) so just stub it for now
    long uploadedTimeUs = startTimeUs;
    HttpData data =
      new HttpData.Builder(0, startTimeUs, uploadedTimeUs, downloadingTimeUs, endTimeUs, TestHttpData.FAKE_THREAD_LIST).setUrl("unusedUrl")
        .build();
    myView.setHttpData(data);

    LegendComponent legendComponent = firstDescendantWithType(myView, LegendComponent.class);
    List<Legend> legends = legendComponent.getModel().getLegends();

    assertThat(legends.get(0).getValue()).isEqualTo(sentLegend);
    assertThat(legends.get(1).getValue()).isEqualTo(receivedLegend);
  }
}
