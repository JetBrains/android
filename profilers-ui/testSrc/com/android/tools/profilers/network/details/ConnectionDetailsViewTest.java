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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.TreeWalker;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.legend.Legend;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profilers.FakeIdeProfilerComponents;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.FakeProfilerService;
import com.android.tools.profilers.ProfilerAspect;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.ProfilerMode;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.StudioProfilersView;
import com.android.tools.profilers.network.FakeNetworkService;
import com.android.tools.profilers.network.NetworkProfilerStage;
import com.android.tools.profilers.network.NetworkProfilerStageView;
import com.android.tools.profilers.network.TestHttpData;
import com.android.tools.profilers.network.httpdata.HttpData;
import com.android.tools.profilers.network.httpdata.StackTrace;
import com.android.tools.profilers.stacktrace.StackTraceModel;
import com.android.tools.profilers.stacktrace.StackTraceView;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import java.awt.Component;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@RunsInEdt
public class ConnectionDetailsViewTest {
  private static final String fakeTrace = "com.google.downloadUrlToStream(ImageFetcher.java:274)";
  private static final HttpData DEFAULT_DATA =
    new HttpData.Builder(1, 10000, 25000, 50000, 100000, 100000, TestHttpData.FAKE_THREAD_LIST)
      .setUrl("dumbUrl").setTrace(fakeTrace).setMethod("GET")
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

  private final FakeTimer myTimer = new FakeTimer();
  private final FakeTransportService myTransportService = new FakeTransportService(myTimer, false);
  @Rule public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("StudioProfilerTestChannel", myTransportService, new FakeProfilerService(myTimer),
                        FakeNetworkService.newBuilder().setHttpDataList(Collections.singletonList(DEFAULT_DATA)).build());

