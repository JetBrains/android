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
import java.util.regex.Matcher;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.property.editors.support.StyleEnumSupportTest.createFrameworkStyle;
import static com.android.tools.idea.uibuilder.property.editors.support.StyleEnumSupportTest.createStyle;
import static com.android.tools.idea.uibuilder.property.editors.support.TextAppearanceEnumSupport.TEXT_APPEARANCE_PATTERN;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(JUnit4.class)
public class TextAppearanceEnumSupportTest {
  private static final StyleResourceValue TEXT_APPEARANCE_STYLE = createFrameworkStyle("TextAppearance");
  private static final StyleResourceValue MATERIAL_STYLE = createFrameworkStyle("TextAppearance.Material");
  private static final StyleResourceValue MATERIAL_SMALL_STYLE = createFrameworkStyle("TextAppearance.Material.Small");
  private static final StyleResourceValue APPCOMPAT_STYLE = createStyle("TextAppearance.AppCompat", APPCOMPAT_LIB_ARTIFACT);
  private static final StyleResourceValue APPCOMPAT_SMALL_STYLE = createStyle("TextAppearance.AppCompat.Small", APPCOMPAT_LIB_ARTIFACT);
  private static final StyleResourceValue APPLICATION_STYLE = createStyle("TextAppearance.MyOwnStyle.Medium", null);

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

  private TextAppearanceEnumSupport mySupport;
  private Disposable myDisposable;

