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
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import com.android.tools.profilers.*;
import com.android.tools.profilers.network.FakeNetworkService;
import com.android.tools.profilers.network.NetworkProfilerStage;
import com.android.tools.profilers.network.NetworkProfilerStageView;
import com.android.tools.profilers.network.TestHttpData;
import com.android.tools.profilers.network.httpdata.HttpData;
import com.android.tools.profilers.network.httpdata.StackTrace;
import com.android.tools.profilers.stacktrace.StackTraceModel;
import com.android.tools.profilers.stacktrace.StackTraceView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
  /**
   * Header names chosen and intentionally unsorted, to make sure that they are shown in the UI in sorted order.
   */
  private static final String TEST_HEADERS = "car = car-value \n border = border-value \n apple = apple-value \n 123 = numeric-value \n";
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

  /**
   * Will throw an exception if no match is found.
   */
  @SuppressWarnings("unchecked") // Cast is safe as filter + findFirst guarantees a match
  @NotNull
  private static <C extends Component> C firstDescendantWithType(Component root, Class<C> type) {
    return (C)new TreeWalker(root).descendantStream().filter(type::isInstance).findFirst().get();
  }

  @Nullable
  private static <T extends TabContent> T findTab(@NotNull ConnectionDetailsView view, @NotNull Class<T> tabClass) {
    //noinspection unchecked - cast is safe because we filter by that type.
    return (T)view.getTabs().stream().filter(tabClass::isInstance).findFirst().orElse(null);
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
    assertThat(findTab(myView, HeadersTabContent.class)).isNotNull();
    assertThat(findTab(myView, RequestTabContent.class)).isNull();

    myIdeProfilerServices.enableRequestPayload(true);
    myView = new ConnectionDetailsView(myStageView);
    assertThat(findTab(myView, HeadersTabContent.class)).isNull();
    assertThat(findTab(myView, RequestTabContent.class)).isNotNull();
  }

  @Test
  public void viewerForRequestPayloadIsPresentWhenRequestPayloadIsNotNull() {
    myIdeProfilerServices.enableRequestPayload(true);
    myView = new ConnectionDetailsView(myStageView);

    myProfilerService.addFile(TEST_REQUEST_PAYLOAD_ID, ByteString.copyFromUtf8("Dummy Content"));

    HttpData data =
      new HttpData.Builder(DEFAULT_DATA).setRequestPayloadId(TEST_REQUEST_PAYLOAD_ID).setResponseFields(RESPONSE_HEADERS).build();
    assertThat(findTab(myView, RequestTabContent.class).findPayloadViewer()).isNull();

    myView.setHttpData(data);
    assertThat(findTab(myView, RequestTabContent.class).findPayloadViewer()).isNotNull();
  }

  @Test
  public void viewerForRequestPayloadIsAbsentWhenRequestPayloadIsNull() {
    myIdeProfilerServices.enableRequestPayload(true);
    myView = new ConnectionDetailsView(myStageView);

    HttpData data = new HttpData.Builder(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();

    myView.setHttpData(data);
    assertThat(findTab(myView, RequestTabContent.class).findPayloadViewer()).isNull();
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
  public void viewerExistsWhenPayloadIsPresent() {
    myProfilerService.addFile(TEST_RESPONSE_PAYLOAD_ID, ByteString.copyFromUtf8("Dummy Content"));

    HttpData data = new HttpData.Builder(DEFAULT_DATA)
      .setResponseFields(RESPONSE_HEADERS)
      .setResponsePayloadId(TEST_RESPONSE_PAYLOAD_ID)
      .build();

    assertThat(findTab(myView, OverviewTabContent.class).findResponsePayloadViewer()).isNull();

    myView.setHttpData(data);
    assertThat(findTab(myView, OverviewTabContent.class).findResponsePayloadViewer()).isNotNull();
  }

  @Test
  public void contentTypeHasProperValueFromData() {
    assertThat(findTab(myView, OverviewTabContent.class).findContentTypeValue()).isNull();
    HttpData data = new HttpData.Builder(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();

    myView.setHttpData(data);
    JLabel value = findTab(myView, OverviewTabContent.class).findContentTypeValue();
    assertThat(value.getText()).isEqualTo("111");
  }

  @Test
  public void contentTypeIsAbsentWhenDataHasNoContentTypeValue() {
    myView.setHttpData(DEFAULT_DATA);
    assertThat(findTab(myView, OverviewTabContent.class).findContentTypeValue()).isNull();
  }

  @Test
  public void initiatingThreadFieldIsPresent() {
    myView.setHttpData(DEFAULT_DATA);
    assertThat(DEFAULT_DATA.getJavaThreads()).hasSize(1);
    assertThat(findTab(myView, OverviewTabContent.class).findInitiatingThreadValue()).isNotNull();
    assertThat(findTab(myView, OverviewTabContent.class).findOtherThreadsValue()).isNull();
  }

  @Test
  public void otherThreadsFieldIsPresent() {
    HttpData data = new HttpData.Builder(DEFAULT_DATA).addJavaThread(new HttpData.JavaThread(2, "thread2")).build();
    assertThat(data.getJavaThreads()).hasSize(2);
    myView.setHttpData(data);
    assertThat(findTab(myView, OverviewTabContent.class).findOtherThreadsValue()).isNotNull();
  }

  @Test
  public void urlHasProperValueFromData() {
    assertThat(findTab(myView, OverviewTabContent.class).findUrlValue()).isNull();

    myView.setHttpData(DEFAULT_DATA);
    JTextArea value = findTab(myView, OverviewTabContent.class).findUrlValue();

    assertThat(value.getText()).isEqualTo("dumbUrl");
  }

  @Test
  public void sizeHasProperValueFromData() {
    assertThat(findTab(myView, OverviewTabContent.class).findSizeValue()).isNull();

    HttpData data = new HttpData.Builder(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();
    myView.setHttpData(data);
    JLabel value = findTab(myView, OverviewTabContent.class).findSizeValue();
    assertThat(value.getText()).isEqualTo("222B");
  }

  @Test
  public void timingFieldIsPresent() {
    assertThat(findTab(myView, OverviewTabContent.class).findTimingBar()).isNull();
    myView.setHttpData(DEFAULT_DATA);
    assertThat(findTab(myView, OverviewTabContent.class).findTimingBar()).isNotNull();
  }

  @Test
  public void headersIsUpdated() {
    HeadersTabContent headersTab = findTab(myView, HeadersTabContent.class);
    assertThat(headersTab.findRequestHeadersSection()).isNull();

    HttpData data = new HttpData.Builder(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();
    myView.setHttpData(data);
    assertThat(headersTab.findRequestHeadersSection()).isNotNull();
  }

  @Test
  public void headerSectionIsSorted() {
    HttpData data = new HttpData.Builder(DEFAULT_DATA).setRequestFields(TEST_HEADERS).build();
    myView.setHttpData(data);
    JPanel requestHeadersPanel = findTab(myView, HeadersTabContent.class).findRequestHeadersSection();
    String text = firstDescendantWithType(requestHeadersPanel, JTextPane.class).getText();

    assertUiContainsLabelAndValue(text, "123", "numeric-value");
    assertUiContainsLabelAndValue(text, "apple", "apple-value");
    assertUiContainsLabelAndValue(text, "border", "border-value");
    assertUiContainsLabelAndValue(text, "car", "car-value");

    assertThat(text.indexOf("123")).isGreaterThan(-1);
    assertThat(text.indexOf("123")).isLessThan(text.indexOf("apple"));
    assertThat(text.indexOf("apple")).isLessThan(text.indexOf("border"));
    assertThat(text.indexOf("border")).isLessThan(text.indexOf("car"));
  }

  private void assertUiContainsLabelAndValue(String uiText, String label, String value) {
    assertThat(uiText).containsMatch(String.format("\\b%s\\b.+\\b%s\\b", label, value));
  }

  @Test
  public void callStackViewHasProperValueFromData() {
    AspectObserver observer = new AspectObserver();
    final int[] stackFramesChangedCount = {0};
    StackTraceView stackTraceView = findTab(myView, CallStackTabContent.class).getStackTraceView();

    stackTraceView.getModel().addDependency(observer)
      .onChange(StackTraceModel.Aspect.STACK_FRAMES, () -> stackFramesChangedCount[0]++);

    assertThat(stackFramesChangedCount[0]).isEqualTo(0);
    assertThat(stackTraceView.getModel().getCodeLocations()).hasSize(0);

    myView.setHttpData(DEFAULT_DATA);
    assertThat(stackFramesChangedCount[0]).isEqualTo(1);

    StackTrace stackTrace = new StackTrace(myStageView.getStage().getConnectionsModel(), DEFAULT_DATA);
    assertThat(stackTraceView.getModel().getCodeLocations()).isEqualTo(stackTrace.getCodeLocations());
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

    StackTraceView stackTraceView = findTab(myView, CallStackTabContent.class).getStackTraceView();
    stackTraceView.getModel().setSelectedIndex(0);
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
