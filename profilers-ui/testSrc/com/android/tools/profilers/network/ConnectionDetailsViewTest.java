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
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.google.common.truth.Truth.assertThat;

public class ConnectionDetailsViewTest {
  private static final String dumbTrace = "com.google.downloadUrlToStream(ImageFetcher.java:274)";
  private static final HttpData DEFAULT_DATA = new HttpData.Builder(1, 10000, 50000, 100000)
    .setUrl("dumbUrl").setTrace(dumbTrace).setMethod("GET").build();
  private static final String RESPONSE_HEADERS = "null =  HTTP/1.1 302 Found \n Content-Type = 111 \n Content-Length = 222 \n";
  private static final String TEST_HEADERS = "car = value \n border = value \n apple = value \n 123 = value \n";
  private static final String TEST_RESOURCE_DIR = "tools/adt/idea/profilers-ui/testData/visualtests/";

  private ConnectionDetailsView myView;

  private NetworkProfilerStage myStage;

  @Rule public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("StudioProfilerTestChannel", new FakeProfilerService(false),
                        FakeNetworkService.newBuilder().setHttpDataList(Collections.singletonList(DEFAULT_DATA)).build());

  @Before
  public void before() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), timer);
    NetworkProfilerStage stage = new NetworkProfilerStage(profilers);
    StudioProfilersView view = new StudioProfilersView(profilers, new FakeIdeProfilerComponents());
    profilers.setStage(stage);
    NetworkProfilerStageView networkView = (NetworkProfilerStageView)view.getStageView();
    myStage = networkView.getStage();
    myView = new ConnectionDetailsView(networkView);
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
    HttpData.Builder builder = new HttpData.Builder(data.getId(), data.getStartTimeUs(), data.getEndTimeUs(), data.getDownloadingTimeUs());
    builder.setUrl(data.getUrl());
    builder.setMethod(data.getMethod());
    builder.setTrace(data.getStackTrace().getTrace());
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

    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    JComponent response = (JComponent)stream.filter(c -> "Response".equals(c.getName())).findFirst().get();
    assertThat(response.getComponentCount()).isNotEqualTo(0);
    assertThat(myView.getStackTraceView().getModel().getCodeLocations()).isNotEmpty();

    myView.setHttpData(null);
    assertThat(stackFramesChangedCount[0]).isEqualTo(2);

    stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    response = (JComponent)stream.filter(c -> "Response".equals(c.getName())).findFirst().get();
    assertThat(response.getComponentCount()).isEqualTo(0);
    assertThat(myView.getStackTraceView().getModel().getCodeLocations()).isEmpty();
  }

  @Test
  public void fileViewerExistWhenPayloadFileIsNotNull() {
    File file = new File("temp");
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();
    data.setResponsePayloadFile(file);
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    assertThat(stream.anyMatch(c -> "FileViewer".equals(c.getName()))).isFalse();
    myView.setHttpData(data);
    stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    assertThat(stream.anyMatch(c -> "FileViewer".equals(c.getName()))).isTrue();
  }

  @Test
  public void contentTypeHasProperValueFromData() {
    String valueName = "Content type";
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    assertThat(stream.anyMatch(c -> valueName.equals(c.getName()))).isFalse();
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();
    myView.setHttpData(data);
    stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    JLabel value = (JLabel)stream.filter(c -> valueName.equals(c.getName())).findFirst().get();
    assertThat(value.getText()).isEqualTo("111");
  }

  @Test
  public void contentTypeIsAbsentWhenDataHasNoContentTypeValue() {
    myView.setHttpData(DEFAULT_DATA);
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    assertThat(stream.anyMatch(c -> "Content type".equals(c.getName()))).isFalse();
  }

  @Test
  public void urlHasProperValueFromData() {
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    assertThat(stream.anyMatch(c -> "URL".equals(c.getName()))).isFalse();
    myView.setHttpData(DEFAULT_DATA);
    stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    JTextArea value = (JTextArea)stream.filter(c -> "URL".equals(c.getName())).findFirst().get();
    assertThat(value.getText()).isEqualTo("dumbUrl");
  }

  @Test
  public void sizeHasProperValueFromData() {
    String valueName = "Size";
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    assertThat(stream.anyMatch(c -> valueName.equals(c.getName()))).isFalse();
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();
    myView.setHttpData(data);
    stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    JLabel value = (JLabel)stream.filter(c -> valueName.equals(c.getName())).findFirst().get();
    assertThat(value.getText()).isEqualTo("222B");
  }

  @Test
  public void contentLengthIsAbsentWhenDataHasNoContentLengthValue() {
    myView.setHttpData(DEFAULT_DATA);
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    assertThat(stream.anyMatch(c -> "Content length".equals(c.getName()))).isFalse();
  }

  @Test
  public void timingFieldIsPresent() {
    myView.setHttpData(DEFAULT_DATA);
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    assertThat(stream.anyMatch(c -> "Timing".equals(c.getName()))).isTrue();
  }

  @Test
  public void headersIsUpdated() {
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    JPanel headers = (JPanel)stream.filter(c -> "Headers".equals(c.getName())).findFirst().get();
    assertThat(headers.getComponentCount()).isEqualTo(0);
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();
    myView.setHttpData(data);
    assertThat(headers.getComponentCount()).isNotEqualTo(0);
  }

  @Test
  @Ignore
  // Failing on mac -> font-family: [Lucida Grande] (Windows and Linux is -> font-family: Dialog)
  public void headerSectionIsSortedAndFormatted() {
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).setRequestFields(TEST_HEADERS).build();
    myView.setHttpData(data);
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    JPanel headers = (JPanel)stream.filter(c -> "Headers".equals(c.getName())).findFirst().get();
    stream = new TreeWalker(headers).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    JPanel responseHeaders = (JPanel)stream.filter(c -> "Request Headers".equals(c.getName())).findFirst().get();

    String text = ((JTextPane)responseHeaders.getComponent(1)).getText();
    String idealText = "<html>\n" +
                       "  <head>\n" +
                       "    <style type=\"text/css\">\n" +
                       "      <!--\n" +
                       "        body { font-family: Dialog; font-size: 10.0pt }\n" +
                       "      -->\n" +
                       "    </style>\n" +
                       "    \n" +
                       "  </head>\n" +
                       "  <body>\n" +
                       "    <p>\n" +
                       "      <nobr><b>123:&#160;&#160;</b></nobr><span>value</span>\n" +
                       "    </p>\n" +
                       "    <p>\n" +
                       "      <nobr><b>apple:&#160;&#160;</b></nobr><span>value</span>\n" +
                       "    </p>\n" +
                       "    <p>\n" +
                       "      <nobr><b>border:&#160;&#160;</b></nobr><span>value</span>\n" +
                       "    </p>\n" +
                       "    <p>\n" +
                       "      <nobr><b>car:&#160;&#160;</b></nobr><span>value</span>\n" +
                       "    </p>\n" +
                       "  </body>\n" +
                       "</html>\n";
    assertThat(text).isEqualTo(idealText);
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
    HttpData data = new HttpData.Builder(0, startTimeUs, endTimeUs, downloadingTimeUs).setUrl("unusedUrl").setMethod("GET").build();
    myView.setHttpData(data);

    LegendComponent legendComponent =
      (LegendComponent)new TreeWalker(myView).descendantStream().filter(c -> c instanceof LegendComponent).findFirst().get();
    List<Legend> legends = legendComponent.getModel().getLegends();

    assertThat(legends.get(0).getValue()).isEqualTo(sentLegend);
    assertThat(legends.get(1).getValue()).isEqualTo(receivedLegend);
  }
}
