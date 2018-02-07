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
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.common.collect.ImmutableList;
import com.intellij.mock.MockApplication;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

import java.util.Collections;

import static com.android.SdkConstants.*;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
  private NlComponent myComponent;
  @Mock
  private XmlTag myTag;
  @Mock
  private ResourceResolver myResolver;
  @Mock
  private StyleFilter myStyleFilter;
  @Mock
  private ResourceRepositoryManager myResourceRepositoryManager;

  private StyleEnumSupport mySupport;
  private Disposable myDisposable;

  @Before
  public void setUp() {
    initMocks(this);
    myDisposable = Disposer.newDisposable();
    ApplicationManager.setApplication(new MockApplication(myDisposable), myDisposable);
    when(myProperty.getResolver()).thenReturn(myResolver);
    when(myProperty.resolveValue(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    when(myProperty.getComponents()).thenReturn(Collections.singletonList(myComponent));
    when(myComponent.getTag()).thenReturn(myTag);
    when(myTag.knownNamespaces()).thenReturn(new String[]{ANDROID_URI, TOOLS_URI});
    when(myResolver.getStyle(any())).thenAnswer(invocation -> resolveStyle(invocation.getArgument(0)));
    when(myResourceRepositoryManager.getNamespace()).thenReturn(ResourceNamespace.TODO);
    mySupport = new StyleEnumSupport(myProperty, myStyleFilter, myResourceRepositoryManager);
  }

  @After
  public void tearDown() {
    Disposer.dispose(myDisposable);
    myProperty = null;
    myComponent = null;
    myTag = null;
    myResolver = null;
    myStyleFilter = null;
    myResourceRepositoryManager = null;
    mySupport = null;
    myDisposable = null;
  }

  private static StyleResourceValue resolveStyle(@NotNull ResourceReference reference) {
    if (reference.getNamespace().equals(ResourceNamespace.ANDROID)) {
      switch (reference.getName()) {
        case "Widget.CompoundButton.CheckBox":
          return CHECKBOX_STYLE;
        case "Widget.Material.CompoundButton.CheckBox":
          return MATERIAL_STYLE;
        default:
          return null;
      }
    }
    else if (reference.getNamespace().equals(ResourceNamespace.RES_AUTO)) {
      switch (reference.getName()) {
        case "Widget.AppCompat.CompoundButton.CheckBox":
          return APPCOMPAT_STYLE;
        case "MyCheckBox":
          return APPLICATION_STYLE;
        default:
          return null;
      }
    }
    return null;
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
