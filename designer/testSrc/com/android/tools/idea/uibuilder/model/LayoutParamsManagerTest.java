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
package com.android.tools.idea.uibuilder.model;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.Density;
import com.android.tools.idea.configurations.Configuration;
import com.google.common.collect.ImmutableMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.NoSuchElementException;
import org.jetbrains.android.AndroidTestCase;
import com.android.tools.dom.attrs.AttributeDefinition;

public class LayoutParamsManagerTest extends AndroidTestCase {

  @SuppressWarnings("unused")
  private static class DefaultValues extends ViewGroup.LayoutParams {
    public static int staticAttribute = 456;
    private int privateAttribute = -123;
    private int protectedAttribute = 567;
    public String stringAttribute = "content";
    public int intAttribute = -50;
    public boolean booleanAttributeTrueDefault = true;
    public boolean booleanAttributeFalseDefault = false;
    public int flagAttribute = 987;

    public DefaultValues(int width, int height) {
      super(width, height);
    }

    public String getOtherStringAttribute() {
      return "other-string-attribute";
    }
  }

  @SuppressWarnings("unused")
  private static class LinearLayoutParams extends LinearLayout.LayoutParams {
    public int intAttribute = -1;

    public LinearLayoutParams() {
      super(0, 0);
    }
  }

  public void testGetDefaultValues() {
    Map<String, Object> defaults = LayoutParamsManager.getDefaultValuesFromClass(DefaultValues.class);

    // Private or static attributes shouldn't be returned
    assertThat(defaults.size()).isEqualTo(8); // 5 attributes from our class + with and height from LayoutParams
    assertThat((Boolean)defaults.get("booleanAttributeTrueDefault")).isTrue();
    assertThat((Boolean)defaults.get("booleanAttributeFalseDefault")).isFalse();
    assertThat(defaults.get("intAttribute")).isEqualTo(-50);
    assertThat(defaults.get("stringAttribute")).isEqualTo("content");
    assertThat(defaults.get("otherStringAttribute")).isEqualTo("other-string-attribute");

    DefaultValues layoutParams = new DefaultValues(0, 0);
    assertThat(LayoutParamsManager.getDefaultValue(layoutParams, new LayoutParamsManager.MappedField("booleanAttributeTrueDefault", null)))
      .isEqualTo(true);
    try {
      LayoutParamsManager.getDefaultValue(layoutParams, new LayoutParamsManager.MappedField("notExistent", null));
      fail("Expected NoSuchElementException");
    }
    catch (NoSuchElementException ignore) {
    }
  }

  public void testMapField() {
    // Check default mappings
    LinearLayoutParams layoutParams = new LinearLayoutParams();
    assertThat(LayoutParamsManager.mapField(layoutParams, "width").type).containsExactly(AttributeFormat.DIMENSION);
    assertThat(LayoutParamsManager.mapField(layoutParams, "height").type).containsExactly(AttributeFormat.DIMENSION);
    assertThat(LayoutParamsManager.mapField(layoutParams, "gravity").type).containsExactly(AttributeFormat.FLAGS);
    for (String m : new String[]{"marginTop", "marginStart", "marginBottom", "marginEnd", "marginEnd", "marginLeft", "marginRight"}) {
      assertThat(LayoutParamsManager.mapField(layoutParams, m).type).containsExactly(AttributeFormat.DIMENSION);
    }

    assertThat(LayoutParamsManager.mapField(layoutParams, "customRegisteredAttribute").type).isEmpty();

    LayoutParamsManager.registerFieldMapper(LinearLayoutParams.class.getName(), (name) -> {
      if ("customRegisteredAttribute".equals(name)) {
        return new LayoutParamsManager.MappedField("intAttribute", AttributeFormat.INTEGER);
      }
      else if ("notExistingMapping".equals(name)) {
        // The resulting MappedField uses an attribute name that does not exist in the class. mapField will ignore this mapping.
        return new LayoutParamsManager.MappedField("missingIntAttribute", AttributeFormat.INTEGER);
      }

      return null;
    });
    assertThat(LayoutParamsManager.mapField(layoutParams, "customRegisteredAttribute").type).containsExactly(AttributeFormat.INTEGER);
    // Check that the mapping was ignored
    assertThat(LayoutParamsManager.mapField(layoutParams, "notExistingMapping").type).isEmpty();
  }

