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

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

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
    assertTrue(myView.isVisible());
  }

  @Test
  public void viewIsNotVisibleWhenDataIsNull() {
    myView.setVisible(true);
    myView.update((HttpData)null);
    assertFalse(myView.isVisible());
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
    assertNotEquals(0, response.getComponentCount());
    verify(myView.getStackTraceView()).clearStackFrames();
    verify(myView.getStackTraceView()).setStackFrames(data.getStackTrace().getCodeLocations());
    reset(myView.getStackTraceView());

    myView.update((HttpData)null);
    stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    response = (JComponent) stream.filter(c -> "Response".equals(c.getName())).findFirst().get();
    assertEquals(0, response.getComponentCount());
    verify(myView.getStackTraceView()).clearStackFrames();
    verify(myView.getStackTraceView(), never()).setStackFrames(anyString());
  }

  @Test
  public void fileViewerExistWhenPayloadFileIsNotNull() {
    File file = new File("temp");
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();
    data.setResponsePayloadFile(file);
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    assertFalse(stream.anyMatch(c -> "FileViewer".equals(c.getName())));
    myView.update(data);
    stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    assertTrue(stream.anyMatch(c -> "FileViewer".equals(c.getName())));
  }

  @Test
  public void contentTypeHasProperValueFromData() {
    String valueName = "Content type";
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    assertFalse(stream.anyMatch(c -> valueName.equals(c.getName())));
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();
    myView.update(data);
    stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    JLabel value = (JLabel) stream.filter(c -> valueName.equals(c.getName())).findFirst().get();
    assertEquals("111", value.getText());
  }

  @Test
  public void contentTypeIsAbsentWhenDataHasNoContentTypeValue() {
    myView.update(DEFAULT_DATA);
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    assertFalse(stream.anyMatch(c -> "Content type".equals(c.getName())));
  }

  @Test
  public void urlHasProperValueFromData() {
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    assertFalse(stream.anyMatch(c -> "URL".equals(c.getName())));
    myView.update(DEFAULT_DATA);
    stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    JTextArea value = (JTextArea) stream.filter(c -> "URL".equals(c.getName())).findFirst().get();
    assertEquals("dumbUrl", value.getText());
  }

  @Test
  public void sizeHasProperValueFromData() {
    String valueName = "Size";
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    assertFalse(stream.anyMatch(c -> valueName.equals(c.getName())));
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();
    myView.update(data);
    stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    JLabel value = (JLabel) stream.filter(c -> valueName.equals(c.getName())).findFirst().get();
    assertEquals("222B", value.getText());
  }

  @Test
  public void contentLengthIsAbsentWhenDataHasNoContentLengthValue() {
    myView.update(DEFAULT_DATA);
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    assertFalse(stream.anyMatch(c -> "Content length".equals(c.getName())));
  }

  @Test
  public void timingFieldIsPresent() {
    myView.update(DEFAULT_DATA);
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    assertTrue(stream.anyMatch(c -> "Timing".equals(c.getName())));
  }

  @Test
  public void headersIsUpdated() {
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    JPanel headers = (JPanel)stream.filter(c -> "Headers".equals(c.getName())).findFirst().get();
    assertEquals(0, headers.getComponentCount());
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).setResponseFields(RESPONSE_HEADERS).build();
    myView.update(data);
    assertNotEquals(0, headers.getComponentCount());
  }

  @Test
  public void headerSectionIsSorted() {
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).setRequestFields(TEST_HEADERS).build();
    myView.update(data);
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    JPanel headers = (JPanel)stream.filter(c -> "Headers".equals(c.getName())).findFirst().get();
    stream = new TreeWalker(headers).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    JPanel responseHeaders = (JPanel)stream.filter(c -> "Request Headers".equals(c.getName())).findFirst().get();
    assertEquals("123", responseHeaders.getComponent(1).getName());
    assertEquals("apple", responseHeaders.getComponent(2).getName());
    assertEquals("border", responseHeaders.getComponent(3).getName());
    assertEquals("car", responseHeaders.getComponent(4).getName());
  }

  @Test
  public void callStackViewHasProperValueFromData() {
    assertEquals(0, myView.getStackTraceView().getCodeLocations().size());

    myView.update(DEFAULT_DATA);
    verify(myView.getStackTraceView()).clearStackFrames();
    verify(myView.getStackTraceView()).setStackFrames(DEFAULT_DATA.getStackTrace().getCodeLocations());
  }

  @Test
  public void callStackNavigationChangesProfilerMode() {
    HttpData data = getBuilderFromHttpData(DEFAULT_DATA).setTrace(FakeNetworkService.FAKE_STACK_TRACE).build();
    myView.update(data);
    assertEquals(2, data.getStackTrace().getCodeLocations().size());

    // Expands Profiler Mode
    myStage.getStudioProfilers().getTimeline().getSelectionRange().set(0, 10);

    boolean modeChanged[] = {false};
    AspectObserver observer = new AspectObserver();
    myStage.getStudioProfilers().addDependency(observer).onChange(ProfilerAspect.MODE, () -> modeChanged[0] = true);

    assertFalse(modeChanged[0]);
    assertEquals(ProfilerMode.EXPANDED, myStage.getProfilerMode());
    myView.getStackTraceView().selectCodeLocation(data.getStackTrace().getCodeLocations().get(0));
    assertTrue(modeChanged[0]);
    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());
  }
}
