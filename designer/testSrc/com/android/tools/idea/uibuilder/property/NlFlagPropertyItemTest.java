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
package com.android.tools.idea.uibuilder.property;

import com.android.util.PropertiesMap;
import com.intellij.util.ui.UIUtil;

import static com.android.SdkConstants.*;
import static com.google.common.truth.Truth.assertThat;

public class NlFlagPropertyItemTest extends PropertyTestCase {

  public void testGetGravityValue() {
    NlFlagPropertyItem gravity = (NlFlagPropertyItem)createFrom(myTextView, ATTR_GRAVITY);
    assertThat(gravity.getName()).isEqualTo(ATTR_GRAVITY);
    assertThat(gravity.getNamespace()).isEqualTo(ANDROID_URI);
    assertThat(gravity.getValue()).isNull();
    assertThat(gravity.getMaskValue()).isEqualTo(0);
    assertThat(gravity.getFormattedValue()).isEqualTo("[]");
  }

  public void testGetDesignGravityValue() {
    NlFlagPropertyItem gravity = (NlFlagPropertyItem)createFrom(myTextView, ATTR_GRAVITY);
    NlFlagPropertyItem design = gravity.getDesignTimeProperty();
    assertThat(design.getName()).isEqualTo(ATTR_GRAVITY);
    assertThat(design.getNamespace()).isEqualTo(TOOLS_URI);
    assertThat(design.getDesignTimeProperty()).isSameAs(design);
    assertThat(design.getValue()).isNull();
    assertThat(design.getMaskValue()).isEqualTo(0);
    assertThat(design.getFormattedValue()).isEqualTo("[]");
  }

  public void testGetGravityChildValue() {
    NlFlagPropertyItem gravity = (NlFlagPropertyItem)createFrom(myTextView, ATTR_GRAVITY);
    assertThat(gravity.hasChildren()).isTrue();
    assertThat(gravity.getChildren()).hasSize(14);
    NlFlagPropertyItemValue top = gravity.getChildProperty(GRAVITY_VALUE_TOP);
    assertThat(top).isNotNull();
    assertThat(top.getValue()).isEqualTo(VALUE_FALSE);
    assertThat(top.getMaskValue()).isFalse();
  }

  public void testGetNonExistingChildValue() {
    NlFlagPropertyItem gravity = (NlFlagPropertyItem)createFrom(myTextView, ATTR_GRAVITY);
    try {
      gravity.getChildProperty("NonExistingValue");
      fail("Should cause an exception");
    }
    catch (IllegalArgumentException ex) {
      assertThat(ex.getMessage()).isEqualTo("NonExistingValue");
    }
  }

  public void testExpandability() {
    NlFlagPropertyItem gravity = (NlFlagPropertyItem)createFrom(myTextView, ATTR_GRAVITY);
    assertThat(gravity.isExpanded()).isFalse();
    gravity.setExpanded(true);
    assertThat(gravity.isExpanded()).isTrue();
    gravity.setExpanded(false);
    assertThat(gravity.isExpanded()).isFalse();
  }

  public void testEdibility() {
    NlFlagPropertyItem gravity = (NlFlagPropertyItem)createFrom(myTextView, ATTR_GRAVITY);
    assertThat(gravity.isEditable(1)).isFalse();

    NlFlagPropertyItemValue top = gravity.getChildProperty(GRAVITY_VALUE_TOP);
    assertThat(top.isEditable(1)).isTrue();
  }

  public void testTextStyleNoneIsSet() {
    NlFlagPropertyItem textStyle = (NlFlagPropertyItem)createFrom(myTextView, ATTR_TEXT_STYLE);
    NlFlagPropertyItemValue normal = textStyle.getChildProperty(TextStyle.VALUE_NORMAL);

    assertThat(textStyle.getValue()).isNull();
    assertThat(normal.getValue()).isEqualTo(VALUE_FALSE);
    assertThat(normal.getMaskValue()).isTrue();
  }

  public void testTextStyleBoldIsSetByDefault() {
    NlFlagPropertyItem textStyle = (NlFlagPropertyItem)createFrom(myTextView, ATTR_TEXT_STYLE);
    textStyle.setDefaultValue(new PropertiesMap.Property(null, "bold"));
    NlFlagPropertyItemValue normal = textStyle.getChildProperty(TextStyle.VALUE_NORMAL);
    NlFlagPropertyItemValue bold = textStyle.getChildProperty(TextStyle.VALUE_BOLD);

    assertThat(textStyle.getValue()).isNull();
    assertThat(normal.getValue()).isEqualTo(VALUE_FALSE);
    assertThat(normal.getMaskValue()).isFalse();
    assertThat(bold.getValue()).isEqualTo(VALUE_FALSE);
    assertThat(bold.getMaskValue()).isTrue();
  }

  public void testTextStyleBoldIsSetExplicitlyAndByDefault() {
    NlFlagPropertyItem textStyle = (NlFlagPropertyItem)createFrom(myTextView, ATTR_TEXT_STYLE);
    textStyle.setDefaultValue(new PropertiesMap.Property(null, TextStyle.VALUE_BOLD));
    textStyle.setValue(TextStyle.VALUE_BOLD);
    UIUtil.dispatchAllInvocationEvents();

    NlFlagPropertyItemValue normal = textStyle.getChildProperty(TextStyle.VALUE_NORMAL);
    NlFlagPropertyItemValue bold = textStyle.getChildProperty(TextStyle.VALUE_BOLD);

    assertThat(textStyle.getValue()).isEqualTo(TextStyle.VALUE_BOLD);
    assertThat(normal.getValue()).isEqualTo(VALUE_FALSE);
    assertThat(normal.getMaskValue()).isFalse();
    assertThat(bold.getValue()).isEqualTo(VALUE_TRUE);
    assertThat(bold.getMaskValue()).isTrue();
  }

  public void testCenterImpliesMultipleFlags() {
    NlFlagPropertyItem gravity = (NlFlagPropertyItem)createFrom(myTextView, ATTR_GRAVITY);
    assertThat(gravity.getValue()).isNull();

    NlFlagPropertyItemValue center = gravity.getChildProperty(GRAVITY_VALUE_CENTER);
    NlFlagPropertyItemValue centerHorizontal = gravity.getChildProperty(GRAVITY_VALUE_CENTER_HORIZONTAL);
    NlFlagPropertyItemValue centerVertical = gravity.getChildProperty(GRAVITY_VALUE_CENTER_VERTICAL);
    center.setValue(VALUE_TRUE);
    UIUtil.dispatchAllInvocationEvents();

    assertThat(gravity.getValue()).isEqualTo(GRAVITY_VALUE_CENTER);
    assertThat(center.getValue()).isEqualTo(VALUE_TRUE);
    assertThat(center.getMaskValue()).isTrue();
    assertThat(centerHorizontal.getValue()).isEqualTo(VALUE_FALSE);
    assertThat(centerHorizontal.getMaskValue()).isTrue();
    assertThat(centerVertical.getValue()).isEqualTo(VALUE_FALSE);
    assertThat(centerVertical.getMaskValue()).isTrue();
  }
}
