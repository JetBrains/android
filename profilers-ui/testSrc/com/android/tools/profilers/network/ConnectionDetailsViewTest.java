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

import com.android.testutils.TestResources;
import com.android.tools.adtui.TreeWalker;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.profilers.*;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Collections;
import java.util.stream.Stream;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

public class ConnectionDetailsViewTest {

  private static final HttpData DEFAULT_DATA = new HttpData.Builder(1, 10000, 50000, 100000)
    .setUrl("dumbUrl").setTrace("dumbTrace").setMethod("GET").build();
  private static final String RESPONSE_HEADERS = "null =  HTTP/1.1 302 Found \n Content-Type = 111 \n Content-Length = 222 \n";
  private static final String TEST_HEADERS = "car = value \n border = value \n apple = value \n 123 = value \n";

  private ConnectionDetailsView myView;

  private NetworkProfilerStage myStage;

  @Rule public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("StudioProfilerTestChannel", new FakeProfilerService(false),
                        FakeNetworkService.newBuilder().setHttpDataList(Collections.singletonList(DEFAULT_DATA)).build());

  @Before
  public void before() {
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices());
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
    myView.update(DEFAULT_DATA);
    assertThat(myView.isVisible()).isTrue();
  }

  @Test
  public void viewIsNotVisibleWhenDataIsNull() {
    myView.setVisible(true);
    myView.update((HttpData)null);
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
    File file = TestResources.getFile(this.getClass(), "/icons/garbage-event.png");
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).build();
    data.setResponsePayloadFile(file);
    myView.update(data);

    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    JComponent response = (JComponent) stream.filter(c -> "Response".equals(c.getName())).findFirst().get();
    assertThat(response.getComponentCount()).isNotEqualTo(0);
    verify(myView.getStackTraceView()).clearStackFrames();
    verify(myView.getStackTraceView()).setStackFrames(data.getStackTrace().getCodeLocations());
    reset(myView.getStackTraceView());

    myView.update((HttpData)null);
    stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    response = (JComponent) stream.filter(c -> "Response".equals(c.getName())).findFirst().get();
    assertThat(response.getComponentCount()).isEqualTo(0);
    verify(myView.getStackTraceView()).clearStackFrames();
    verify(myView.getStackTraceView(), never()).setStackFrames(anyString());
  }

  @Test
  public void fileViewerExistWhenPayloadFileIsNotNull() {
    File file = new File("temp");
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();
    data.setResponsePayloadFile(file);
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    assertThat(stream.anyMatch(c -> "FileViewer".equals(c.getName()))).isFalse();
    myView.update(data);
    stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    assertThat(stream.anyMatch(c -> "FileViewer".equals(c.getName()))).isTrue();
  }

  @Test
  public void contentTypeHasProperValueFromData() {
    String valueName = "Content type";
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    assertThat(stream.anyMatch(c -> valueName.equals(c.getName()))).isFalse();
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();
    myView.update(data);
    stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    JLabel value = (JLabel) stream.filter(c -> valueName.equals(c.getName())).findFirst().get();
    assertThat(value.getText()).isEqualTo("111");
  }

  @Test
  public void contentTypeIsAbsentWhenDataHasNoContentTypeValue() {
    myView.update(DEFAULT_DATA);
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    assertThat(stream.anyMatch(c -> "Content type".equals(c.getName()))).isFalse();
  }

  @Test
  public void urlHasProperValueFromData() {
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    assertThat(stream.anyMatch(c -> "URL".equals(c.getName()))).isFalse();
    myView.update(DEFAULT_DATA);
    stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    JTextArea value = (JTextArea) stream.filter(c -> "URL".equals(c.getName())).findFirst().get();
    assertThat(value.getText()).isEqualTo("dumbUrl");
  }

  @Test
  public void sizeHasProperValueFromData() {
    String valueName = "Size";
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    assertThat(stream.anyMatch(c -> valueName.equals(c.getName()))).isFalse();
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();
    myView.update(data);
    stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    JLabel value = (JLabel) stream.filter(c -> valueName.equals(c.getName())).findFirst().get();
    assertThat(value.getText()).isEqualTo("222B");
  }

  @Test
  public void contentLengthIsAbsentWhenDataHasNoContentLengthValue() {
    myView.update(DEFAULT_DATA);
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    assertThat(stream.anyMatch(c -> "Content length".equals(c.getName()))).isFalse();
  }

  @Test
  public void timingFieldIsPresent() {
    myView.update(DEFAULT_DATA);
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    assertThat(stream.anyMatch(c -> "Timing".equals(c.getName()))).isTrue();
  }

  @Test
  public void headersIsUpdated() {
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    JPanel headers = (JPanel)stream.filter(c -> "Headers".equals(c.getName())).findFirst().get();
    assertThat(headers.getComponentCount()).isEqualTo(0);
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();
    myView.update(data);
    assertThat(headers.getComponentCount()).isNotEqualTo(0);
  }

  @Test
  public void headerSectionIsSorted() {
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).setRequestFields(TEST_HEADERS).build();
    myView.update(data);
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    JPanel headers = (JPanel)stream.filter(c -> "Headers".equals(c.getName())).findFirst().get();
    stream = new TreeWalker(headers).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    JPanel responseHeaders = (JPanel)stream.filter(c -> "Request Headers".equals(c.getName())).findFirst().get();
    assertThat(responseHeaders.getComponent(1).getName()).isEqualTo("123");
    assertThat(responseHeaders.getComponent(2).getName()).isEqualTo("apple");
    assertThat(responseHeaders.getComponent(3).getName()).isEqualTo("border");
    assertThat(responseHeaders.getComponent(4).getName()).isEqualTo("car");
  }

  @Test
  public void callStackViewHasProperValueFromData() {
    assertThat(myView.getStackTraceView().getCodeLocations().size()).isEqualTo(0);

    myView.update(DEFAULT_DATA);
    verify(myView.getStackTraceView()).clearStackFrames();
    verify(myView.getStackTraceView()).setStackFrames(DEFAULT_DATA.getStackTrace().getCodeLocations());
  }

  @Test
  public void callStackNavigationChangesProfilerMode() {
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).setTrace(FakeNetworkService.FAKE_STACK_TRACE).build();
    myView.update(data);
    assertThat(data.getStackTrace().getCodeLocations().size()).isEqualTo(2);

    // Expands Profiler Mode
    myStage.getStudioProfilers().getTimeline().getSelectionRange().set(0, 10);

    boolean modeChanged[] = {false};
    AspectObserver observer = new AspectObserver();
    myStage.getStudioProfilers().addDependency(observer).onChange(ProfilerAspect.MODE, () -> modeChanged[0] = true);

    assertThat(modeChanged[0]).isFalse();
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);
    myView.getStackTraceView().selectCodeLocation(data.getStackTrace().getCodeLocations().get(0));
    assertThat(modeChanged[0]).isTrue();
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
  }
}
