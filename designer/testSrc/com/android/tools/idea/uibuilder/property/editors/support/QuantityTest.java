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
import com.android.tools.idea.common.property.NlProperty;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import com.android.ide.common.rendering.api.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.util.Collections;

import static com.android.SdkConstants.*;
import static com.google.common.truth.Truth.assertThat;
import static com.android.ide.common.rendering.api.AttributeFormat.*;
import static com.android.ide.common.rendering.api.AttributeFormat.INTEGER;
import static com.android.ide.common.rendering.api.AttributeFormat.STRING;

@RunWith(JUnit4.class)
public class QuantityTest {

  @Test
  public void testParse() {
    assertQuantity(Quantity.parse("25"), 25, "");
    assertQuantity(Quantity.parse("12dp"), 12, "dp");
    assertQuantity(Quantity.parse("20sp"), 20, "sp");
    assertQuantity(Quantity.parse("-15 dp"), -15, "dp");
    assertThat(Quantity.parse("9999999999999999dp")).isNull();
    assertThat(Quantity.parse("rio")).isNull();
  }

  @Test
  public void testAddUnit() {
    assertThat(Quantity.addUnit(mockProperty(ATTR_TEXT_SIZE, DIMENSION), "13")).isEqualTo("13sp");
    assertThat(Quantity.addUnit(mockProperty(ATTR_LINE_SPACING_EXTRA, DIMENSION), "10")).isEqualTo("10sp");
    assertThat(Quantity.addUnit(mockProperty(ATTR_LAYOUT_WIDTH, DIMENSION), "53")).isEqualTo("53dp");
    assertThat(Quantity.addUnit(mockProperty(ATTR_LAYOUT_WIDTH, DIMENSION), "53px")).isEqualTo("53px");
    assertThat(Quantity.addUnit(mockProperty(ATTR_LAYOUT_WIDTH, DIMENSION), "wrap_content")).isEqualTo("wrap_content");
    assertThat(Quantity.addUnit(mockProperty(ATTR_TEXT, STRING), "13")).isEqualTo("13");
    assertThat(Quantity.addUnit(mockProperty(ATTR_MIN_SDK_VERSION, INTEGER), "22")).isEqualTo("22");
  }

  @SuppressWarnings("ConstantConditions")  // Possible NullPointerException from Quantity.parse
  @Test
  public void testCompare() {
    // Null
    assertThat(Quantity.parse("12dp").compareTo(null)).isLessThan(0);

    // Group by unit
    assertThat(Quantity.parse("-12dp").compareTo(Quantity.parse("12sp"))).isLessThan(0);
    assertThat(Quantity.parse("12dp").compareTo(Quantity.parse("12px"))).isLessThan(0);
    assertThat(Quantity.parse("12sp").compareTo(Quantity.parse("12px"))).isGreaterThan(0);

    // Then order by number
    assertThat(Quantity.parse("12dp").compareTo(Quantity.parse("-1dp"))).isGreaterThan(0);
    assertThat(Quantity.parse("12dp").compareTo(Quantity.parse("13dp"))).isLessThan(0);
    assertThat(Quantity.parse("12dp").compareTo(Quantity.parse("121dp"))).isLessThan(0);
    assertThat(Quantity.parse("3210dp").compareTo(Quantity.parse("121dp"))).isGreaterThan(0);
    assertThat(Quantity.parse("50dp").compareTo(Quantity.parse("50dp"))).isEqualTo(0);
  }

  private static void assertQuantity(@Nullable Quantity quantity, int amount, @NotNull String unit) {
    assertThat(quantity).isNotNull();
    assertThat(quantity.getValue()).isEqualTo(amount);
    assertThat(quantity.getUnit()).isEqualTo(unit);
  }

  private static NlProperty mockProperty(@NotNull String attribute, @NotNull AttributeFormat format) {
    NlProperty property = Mockito.mock(NlProperty.class);
    Mockito.when(property.getName()).thenReturn(attribute);
    Mockito.when(property.getDefinition()).thenReturn(
        new AttributeDefinition(ResourceNamespace.RES_AUTO, attribute, null, null, Collections.singleton(format)));
    return property;
  }
}