  public void testSetAttribute() {
    DefaultValues layoutParams = new DefaultValues(0, 0);
    Configuration configurationMock = mock(Configuration.class);
    when(configurationMock.getResourceResolver()).thenReturn(null);
    when(configurationMock.getDensity()).thenReturn(Density.HIGH);

    assertThat(LayoutParamsManager.setAttribute(layoutParams, "intAttribute", "123456", myModule, configurationMock)).isTrue();
    assertThat(layoutParams.intAttribute).isEqualTo(123456);
    // Incompatible types
    assertThat(LayoutParamsManager.setAttribute(layoutParams, "intAttribute", "true", myModule, configurationMock)).isFalse();
    assertThat(layoutParams.intAttribute).isEqualTo(123456);
    // Restore default value
    assertThat(LayoutParamsManager.setAttribute(layoutParams, "intAttribute", null, myModule, configurationMock)).isTrue();
    assertThat(layoutParams.intAttribute).isEqualTo(-50);

    assertThat(LayoutParamsManager.setAttribute(layoutParams, "stringAttribute", "Hello world", myModule, configurationMock)).isTrue();
    assertThat(layoutParams.stringAttribute).isEqualTo("Hello world");
    // Restore default value
    assertThat(LayoutParamsManager.setAttribute(layoutParams, "stringAttribute", null, myModule, configurationMock)).isTrue();
    assertThat(layoutParams.stringAttribute).isEqualTo("content");

    // Check dimension conversions
    assertThat(LayoutParamsManager.setAttribute(layoutParams, "width", "123dp", myModule, configurationMock)).isTrue();
    assertThat(layoutParams.width).isEqualTo(185);
    assertThat(LayoutParamsManager.setAttribute(layoutParams, "width", "123px", myModule, configurationMock)).isTrue();
    assertThat(layoutParams.width).isEqualTo(123);
    // Restore default value
    assertThat(LayoutParamsManager.setAttribute(layoutParams, "width", null, myModule, configurationMock)).isTrue();
    assertThat(layoutParams.width).isEqualTo(0);

    assertThat(LayoutParamsManager.setAttribute(layoutParams, "notExistent", null, myModule, configurationMock)).isFalse();

    // Test flag attribute
    AttributeDefinition flagDefinition =
        new AttributeDefinition(ResourceNamespace.RES_AUTO, "flagAttribute", null, EnumSet.of(AttributeFormat.FLAGS));
    flagDefinition.setValueMappings(ImmutableMap.of("value1", 0b001, "value2", 0b010, "value3", 0b111));

    assertThat(LayoutParamsManager.setAttribute(flagDefinition, layoutParams, "flagAttribute", "invalidValue", myModule, configurationMock)).isFalse();
    assertThat(layoutParams.flagAttribute).isEqualTo(987); // Invalid value so no change expected
    assertThat(LayoutParamsManager.setAttribute(flagDefinition, layoutParams, "flagAttribute", "value3", myModule, configurationMock)).isTrue();
    assertThat(layoutParams.flagAttribute).isEqualTo(0b111);
    assertThat(LayoutParamsManager.setAttribute(flagDefinition, layoutParams, "flagAttribute", "value1", myModule, configurationMock)).isTrue();
    assertThat(layoutParams.flagAttribute).isEqualTo(0b001);
    assertThat(LayoutParamsManager.setAttribute(flagDefinition, layoutParams, "flagAttribute", "value1| value2|value3", myModule, configurationMock))
      .isTrue();
    assertThat(layoutParams.flagAttribute).isEqualTo(0b111); // This should be the three flags combined
    assertThat(LayoutParamsManager.setAttribute(flagDefinition, layoutParams, "flagAttribute", null, myModule, configurationMock)).isTrue();
    assertThat(layoutParams.flagAttribute).isEqualTo(987); // Check that default value restored
  }

  /**
   * Regression test for b/130048025
   * This checks that even if an attribute does not define {@link AttributeFormat}, we do not crash.
   */
  public void testEmptyAttributeFormat() {
    DefaultValues layoutParams = new DefaultValues(0, 0);
    Configuration configurationMock = mock(Configuration.class);
    when(configurationMock.getResourceResolver()).thenReturn(null);
    when(configurationMock.getDensity()).thenReturn(Density.HIGH);

    // Test flag attribute
    AttributeDefinition flagDefinition =
      new AttributeDefinition(ResourceNamespace.RES_AUTO, "flagAttribute", null, EnumSet.noneOf(AttributeFormat.class));
    flagDefinition.setValueMappings(ImmutableMap.of("value1", 0b001, "value2", 0b010, "value3", 0b111));
    // The set will fail but will not crash
    assertThat(LayoutParamsManager.setAttribute(flagDefinition, layoutParams, "flagAttribute", "value3", myModule, configurationMock))
      .isFalse();
  }
}
