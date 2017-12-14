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
package com.android.tools.idea.uibuilder.property.editors.support;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.common.property.NlProperty;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

import static com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT;
import static com.android.SdkConstants.CHECK_BOX;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(JUnit4.class)
public class StyleEnumSupportTest {
  private static final StyleResourceValue CHECKBOX_STYLE = createFrameworkStyle("Widget.CompoundButton.CheckBox");
  private static final StyleResourceValue MATERIAL_STYLE = createFrameworkStyle("Widget.Material.CompoundButton.CheckBox");
  private static final StyleResourceValue APPCOMPAT_STYLE = createStyle("Widget.AppCompat.CompoundButton.CheckBox", APPCOMPAT_LIB_ARTIFACT);
  private static final StyleResourceValue APPLICATION_STYLE = createStyle("MyCheckBox", null);

  @Mock
  private NlProperty myProperty;
  @Mock
  private ResourceResolver myResolver;
  @Mock
  private StyleFilter myStyleFilter;

  private StyleEnumSupport mySupport;

  @Before
  public void setUp() {
    initMocks(this);
    when(myProperty.getResolver()).thenReturn(myResolver);
    when(myProperty.resolveValue(anyString())).thenAnswer(invocation -> invocation.getArguments()[0]);
    when(myResolver.getStyle("Widget.CompoundButton.CheckBox", true)).thenReturn(CHECKBOX_STYLE);
    when(myResolver.getStyle("Widget.Material.CompoundButton.CheckBox", true)).thenReturn(MATERIAL_STYLE);
    when(myResolver.getStyle("Widget.AppCompat.CompoundButton.CheckBox", false)).thenReturn(APPCOMPAT_STYLE);
    when(myResolver.getStyle("MyCheckBox", false)).thenReturn(APPLICATION_STYLE);

    mySupport = new StyleEnumSupport(myProperty, myStyleFilter);
  }

  @Test
  public void testFindPossibleValues() {
    when(myProperty.getTagName()).thenReturn(CHECK_BOX);
    when(myStyleFilter.getWidgetStyles(CHECK_BOX)).thenReturn(ImmutableList.of(
      CHECKBOX_STYLE, MATERIAL_STYLE, APPCOMPAT_STYLE, APPLICATION_STYLE));
    assertThat(mySupport.getAllValues()).containsExactly(
      new ValueWithDisplayString("Widget.CompoundButton.CheckBox", "@android:style/Widget.CompoundButton.CheckBox"),
      new ValueWithDisplayString("Widget.Material.CompoundButton.CheckBox", "@android:style/Widget.Material.CompoundButton.CheckBox"),
      ValueWithDisplayString.SEPARATOR,
      new ValueWithDisplayString("Widget.AppCompat.CompoundButton.CheckBox", "@style/Widget.AppCompat.CompoundButton.CheckBox"),
      ValueWithDisplayString.SEPARATOR,
      new ValueWithDisplayString("MyCheckBox", "@style/MyCheckBox")).inOrder();
  }

  @Test
  public void testCreateDefaultValue() {
    assertThat(mySupport.createValue(""))
      .isEqualTo(ValueWithDisplayString.UNSET);
  }

  @Test
  public void testCreateFromCompleteFrameworkAttributeValue() {
    assertThat(mySupport.createValue("@android:style/Widget.CompoundButton.CheckBox"))
      .isEqualTo(new ValueWithDisplayString("Widget.CompoundButton.CheckBox", "@android:style/Widget.CompoundButton.CheckBox"));
  }

  @Test
  public void testCreateFromCompleteAppcompatAttributeValue() {
    assertThat(mySupport.createValue("@style/Widget.AppCompat.CompoundButton.CheckBox"))
      .isEqualTo(new ValueWithDisplayString("Widget.AppCompat.CompoundButton.CheckBox", "@style/Widget.AppCompat.CompoundButton.CheckBox"));
  }

  @Test
  public void testCreateFromCompleteUserDefinedAttributeValue() {
    assertThat(mySupport.createValue("@style/MyCheckBox"))
      .isEqualTo(new ValueWithDisplayString("MyCheckBox", "@style/MyCheckBox"));
  }

  @Test
  public void testCreateFromIncompleteFrameworkAttributeValue() {
    assertThat(mySupport.createValue("Widget.CompoundButton.CheckBox"))
      .isEqualTo(new ValueWithDisplayString("Widget.CompoundButton.CheckBox", "@android:style/Widget.CompoundButton.CheckBox"));
  }

  @Test
  public void testCreateFromIncompleteAppcompatAttributeValue() {
    assertThat(mySupport.createValue("Widget.AppCompat.CompoundButton.CheckBox"))
      .isEqualTo(new ValueWithDisplayString("Widget.AppCompat.CompoundButton.CheckBox", "@style/Widget.AppCompat.CompoundButton.CheckBox"));
  }

  @Test
  public void testCreateFromIncompleteUserDefinedAttributeValue() {
    assertThat(mySupport.createValue("MyCheckBox"))
      .isEqualTo(new ValueWithDisplayString("MyCheckBox", "@style/MyCheckBox"));
  }

  @Test
  public void testCreateFromIncompleteUnknownAttributeValue() {
    assertThat(mySupport.createValue("Unknown.Medium"))
      .isEqualTo(new ValueWithDisplayString("Unknown.Medium", "@style/Unknown.Medium"));
  }

  static StyleResourceValue createFrameworkStyle(@NotNull String name) {
    return new StyleResourceValue(new ResourceReference(ResourceNamespace.ANDROID, ResourceType.STYLE, name), null, null);
  }

  static StyleResourceValue createStyle(@NotNull String name, @Nullable String libraryName) {
    return new StyleResourceValue(new ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STYLE, name), null, libraryName);
  }
}
