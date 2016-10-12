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

import com.google.common.collect.ImmutableSet;

import static com.android.SdkConstants.*;
import static com.google.common.truth.Truth.assertThat;

public class NlFlagPropertyItemTest extends PropertyTestCase {

  public void testGetters() {
    NlFlagPropertyItem gravity = (NlFlagPropertyItem)createFrom(myTextView, ATTR_GRAVITY);
    NlFlagPropertyItem design = gravity.getDesignTimeProperty();
    assertThat(design.getName()).isEqualTo(ATTR_GRAVITY);
    assertThat(design.getNamespace()).isEqualTo(TOOLS_URI);
    assertThat(design.getDesignTimeProperty()).isSameAs(design);

    assertThat(gravity.hasChildren()).isTrue();
    assertThat(gravity.getChildren()).hasSize(14);
    assertThat(gravity.getChildProperty(GRAVITY_VALUE_TOP)).isNotNull();
    try {
      gravity.getChildProperty("NonExistingValue");
      fail("Should case an exception");
    }
    catch (IllegalArgumentException ex) {
      assertThat(ex.getMessage()).isEqualTo("NonExistingValue");
    }

    assertThat(gravity.isExpanded()).isFalse();
    gravity.setExpanded(true);
    assertThat(gravity.isExpanded()).isTrue();
    gravity.setExpanded(false);
    assertThat(gravity.isExpanded()).isFalse();
    assertThat(gravity.isEditable(1)).isFalse();
    assertThat(gravity.getValue()).isNull();
    assertThat(gravity.getFormattedValue()).isEqualTo("[]");
  }

  public void testMutators() {
    NlFlagPropertyItem gravity = (NlFlagPropertyItem)createFrom(myTextView, ATTR_GRAVITY);
    gravity.setValue("bottom|fill_horizontal");
    assertThat(gravity.getValue()).isEqualTo("bottom|fill_horizontal");
    assertThat(gravity.getFormattedValue()).isEqualTo("[bottom, fill_horizontal]");
    assertThat(gravity.isItemSet(GRAVITY_VALUE_TOP)).isFalse();
    assertThat(gravity.isItemSet(GRAVITY_VALUE_BOTTOM)).isTrue();
    assertThat(gravity.isAnyItemSet(GRAVITY_VALUE_TOP, GRAVITY_VALUE_CENTER)).isFalse();
    assertThat(gravity.isAnyItemSet(GRAVITY_VALUE_TOP, GRAVITY_VALUE_BOTTOM, GRAVITY_VALUE_CENTER)).isTrue();

    NlFlagPropertyItemValue top = gravity.getChildProperty(GRAVITY_VALUE_TOP);
    NlFlagPropertyItemValue bottom = gravity.getChildProperty(GRAVITY_VALUE_BOTTOM);
    assertThat(gravity.isItemSet(top)).isFalse();
    assertThat(gravity.isItemSet(bottom)).isTrue();

    gravity.setItem(top, true);
    assertThat(gravity.getValue()).isEqualTo("top|bottom|fill_horizontal");
    gravity.setItem(top, false);
    gravity.setItem(bottom, false);
    assertThat(gravity.getValue()).isEqualTo("fill_horizontal");

    gravity.updateItems(
      ImmutableSet.of(GRAVITY_VALUE_CENTER, GRAVITY_VALUE_START),
      ImmutableSet.of(GRAVITY_VALUE_BOTTOM, GRAVITY_VALUE_START));
    assertThat(gravity.getValue()).isEqualTo("fill_horizontal|center|start");
  }
}