  @Rule public final EdtRule myEdtRule = new EdtRule();

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
    myIdeProfilerServices = new FakeIdeProfilerServices();
    StudioProfilers profilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), myIdeProfilerServices, myTimer);
    NetworkProfilerStage stage = new NetworkProfilerStage(profilers);
    StudioProfilersView view = new StudioProfilersView(profilers, new FakeIdeProfilerComponents());
    profilers.setStage(stage);
    myStageView = (NetworkProfilerStageView)view.getStageView();
    myStage = myStageView.getStage();
    myView = new ConnectionDetailsView(myStageView);

    myTransportService.addFile(TestHttpData.fakeStackTraceId(fakeTrace), ByteString.copyFromUtf8(fakeTrace));
  }

  @Test
  public void viewerForRequestPayloadIsPresentWhenRequestPayloadIsNotNull() {
    myView = new ConnectionDetailsView(myStageView);

    myTransportService.addFile(TEST_REQUEST_PAYLOAD_ID, ByteString.copyFromUtf8("Sample Content"));

    HttpData data =
      new HttpData.Builder(DEFAULT_DATA).setRequestPayloadId(TEST_REQUEST_PAYLOAD_ID).setResponseFields(RESPONSE_HEADERS).build();
    assertThat(HttpDataComponentFactory.findPayloadViewer(findTab(myView, RequestTabContent.class).findPayloadBody())).isNull();

    myView.setHttpData(data);
    assertThat(HttpDataComponentFactory.findPayloadViewer(findTab(myView, RequestTabContent.class).findPayloadBody())).isNotNull();
  }

  @Test
  public void viewerForRequestPayloadIsAbsentWhenRequestPayloadIsNull() {
    myView = new ConnectionDetailsView(myStageView);

    HttpData data = new HttpData.Builder(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();

    myView.setHttpData(data);
    assertThat(HttpDataComponentFactory.findPayloadViewer(findTab(myView, RequestTabContent.class).findPayloadBody())).isNull();
  }

  @Test
  public void requestPayloadHasBothParsedViewAndRawDataView() {
    myView = new ConnectionDetailsView(myStageView);

    HttpData data = new HttpData.Builder(DEFAULT_DATA).setRequestFields("Content-Type = application/x-www-form-urlencoded")
      .setResponseFields(RESPONSE_HEADERS).setRequestPayloadId(TEST_REQUEST_PAYLOAD_ID)
      .build();
    myTransportService.addFile(TEST_REQUEST_PAYLOAD_ID, ByteString.copyFromUtf8("a=1&b=2"));
    myView.setHttpData(data);

    JComponent payloadBody = findTab(myView, RequestTabContent.class).findPayloadBody();
    assertThat(payloadBody).isNotNull();
    assertThat(new TreeWalker(payloadBody).descendantStream().anyMatch(c -> c.getName().equals("View Parsed"))).isTrue();
    assertThat(new TreeWalker(payloadBody).descendantStream().anyMatch(c -> c.getName().equals("View Source"))).isTrue();
  }

  @Test
  public void responsePayloadHasBothParsedViewAndRawDataView() {
    myView = new ConnectionDetailsView(myStageView);

    HttpData data =
      new HttpData.Builder(DEFAULT_DATA).setResponseFields("null =  HTTP/1.1 302 Found\n Content-Type = application/x-www-form-urlencoded")
        .setResponsePayloadId(TEST_RESPONSE_PAYLOAD_ID).build();
    myTransportService.addFile(TEST_RESPONSE_PAYLOAD_ID, ByteString.copyFromUtf8("a=1&b=2"));
    myView.setHttpData(data);

    JComponent payloadBody = findTab(myView, ResponseTabContent.class).findPayloadBody();
    assertThat(payloadBody).isNotNull();
    assertThat(new TreeWalker(payloadBody).descendantStream().anyMatch(c -> c.getName().equals("View Parsed"))).isTrue();
    assertThat(new TreeWalker(payloadBody).descendantStream().anyMatch(c -> c.getName().equals("View Source"))).isTrue();
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
    myTransportService.addFile(TEST_RESPONSE_PAYLOAD_ID, ByteString.copyFromUtf8("Sample Content"));

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
    assertThat(value.getText()).isEqualTo("222 B");
  }

  @Test
  public void timingFieldIsPresent() {
    assertThat(findTab(myView, OverviewTabContent.class).findTimingBar()).isNull();
    myView.setHttpData(DEFAULT_DATA);
    assertThat(findTab(myView, OverviewTabContent.class).findTimingBar()).isNotNull();
  }

  @Test
  public void headerSectionIsSorted() {
    HttpData data = new HttpData.Builder(DEFAULT_DATA).setRequestFields(TEST_HEADERS).build();
    myView.setHttpData(data);
    TabContent tabContent = findTab(myView, RequestTabContent.class);
    String text = firstDescendantWithType(tabContent.getComponent(), JTextPane.class).getText();

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
  public void expectedDisplayNameForContentTypes() {
    assertThat(HttpDataComponentFactory.getDisplayName(new HttpData.ContentType(""))).isEqualTo("");
    assertThat(HttpDataComponentFactory.getDisplayName(new HttpData.ContentType(" "))).isEqualTo("");
    assertThat(HttpDataComponentFactory.getDisplayName(new HttpData.ContentType("application/x-www-form-urlencoded; charset=utf-8")))
      .isEqualTo("Form Data");
    assertThat(HttpDataComponentFactory.getDisplayName(new HttpData.ContentType("text/html"))).isEqualTo("HTML");
    assertThat(HttpDataComponentFactory.getDisplayName(new HttpData.ContentType("application/json"))).isEqualTo("JSON");
    assertThat(HttpDataComponentFactory.getDisplayName(new HttpData.ContentType("image/jpeg"))).isEqualTo("Image");
    assertThat(HttpDataComponentFactory.getDisplayName(new HttpData.ContentType("audio/webm"))).isEqualTo("Audio");
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

    StackTrace stackTrace = new StackTrace(DEFAULT_DATA);
    assertThat(stackTraceView.getModel().getCodeLocations()).isEqualTo(stackTrace.getCodeLocations());
  }

  @Test
  public void callStackNavigationChangesProfilerMode() {
    HttpData data = new HttpData.Builder(DEFAULT_DATA).build();
    myView.setHttpData(data);

    StackTrace stackTrace = new StackTrace(data);
    assertThat(stackTrace.getTrace()).isEqualTo(data.getTrace());
    assertThat(stackTrace.getCodeLocations()).hasSize(1);

    // Expands Profiler Mode
    myStage.getTimeline().getSelectionRange().set(0, 10);

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

    assertExpectedTimingLegends(TimeUnit.MILLISECONDS.toMicros(1000), TimeUnit.MILLISECONDS.toMicros(1000), 0, "0 ms", "*");
    assertExpectedTimingLegends(TimeUnit.MILLISECONDS.toMicros(1000), TimeUnit.MILLISECONDS.toMicros(2500), 0, "1 s 500 ms", "*");

    assertExpectedTimingLegends(TimeUnit.MILLISECONDS.toMicros(1000), TimeUnit.MILLISECONDS.toMicros(3000),
                                TimeUnit.MILLISECONDS.toMicros(3000), "2 s", "0 ms");
    assertExpectedTimingLegends(TimeUnit.MILLISECONDS.toMicros(1000), TimeUnit.MILLISECONDS.toMicros(3000),
                                TimeUnit.MILLISECONDS.toMicros(4234), "2 s", "1 s 234 ms");

    assertExpectedTimingLegends(TimeUnit.MILLISECONDS.toMicros(1000), 0, TimeUnit.MILLISECONDS.toMicros(1000), "0 ms", "0 ms");
    assertExpectedTimingLegends(TimeUnit.MILLISECONDS.toMicros(1000), 0, TimeUnit.MILLISECONDS.toMicros(2000), "1 s", "0 ms");
  }

  private void assertExpectedTimingLegends(long startTimeUs,
                                           long downloadingTimeUs,
                                           long endTimeUs,
                                           String sentLegend,
                                           String receivedLegend) {
    // uploadedTime isn't used in legends (at the moment anyway) so just stub it for now
    long uploadedTimeUs = startTimeUs;
    HttpData data =
      new HttpData.Builder(0, startTimeUs, uploadedTimeUs, downloadingTimeUs, endTimeUs, endTimeUs, TestHttpData.FAKE_THREAD_LIST)
        .setUrl("unusedUrl")
        .build();
    myView.setHttpData(data);

    LegendComponent legendComponent = firstDescendantWithType(myView, LegendComponent.class);
    List<Legend> legends = legendComponent.getModel().getLegends();

    assertThat(legends.get(0).getValue()).isEqualTo(sentLegend);
    assertThat(legends.get(1).getValue()).isEqualTo(receivedLegend);
  }
}
