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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.tools.idea.uibuilder.property.NlProperty;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import static com.android.SdkConstants.*;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.anyString;

@RunWith(JUnit4.class)
public class ValueWithDisplayStringTest {

  @Test
  public void testCreateFromArray() {
    ValueWithDisplayString[] values = ValueWithDisplayString.create(new String[]{"bread", "cookies"});
    assertThat(values[0]).isEqualTo(new ValueWithDisplayString("bread", "bread"));
    assertThat(values[1]).isEqualTo(new ValueWithDisplayString("cookies", "cookies"));
  }

  @Test
  public void testCreateFromList() {
    ValueWithDisplayString[] values = ValueWithDisplayString.create(Lists.newArrayList("bread", "cookies"));
    assertThat(values[0]).isEqualTo(new ValueWithDisplayString("bread", "bread"));
    assertThat(values[1]).isEqualTo(new ValueWithDisplayString("cookies", "cookies"));
  }

  @Test
  public void testGetters() {
    ValueWithDisplayString value = new ValueWithDisplayString("bread", "cookies");
    assertThat(value.getDisplayString()).isEqualTo("bread");
    assertThat(value.getValue()).isEqualTo("cookies");
    assertThat(value.toString()).isEqualTo("bread");

    value.setUseValueForToString(true);
    assertThat(value.getDisplayString()).isEqualTo("bread");
    assertThat(value.getValue()).isEqualTo("cookies");
    assertThat(value.toString()).isEqualTo("cookies");
  }

  @Test
  public void testCreateFromPropertyValue() {
    assertThat(ValueWithDisplayString.create("100dp", mockProperty(ATTR_LAYOUT_WIDTH)))
      .isEqualTo(new ValueWithDisplayString("100dp", "100dp"));

    assertThat(ValueWithDisplayString.create("MyText", mockProperty(ATTR_TEXT)))
      .isEqualTo(new ValueWithDisplayString("MyText", "MyText"));

    assertThat(ValueWithDisplayString.create(null, mockProperty(ATTR_TEXT)))
      .isEqualTo(new ValueWithDisplayString("none", null));

    assertThat(ValueWithDisplayString.create("?attr/textAppearanceSmall", mockProperty(ATTR_TEXT_APPEARANCE)))
      .isEqualTo(new ValueWithDisplayString("Material.Small", "?attr/textAppearanceSmall"));

    assertThat(ValueWithDisplayString.create("@android:style/TextAppearance.Material.Small", mockProperty(ATTR_ITEM_TEXT_APPEARANCE)))
      .isEqualTo(new ValueWithDisplayString("Material.Small", "@android:style/TextAppearance.Material.Small"));

    assertThat(ValueWithDisplayString.create("@style/TextAppearance.MyOwnStyle.Medium", mockProperty(ATTR_TEXT_APPEARANCE)))
      .isEqualTo(new ValueWithDisplayString("MyOwnStyle.Medium", "@style/TextAppearance.MyOwnStyle.Medium"));

    assertThat(ValueWithDisplayString.create("@string/font_family_body_1_material", mockProperty(ATTR_FONT_FAMILY)))
      .isEqualTo(new ValueWithDisplayString("sans-serif", "@string/font_family_body_1_material"));
  }

  @Test
  public void testCreateFromStyleValue() {
    assertThat(ValueWithDisplayString.createStyleValue("TextAppearance.AppCompat", "@style/TextAppearance.AppCompat"))
      .isEqualTo(new ValueWithDisplayString("AppCompat", "@style/TextAppearance.AppCompat"));

    assertThat(ValueWithDisplayString.createStyleValue("@style/TextAppearance.AppCompat", null))
      .isEqualTo(new ValueWithDisplayString("AppCompat", null));
  }

  @Test
  public void testEquals() {
    assertThat(new ValueWithDisplayString("display", "value")).isEqualTo(new ValueWithDisplayString("display", "value"));
    assertThat(new ValueWithDisplayString("display", "value")).isNotEqualTo(new ValueWithDisplayString("different", "value"));
    assertThat(new ValueWithDisplayString("display", "value")).isNotEqualTo(new ValueWithDisplayString("display", "other"));
    assertThat(new ValueWithDisplayString("display", "value")).isNotEqualTo(new Object());
    assertThat(new ValueWithDisplayString("display", "value")).isNotEqualTo(null);
  }

  private static NlProperty mockProperty(@NotNull String attribute) {
    NlProperty property = Mockito.mock(NlProperty.class);
    Mockito.when(property.getName()).thenReturn(attribute);
    Mockito.when(property.resolveValue(anyString())).thenAnswer(invocation -> {
      String value = (String)invocation.getArguments()[0];
      if (value == null) {
        return null;
      }
      switch (value) {
        case "?attr/textAppearanceSmall":
          return "@android:style/TextAppearance.Material.Small";
        case "@string/font_family_body_1_material":
          return "sans-serif";
        default:
          return value;
      }
    });
    return property;
  }
}
