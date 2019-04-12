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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT_ID;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.CHECK_BOX;
import static com.android.SdkConstants.CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;
import static com.android.tools.idea.uibuilder.property.editors.support.TextAppearanceEnumSupport.TEXT_APPEARANCE_PATTERN;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.fixtures.ComponentDescriptor;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.Dependencies;
import com.android.tools.idea.uibuilder.NlModelBuilderUtil;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.google.common.collect.ImmutableList;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.util.xml.XmlName;
import java.util.List;
import java.util.regex.Matcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TextAppearanceEnumSupportTest {

  @Rule
  public final AndroidProjectRule myProjectRule = AndroidProjectRule.withSdk().initAndroid(true);

  @Rule
  public final EdtRule myEdtRule = new EdtRule();

  private SyncNlModel myModel;
  private NlComponent myLayout;

  @Before
  public void setUp() {
    addProjectStyles();
    myModel = createModel().build();
    myLayout = myModel.getComponents().get(0);
  }

  @After
  public void tearDown() {
    myModel = null;
    myLayout = null;
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

  @RunsInEdt
  @Test
  public void testFindPossibleValues() {
    Dependencies.INSTANCE.add(myProjectRule.fixture, APPCOMPAT_LIB_ARTIFACT_ID);
    NlComponent textView = myLayout.getChild(0);
    NlPropertyItem property = NlPropertyItem.create(new XmlName(ATTR_STYLE, ANDROID_URI), null, ImmutableList.of(textView), null);
    TextAppearanceEnumSupport support = new TextAppearanceEnumSupport(property);
    List<ValueWithDisplayString> values = support.getAllValues();
    assertThat(values.subList(0, 7)).containsExactly(
      new ValueWithDisplayString("MyOwnStyle", "@style/MyOwnStyle"),
      new ValueWithDisplayString("MyOwnStyle.Medium", "@style/MyOwnStyle.Medium"),
      new ValueWithDisplayString("TextAppearance", "@style/TextAppearance"),
      ValueWithDisplayString.SEPARATOR,
      new ValueWithDisplayString("AppCompat", "@style/TextAppearance.AppCompat"),
      new ValueWithDisplayString("AppCompat.Body1", "@style/TextAppearance.AppCompat.Body1"),
      new ValueWithDisplayString("AppCompat.Body2", "@style/TextAppearance.AppCompat.Body2")).inOrder();

    assertThat(support.getAllValues()).containsAllOf(
      new ValueWithDisplayString("TextAppearance", "@android:style/TextAppearance"),
      new ValueWithDisplayString("Material", "@android:style/TextAppearance.Material"),
      new ValueWithDisplayString("Material.Small", "@android:style/TextAppearance.Material.Small"),
      new ValueWithDisplayString("AppCompat", "@style/TextAppearance.AppCompat"),
      new ValueWithDisplayString("AppCompat.Display1", "@style/TextAppearance.AppCompat.Display1"),
      new ValueWithDisplayString("AppCompat.Display2", "@style/TextAppearance.AppCompat.Display2"),
      new ValueWithDisplayString("AppCompat.Display3", "@style/TextAppearance.AppCompat.Display3"),
      new ValueWithDisplayString("AppCompat.Display4", "@style/TextAppearance.AppCompat.Display4"),
      new ValueWithDisplayString("Widget.TextView", "@android:style/TextAppearance.Widget.TextView"));
  }

  private void addProjectStyles() {
    String styles =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      "<resources xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
      "    <style name=\"MyOwnStyle\" parent=\"android:TextAppearance\"/>\n" +
      "    <style name=\"MyOwnStyle.Medium\"/>\n" +
      "    <style name=\"TextAppearance\" parent=\"android:TextAppearance\"/>\n" +
      "</resources>\n";
    myProjectRule.fixture.addFileToProject("res/values/styles.xml", styles);
  }

  private ModelBuilder createModel() {
    return NlModelBuilderUtil.model(
      myProjectRule,
      "layout",
      "constraint.xml",
      new ComponentDescriptor(CONSTRAINT_LAYOUT.defaultName())
        .id("@id/root")
        .withBounds(0, 0, 2000, 2000)
        .width("1000dp")
        .height("1000dp")
        .withAttribute("android:padding", "20dp")
        .children(
          new ComponentDescriptor(CHECK_BOX)
            .id("@id/button")
            .withBounds(100, 100, 200, 40)
            .width("100dp")
            .height("20dp"),
          new ComponentDescriptor(TEXT_VIEW)
            .id("@id/button")
            .withBounds(200, 100, 200, 40)
            .width("100dp")
            .height("20dp")
        ));
  }
}
