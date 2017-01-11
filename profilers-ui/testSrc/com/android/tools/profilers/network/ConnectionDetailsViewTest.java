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

import com.android.tools.profilers.*;
import com.android.tools.profilers.common.CodeLocation;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.labels.LinkLabel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

public class ConnectionDetailsViewTest {

  @Mock private IdeProfilerComponents myIdeProfilerComponents;
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

    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), myIdeServices);
    NetworkProfilerStage stage = new NetworkProfilerStage(profilers);
    StudioProfilersView view = new StudioProfilersView(profilers, myIdeProfilerComponents);
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
    when(myIdeProfilerComponents.getFileViewer(any())).thenReturn(new JLabel());
    myView.update(myHttpData);
    assertNotNull(myView.getFileViewer());
    assertNotNull(myView.getFieldComponent(0));
    assertEquals(1, myView.getStackView().getComponentCount());

    myView.update((HttpData)null);
    assertNull(myView.getFileViewer());
    assertNull(myView.getFieldComponent(0));
    assertEquals(0, myView.getStackView().getComponentCount());
  }

  @Test
  public void editorComponentIsAddedWhenComponentsProviderReturnsNonNull() {
    JLabel fileViewer = new JLabel("fileViewer");
    when(myIdeProfilerComponents.getFileViewer(any())).thenReturn(fileViewer);
    myView.update(myHttpData);
    assertEquals(fileViewer, myView.getFileViewer());
  }

  @Test
  public void editorComponentIsAbsentWhenComponentsProviderReturnsNull() {
    when(myIdeProfilerComponents.getFileViewer(any())).thenReturn(null);
    myView.update(myHttpData);
    assertNull(myView.getFileViewer());
  }

  @Test
  public void responseFieldHasProperValueFromData() {
    assertEquals(-1, myView.getFieldComponentIndex("Request"));
    myView.update(myHttpData);
    int responseFieldIndex = myView.getFieldComponentIndex("Request");
    assertNotEquals(-1, responseFieldIndex);
    JLabel value = (JLabel)myView.getFieldComponent(responseFieldIndex + 1);
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
    assertEquals("testContentLengthValue", value.getText());
  }

  @Test
  public void contentLengthIsAbsentWhenDataHasNoContentLengthValue() {
    when(myHttpData.getResponseField(eq(HttpData.FIELD_CONTENT_LENGTH))).thenReturn(null);
    myView.update(myHttpData);
    assertEquals(-1, myView.getFieldComponentIndex("Content length"));
  }

  @Test
  public void callstackViewHasProperValueFromData() {
    assertEquals(0, myView.getStackView().getComponentCount());

    myView.update(myHttpData);
    assertEquals(1, myView.getStackView().getComponentCount());

    assertEquals("dumbTrace", ((JLabel)myView.getStackView().getComponent(0)).getText());
  }

  @Test
  public void callStackLineClick() {
    List<String> classNames = Arrays.asList("java.lang.Integer.intValue()", "com.foo.Bar.method()", "int");
    when(myHttpData.getTrace()).thenReturn(String.join("\n", classNames));

    final String[] lastLine = new String[1];

    doAnswer(invocation -> {
      lastLine[0] = ((CodeLocation)invocation.getArguments()[0]).getClassName();
      return true;
    }).when(myIdeServices).navigateToStackFrame(any());

    myView.update(myHttpData);
    assertEquals(3, myView.getStackView().getComponentCount());

    LinkLabel link0 = (LinkLabel)myView.getStackView().getComponent(0);
    LinkLabel link1 = (LinkLabel)myView.getStackView().getComponent(1);
    assert myView.getStackView().getComponent(2) instanceof JLabel;

    lastLine[0] = null;
    link0.doClick();
    assertEquals(classNames.get(0).substring(0, classNames.get(0).lastIndexOf('.')), lastLine[0]);

    lastLine[0] = null;
    link1.doClick();
    assertEquals(classNames.get(1).substring(0, classNames.get(1).lastIndexOf('.')), lastLine[0]);
  }
}
