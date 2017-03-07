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
package com.android.tools.idea.uibuilder.property.renderer;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.PropertyTestCase;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.ThreeStateCheckBox;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.SdkConstants.*;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NlBooleanRendererTest extends PropertyTestCase {
  private NlBooleanRenderer myRenderer;
  private ThreeStateCheckBox myCheckbox;
  private SimpleColoredComponent myLabel;
  private JTable myTable;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myRenderer = new NlBooleanRenderer();
    JPanel panel = myRenderer.getContentPanel();
    myCheckbox = (ThreeStateCheckBox)panel.getComponent(1);
    myLabel = (SimpleColoredComponent)panel.getComponent(2);
    myTable = new JBTable();
  }

  public void testAutoTextNotSet() {
    NlProperty property = getProperty(myButton, ATTR_AUTO_TEXT);
    myRenderer.customizeRenderContent(myTable, property, false, false, 10, 1);
    assertThat(myCheckbox.getState()).isEqualTo(ThreeStateCheckBox.State.DONT_CARE);
  }

  public void testAutoTextOff() {
    NlProperty property = getProperty(myButton, ATTR_AUTO_TEXT);
    property.setValue(VALUE_FALSE);
    UIUtil.dispatchAllInvocationEvents();

    myRenderer.customizeRenderContent(myTable, property, false, false, 10, 1);
    assertThat(myCheckbox.getState()).isEqualTo(ThreeStateCheckBox.State.NOT_SELECTED);
  }

  public void testAutoTextOn() {
    NlProperty property = getProperty(myButton, ATTR_AUTO_TEXT);
    property.setValue(VALUE_TRUE);
    UIUtil.dispatchAllInvocationEvents();

    myRenderer.customizeRenderContent(myTable, property, false, false, 10, 1);
    assertThat(myCheckbox.getState()).isEqualTo(ThreeStateCheckBox.State.SELECTED);
  }

  public void testAutoTextNotSetFromResource() {
    NlProperty property = createMockProperty(ATTR_AUTO_TEXT, "@string/whatever", null);

    myRenderer.customizeRenderContent(myTable, property, false, false, 10, 1);
    assertThat(myCheckbox.getState()).isEqualTo(ThreeStateCheckBox.State.DONT_CARE);
  }

  public void testAutoTextOnFromResource() {
    NlProperty property = createMockProperty(ATTR_AUTO_TEXT, "@string/another", "true");

    myRenderer.customizeRenderContent(myTable, property, false, false, 10, 1);
    assertThat(myCheckbox.getState()).isEqualTo(ThreeStateCheckBox.State.SELECTED);
  }

  public void testAutoTextOffFromResource() {
    NlProperty property = createMockProperty(ATTR_AUTO_TEXT, "@string/whatever", "false");

    myRenderer.customizeRenderContent(myTable, property, false, false, 10, 1);
    assertThat(myCheckbox.getState()).isEqualTo(ThreeStateCheckBox.State.NOT_SELECTED);
  }

  public void testAutoTextFromNonExistingResource() {
    ResourceResolver resolver = mock(ResourceResolver.class);

    NlProperty property = mock(NlProperty.class);
    when(property.getName()).thenReturn(ATTR_AUTO_TEXT);
    when(property.getValue()).thenReturn("@string/whatever");
    when(property.getResolver()).thenReturn(resolver);

    myRenderer.customizeRenderContent(myTable, property, false, false, 10, 1);
    assertThat(myCheckbox.getState()).isEqualTo(ThreeStateCheckBox.State.DONT_CARE);
  }

  private static NlProperty createMockProperty(@NotNull String propertyName, @Nullable String value, @Nullable String resolvedValue) {
    ResourceValue resource = mock(ResourceValue.class);
    when(resource.getValue()).thenReturn(resolvedValue);

    ResourceResolver resolver = mock(ResourceResolver.class);
    when(resolver.findResValue(eq(value), anyBoolean())).thenReturn(resource);

    NlProperty property = mock(NlProperty.class);
    when(property.getName()).thenReturn(propertyName);
    when(property.getValue()).thenReturn(value);
    when(property.getResolver()).thenReturn(resolver);

    return property;
  }
}