  @Before
  public void setUp() {
    initMocks(this);
    myDisposable = Disposer.newDisposable();
    ApplicationManager.setApplication(new MockApplication(myDisposable), myDisposable);
    when(myProperty.getResolver()).thenReturn(myResolver);
    when(myProperty.resolveValue(anyString())).thenAnswer(invocation -> invocation.getArguments()[0]);
    when(myProperty.resolveValue("?attr/textAppearanceSmall")).thenReturn("@android:style/TextAppearance.Material.Small");
    when(myProperty.getComponents()).thenReturn(Collections.singletonList(myComponent));
    when(myComponent.getTag()).thenReturn(myTag);
    when(myTag.knownNamespaces()).thenReturn(new String[]{ANDROID_URI, TOOLS_URI});
    when(myResolver.getStyle(any())).thenAnswer(invocation -> resolveStyle(invocation.getArgument(0)));
    when(myResourceRepositoryManager.getNamespace()).thenReturn(ResourceNamespace.TODO);

    mySupport = new TextAppearanceEnumSupport(myProperty, myStyleFilter, myResourceRepositoryManager);
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
        case "TextAppearance":
          return TEXT_APPEARANCE_STYLE;
        case "TextAppearance.Material":
          return MATERIAL_STYLE;
        case "TextAppearance.Material.Small":
          return MATERIAL_SMALL_STYLE;
        default:
          return null;
      }
    }
    else if (reference.getNamespace().equals(ResourceNamespace.RES_AUTO)) {
      switch (reference.getName()) {
        case "TextAppearance.AppCompat":
          return APPCOMPAT_STYLE;
        case "TextAppearance.MyOwnStyle.Medium":
          return APPLICATION_STYLE;
        default:
          return null;
      }
    }
    return null;
  }

  @Test
  public void testTextAppearancePattern() {
    checkTextAppearancePattern("TextAppearance", true, null);
    checkTextAppearancePattern("TextAppearance.Small", true, "Small");
    checkTextAppearancePattern("@android:style/TextAppearance.Material.Small", true, "Material.Small");
    checkTextAppearancePattern("@style/TextAppearance.AppCompat.Small", true, "AppCompat.Small");
    checkTextAppearancePattern("WhatEver", false, null);
  }

  private static void checkTextAppearancePattern(@NotNull String value, boolean expectedMatch, @Nullable String expectedMatchValue) {
    Matcher matcher = TEXT_APPEARANCE_PATTERN.matcher(value);
    assertThat(matcher.matches()).isEqualTo(expectedMatch);
    if (expectedMatch) {
      assertThat(matcher.group(5)).isEqualTo(expectedMatchValue);
    }
  }

  @Test
  public void testFindPossibleValues() {
    when(myStyleFilter.getStylesDerivedFrom(TEXT_APPEARANCE_STYLE)).thenReturn(ImmutableList.of(
      TEXT_APPEARANCE_STYLE, MATERIAL_STYLE, MATERIAL_SMALL_STYLE, APPCOMPAT_STYLE, APPCOMPAT_SMALL_STYLE, APPLICATION_STYLE));
    assertThat(mySupport.getAllValues()).containsExactly(
      new ValueWithDisplayString("TextAppearance", "@android:style/TextAppearance"),
      new ValueWithDisplayString("Material", "@android:style/TextAppearance.Material"),
      new ValueWithDisplayString("Material.Small", "@android:style/TextAppearance.Material.Small"),
      ValueWithDisplayString.SEPARATOR,
      new ValueWithDisplayString("AppCompat", "@style/TextAppearance.AppCompat"),
      new ValueWithDisplayString("AppCompat.Small", "@style/TextAppearance.AppCompat.Small"),
      ValueWithDisplayString.SEPARATOR,
      new ValueWithDisplayString("MyOwnStyle.Medium", "@style/TextAppearance.MyOwnStyle.Medium")).inOrder();
  }

  @Test
  public void testCreateDefaultValue() {
    assertThat(mySupport.createValue(""))
      .isEqualTo(ValueWithDisplayString.UNSET);
  }

  @Test
  public void testCreateDefaultValueResolvedToStyle() {
    when(myProperty.resolveValue(null)).thenReturn("@android:style/TextAppearance.Material.Small");
    assertThat(mySupport.createValue(""))
      .isEqualTo(new ValueWithDisplayString("Material.Small", null, "default"));
  }

  @Test
  public void testCreateFromCompleteFrameworkAttributeValue() {
    assertThat(mySupport.createValue("@android:style/TextAppearance.Material.Small"))
      .isEqualTo(new ValueWithDisplayString("Material.Small", "@android:style/TextAppearance.Material.Small"));
  }

  @Test
  public void testCreateFromCompleteAppcompatAttributeValue() {
    assertThat(mySupport.createValue("@style/TextAppearance.AppCompat"))
      .isEqualTo(new ValueWithDisplayString("AppCompat", "@style/TextAppearance.AppCompat"));
  }

  @Test
  public void testCreateFromCompleteUserDefinedAttributeValue() {
    assertThat(mySupport.createValue("@style/TextAppearance.MyOwnStyle.Medium"))
      .isEqualTo(new ValueWithDisplayString("MyOwnStyle.Medium", "@style/TextAppearance.MyOwnStyle.Medium"));
  }

  @Test
  public void testCreateFromIncompleteFrameworkAttributeValue() {
    assertThat(mySupport.createValue("TextAppearance.Material.Small"))
      .isEqualTo(new ValueWithDisplayString("Material.Small", "@android:style/TextAppearance.Material.Small"));
  }

  @Test
  public void testCreateFromIncompleteAppcompatAttributeValue() {
    assertThat(mySupport.createValue("TextAppearance.AppCompat"))
      .isEqualTo(new ValueWithDisplayString("AppCompat", "@style/TextAppearance.AppCompat"));
  }

  @Test
  public void testCreateFromIncompleteUserDefinedAttributeValue() {
    assertThat(mySupport.createValue("TextAppearance.MyOwnStyle.Medium"))
      .isEqualTo(new ValueWithDisplayString("MyOwnStyle.Medium", "@style/TextAppearance.MyOwnStyle.Medium"));
  }

  @Test
  public void testCreateFromIncompleteUnknownAttributeValue() {
    assertThat(mySupport.createValue("Unknown.Medium"))
      .isEqualTo(new ValueWithDisplayString("Unknown.Medium", "@style/Unknown.Medium"));
  }

  @Test
  public void testCreateFromMinimalFrameworkAttributeValue() {
    assertThat(mySupport.createValue("Material.Small"))
      .isEqualTo(new ValueWithDisplayString("Material.Small", "@android:style/TextAppearance.Material.Small"));
  }

  @Test
  public void testCreateFromMinimalAppcompatAttributeValue() {
    assertThat(mySupport.createValue("AppCompat"))
      .isEqualTo(new ValueWithDisplayString("AppCompat", "@style/TextAppearance.AppCompat"));
  }

  @Test
  public void testCreateFromMinimalUserDefinedAttributeValue() {
    assertThat(mySupport.createValue("MyOwnStyle.Medium"))
      .isEqualTo(new ValueWithDisplayString("MyOwnStyle.Medium", "@style/TextAppearance.MyOwnStyle.Medium"));
  }

  @Test
  public void testCreateFromThemeValue() {
    assertThat(mySupport.createValue("?attr/textAppearanceSmall"))
      .isEqualTo(new ValueWithDisplayString("Material.Small", "?attr/textAppearanceSmall", "?attr/textAppearanceSmall"));
  }
}
