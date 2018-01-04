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

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceUrl;
import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.adtui.ptable.PTableModel;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.android.tools.idea.uibuilder.property.PropertyTestCase;
import com.intellij.util.ui.ThreeStateCheckBox;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.SdkConstants.*;
import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NlBooleanRendererTest extends PropertyTestCase {
  private NlBooleanRenderer myRenderer;
  private ThreeStateCheckBox myCheckbox;
  private PTable myTable;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myRenderer = new NlBooleanRenderer();
    JPanel panel = myRenderer.getContentPanel();
    myCheckbox = (ThreeStateCheckBox)panel.getComponent(2);
    myTable = new PTable(new PTableModel());
  }

  public void testAutoTextNotSet() {
    NlPropertyItem property = getProperty(myButton, ATTR_AUTO_TEXT);
    myRenderer.customizeCellRenderer(myTable, property, false, false, 10, 1);
    assertThat(myCheckbox.getState()).isEqualTo(ThreeStateCheckBox.State.DONT_CARE);
  }

  public void testAutoTextOff() {
    NlPropertyItem property = getProperty(myButton, ATTR_AUTO_TEXT);
    property.setValue(VALUE_FALSE);
    UIUtil.dispatchAllInvocationEvents();

    myRenderer.customizeCellRenderer(myTable, property, false, false, 10, 1);
    assertThat(myCheckbox.getState()).isEqualTo(ThreeStateCheckBox.State.NOT_SELECTED);
  }

  public void testAutoTextOn() {
    NlPropertyItem property = getProperty(myButton, ATTR_AUTO_TEXT);
    property.setValue(VALUE_TRUE);
    UIUtil.dispatchAllInvocationEvents();

    myRenderer.customizeCellRenderer(myTable, property, false, false, 10, 1);
    assertThat(myCheckbox.getState()).isEqualTo(ThreeStateCheckBox.State.SELECTED);
  }

  public void testAutoTextNotSetFromResource() {
    NlPropertyItem property = createMockProperty(ATTR_AUTO_TEXT, "@string/whatever", null);

    myRenderer.customizeCellRenderer(myTable, property, false, false, 10, 1);
    assertThat(myCheckbox.getState()).isEqualTo(ThreeStateCheckBox.State.DONT_CARE);
  }

  public void testAutoTextOnFromResource() {
    NlPropertyItem property = createMockProperty(ATTR_AUTO_TEXT, "@string/another", "true");

    myRenderer.customizeCellRenderer(myTable, property, false, false, 10, 1);
    assertThat(myCheckbox.getState()).isEqualTo(ThreeStateCheckBox.State.SELECTED);
  }

  public void testAutoTextOffFromResource() {
    NlPropertyItem property = createMockProperty(ATTR_AUTO_TEXT, "@string/whatever", "false");

    myRenderer.customizeCellRenderer(myTable, property, false, false, 10, 1);
    assertThat(myCheckbox.getState()).isEqualTo(ThreeStateCheckBox.State.NOT_SELECTED);
  }

  public void testAutoTextFromNonExistingResource() {
    ResourceResolver resolver = mock(ResourceResolver.class);

    NlPropertyItem property = mock(NlPropertyItem.class);
    when(property.getName()).thenReturn(ATTR_AUTO_TEXT);
    when(property.getValue()).thenReturn("@string/whatever");
    when(property.getResolver()).thenReturn(resolver);

    myRenderer.customizeCellRenderer(myTable, property, false, false, 10, 1);
    assertThat(myCheckbox.getState()).isEqualTo(ThreeStateCheckBox.State.DONT_CARE);
  }

  public void testWithNonProperty() {
    PTableItem item = mock(PTableItem.class);
    myRenderer.customizeCellRenderer(myTable, item, false, false, 10, 1);
    assertThat(myCheckbox.getState()).isEqualTo(ThreeStateCheckBox.State.DONT_CARE);
  }

  private static NlPropertyItem createMockProperty(@NotNull String propertyName, @Nullable String url, @Nullable String resolvedValue) {
    ResourceResolver resolver = ResourceResolver.withValues(
      new ResourceValue(ResourceUrl.parse(url).resolve(RES_AUTO, ResourceNamespace.EMPTY_NAMESPACE_CONTEXT), resolvedValue));

    NlPropertyItem property = mock(NlPropertyItem.class);
    when(property.getName()).thenReturn(propertyName);
    when(property.getValue()).thenReturn(url);
    when(property.getResolver()).thenReturn(resolver);

    return property;
  }
}
