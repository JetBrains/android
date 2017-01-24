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
import com.android.tools.profilers.*;
import com.google.common.collect.ImmutableMap;
import com.intellij.ui.HyperlinkLabel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.stream.Stream;

import static org.junit.Assert.*;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class ConnectionDetailsViewTest {
  private static final ImmutableMap<String, String> TEST_HEADERS = ImmutableMap.<String, String>builder()
    .put("car", "value")
    .put("border", "value")
    .put("apple", "value")
    .put("123", "value").build();

  @Mock private IdeProfilerServices myIdeServices;
  @Mock private HttpData myHttpData;
  private ConnectionDetailsView myView;

  private final FakeProfilerService myService = new FakeProfilerService();

  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("StudioProfilerTestChannel", myService);

  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);
    when(myHttpData.getUrl()).thenReturn("dumbUrl");
    when(myHttpData.getTrace()).thenReturn("dumbTrace");
    when(myHttpData.getResponseHeaders()).thenReturn(ImmutableMap.of());
    when(myHttpData.getRequestHeaders()).thenReturn(ImmutableMap.of());

    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), myIdeServices);
    NetworkProfilerStage stage = new NetworkProfilerStage(profilers);
    StudioProfilersView view = new StudioProfilersView(profilers, new FakeIdeProfilerComponents());
    profilers.setStage(stage);

    NetworkProfilerStageView networkView = (NetworkProfilerStageView)view.getStageView();
    myView = new ConnectionDetailsView(networkView);
  }

  @Test
  public void viewIsVisibleWhenDataIsNotNull() {
    myView.setVisible(false);
    myView.update(myHttpData);
    assertTrue(myView.isVisible());
  }

  @Test
  public void viewIsNotVisibleWhenDataIsNull() {
    myView.setVisible(true);
    myView.update((HttpData)null);
    assertFalse(myView.isVisible());
  }

  @Test
  public void contentsAreEmptyWhenDataIsNull() {
    File file = TestResources.getFile(this.getClass(), "/icons/garbage-event.png");
    when(myHttpData.getResponsePayloadFile()).thenReturn(file);
    myView.update(myHttpData);
    assertNotNull(myView.getFileViewer());
    assertNotNull(myView.getFieldComponent(0));
    verify(myView.getStackTraceView()).clearStackFrames();
    verify(myView.getStackTraceView()).setStackFrames(myHttpData.getTrace());
    reset(myView.getStackTraceView());

    myView.update((HttpData)null);
    assertNull(myView.getFileViewer());
    assertNull(myView.getFieldComponent(0));
    verify(myView.getStackTraceView()).clearStackFrames();
    verify(myView.getStackTraceView(), never()).setStackFrames(anyString());
  }

  @Test
  public void fileViewerExistWhenPayloadFileIsNotNull() {
    File file = new File("temp");
    when(myHttpData.getResponsePayloadFile()).thenReturn(file);
    when(myHttpData.getResponseField(eq(HttpData.FIELD_CONTENT_TYPE))).thenReturn("test");
    assertNull(myView.getFileViewer());
    myView.update(myHttpData);
    assertNotNull(myView.getFileViewer());
  }

  @Test
  public void responseFieldHasProperValueFromData() {
    assertEquals(-1, myView.getFieldComponentIndex("Request"));
    myView.update(myHttpData);
    int responseFieldIndex = myView.getFieldComponentIndex("Request");
    assertNotEquals(-1, responseFieldIndex);
    JLabel value = (JLabel)myView.getFieldComponent(responseFieldIndex + 1);
    assertNotNull(value);
    assertEquals("dumbUrl", value.getText());
  }

  @Test
  public void contentTypeHasProperValueFromData() {
    assertEquals(-1, myView.getFieldComponentIndex("Content type"));
    when(myHttpData.getResponseField(eq(HttpData.FIELD_CONTENT_TYPE))).thenReturn("testContentTypeValue");
    myView.update(myHttpData);
    int contentTypeFieldIndex = myView.getFieldComponentIndex("Content type");
    assertNotEquals(-1, contentTypeFieldIndex);
    JLabel value = (JLabel)myView.getFieldComponent(contentTypeFieldIndex + 1);
    assertNotNull(value);
    assertEquals("testContentTypeValue", value.getText());
  }

  @Test
  public void contentTypeIsAbsentWhenDataHasNoContentTypeValue() {
    when(myHttpData.getResponseField(eq(HttpData.FIELD_CONTENT_TYPE))).thenReturn(null);
    myView.update(myHttpData);
    assertEquals(-1, myView.getFieldComponentIndex("Content type"));
  }

  @Test
  public void urlHasProperValueFromData() {
    assertEquals(-1, myView.getFieldComponentIndex("URL"));
    myView.update(myHttpData);
    int urlFieldIndex = myView.getFieldComponentIndex("URL");
    assertNotEquals(-1, urlFieldIndex);
    HyperlinkLabel value = (HyperlinkLabel)myView.getFieldComponent(urlFieldIndex + 1);
    assertNotNull(value);
    // Testing hack: HyperLink label doesn't expose its text directly, but does for accessibility
    // readers, so we use that instead.
    assertTrue(value.getAccessibleContext().getAccessibleName().contains("dumbUrl"));
  }

  @Test
  public void contentLengthHasProperValueFromData() {
    assertEquals(-1, myView.getFieldComponentIndex("Content length"));
    when(myHttpData.getResponseField(eq(HttpData.FIELD_CONTENT_LENGTH))).thenReturn("testContentLengthValue");
    myView.update(myHttpData);
    int contentLengthFieldIndex = myView.getFieldComponentIndex("Content length");
    assertNotEquals(-1, contentLengthFieldIndex);
    JLabel value = (JLabel)myView.getFieldComponent(contentLengthFieldIndex + 1);
    assertNotNull(value);
    assertEquals("testContentLengthValue", value.getText());
  }

  @Test
  public void contentLengthIsAbsentWhenDataHasNoContentLengthValue() {
    when(myHttpData.getResponseField(eq(HttpData.FIELD_CONTENT_LENGTH))).thenReturn(null);
    myView.update(myHttpData);
    assertEquals(-1, myView.getFieldComponentIndex("Content length"));
  }

  @Test
  public void headersIsUpdated() {
    when(myHttpData.getResponseHeaders()).thenReturn(TEST_HEADERS);
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    JPanel headers = (JPanel)stream.filter(c -> "Headers".equals(c.getName())).findFirst().get();
    assertEquals(0, headers.getComponentCount());
    myView.update(myHttpData);
    assertNotEquals(0, headers.getComponentCount());
  }

  @Test
  public void headerSectionIsSorted() {
    when(myHttpData.getResponseHeaders()).thenReturn(TEST_HEADERS);
    Stream<Component> stream = new TreeWalker(myView).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    JPanel headers = (JPanel)stream.filter(c -> "Headers".equals(c.getName())).findFirst().get();
    myView.update(myHttpData);
    stream = new TreeWalker(headers).descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST);
    JPanel responseHeaders = (JPanel)stream.filter(c -> "Response Headers".equals(c.getName())).findFirst().get();
    assertNotEquals("123", TEST_HEADERS.entrySet().iterator().next().getKey());
    assertEquals("123", responseHeaders.getComponent(1).getName());
    assertEquals("apple", responseHeaders.getComponent(2).getName());
    assertEquals("border", responseHeaders.getComponent(3).getName());
    assertEquals("car", responseHeaders.getComponent(4).getName());
  }

  @Test
  public void callstackViewHasProperValueFromData() {
    assertEquals(0, myView.getStackTraceView().getCodeLocations().size());

    myView.update(myHttpData);
    verify(myView.getStackTraceView()).clearStackFrames();
    verify(myView.getStackTraceView()).setStackFrames(myHttpData.getTrace());
  }
}
