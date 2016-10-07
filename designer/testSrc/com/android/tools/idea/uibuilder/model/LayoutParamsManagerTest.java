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

import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.android.resources.Density;
import com.android.tools.idea.configurations.Configuration;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.junit.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LayoutParamsManagerTest {

  @SuppressWarnings("unused")
  private static class DefaultValues extends ViewGroup.LayoutParams {
    public static int staticAttribute = 456;
    private int privateAttribute = -123;
    private int protectedAttribute = 567;
    public String stringAttribute = "content";
    public int intAttribute = -50;
    public boolean booleanAttributeTrueDefault = true;
    public boolean booleanAttributeFalseDefault = false;

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

  @Test
  public void testGetDefaultValues() {
    Map<String, Object> defaults = LayoutParamsManager.getDefaultValuesFromClass(DefaultValues.class);

    // Private or static attributes shouldn't be returned
    assertEquals(7, defaults.size()); // 4 attributes from our class + with and height from LayoutParams
    assertTrue((Boolean)defaults.get("booleanAttributeTrueDefault"));
    assertFalse((Boolean)defaults.get("booleanAttributeFalseDefault"));
    assertTrue(-50 == (Integer)defaults.get("intAttribute"));
    assertEquals("content", defaults.get("stringAttribute"));
    assertEquals("other-string-attribute", defaults.get("otherStringAttribute"));

    DefaultValues layoutParams = new DefaultValues(0, 0);
    assertEquals(true,
                 LayoutParamsManager
                   .getDefaultValue(layoutParams, new LayoutParamsManager.MappedField("booleanAttributeTrueDefault", null)));
    try {
      LayoutParamsManager.getDefaultValue(layoutParams, new LayoutParamsManager.MappedField("notExistent", null));
      fail("Expected NoSuchElementException");
    }
    catch (NoSuchElementException ignore) {
    }
  }

  @Test
  public void testMapField() {
    // Check default mappings
    LinearLayoutParams layoutParams = new LinearLayoutParams();
    assertThat(LayoutParamsManager.mapField(layoutParams, "width").type, equalTo(EnumSet.of(AttributeFormat.Dimension)));
    assertThat(LayoutParamsManager.mapField(layoutParams, "height").type, equalTo(EnumSet.of(AttributeFormat.Dimension)));
    assertThat(LayoutParamsManager.mapField(layoutParams, "gravity").type, equalTo(EnumSet.of(AttributeFormat.Flag)));
    for (String m : new String[]{"marginTop", "marginStart", "marginBottom", "marginEnd", "marginEnd", "marginLeft", "marginRight"}) {
      assertThat(LayoutParamsManager.mapField(layoutParams, m).type, equalTo(EnumSet.of(AttributeFormat.Dimension)));
    }

    assertThat(LayoutParamsManager.mapField(layoutParams, "customRegisteredAttribute").type, empty());

    LayoutParamsManager.registerFieldMapper(LinearLayoutParams.class.getName(), (name) -> {
      if ("customRegisteredAttribute".equals(name)) {
        return new LayoutParamsManager.MappedField("intAttribute", AttributeFormat.Integer);
      }
      else if ("notExistingMapping".equals(name)) {
        // The resulting MappedField uses an attribute name that does not exist in the class. mapField will ignore this mapping.
        return new LayoutParamsManager.MappedField("missingIntAttribute", AttributeFormat.Integer);
      }

      return null;
    });
    assertThat(LayoutParamsManager.mapField(layoutParams, "customRegisteredAttribute").type, equalTo(EnumSet.of(AttributeFormat.Integer)));
    // Check that the mapping was ignored
    assertThat(LayoutParamsManager.mapField(layoutParams, "notExistingMapping").type, empty());
  }

  @Test
  public void testSetAttribute() {
    DefaultValues layoutParams = new DefaultValues(0, 0);
    Configuration configurationMock = mock(Configuration.class);
    when(configurationMock.getResourceResolver()).thenReturn(null);
    when(configurationMock.getDensity()).thenReturn(Density.HIGH);
    NlModel nlModelMock = mock(NlModel.class);
    when(nlModelMock.getConfiguration()).thenReturn(configurationMock);

    assertTrue(LayoutParamsManager.setAttribute(layoutParams, "intAttribute", "123456", nlModelMock));
    assertEquals(123456, layoutParams.intAttribute);
    // Incompatible types
    assertFalse(LayoutParamsManager.setAttribute(layoutParams, "intAttribute", "true", nlModelMock));
    assertEquals(123456, layoutParams.intAttribute);
    // Restore default value
    assertTrue(LayoutParamsManager.setAttribute(layoutParams, "intAttribute", null, nlModelMock));
    assertEquals(-50, layoutParams.intAttribute);

    assertTrue(LayoutParamsManager.setAttribute(layoutParams, "stringAttribute", "Hello world", nlModelMock));
    assertEquals("Hello world", layoutParams.stringAttribute);
    // Restore default value
    assertTrue(LayoutParamsManager.setAttribute(layoutParams, "stringAttribute", null, nlModelMock));
    assertEquals("content", layoutParams.stringAttribute);

    // Check dimension conversions
    assertTrue(LayoutParamsManager.setAttribute(layoutParams, "width", "123dp", nlModelMock));
    assertEquals(185, layoutParams.width);
    assertTrue(LayoutParamsManager.setAttribute(layoutParams, "width", "123px", nlModelMock));
    assertEquals(123, layoutParams.width);
    // Restore default value
    assertTrue(LayoutParamsManager.setAttribute(layoutParams, "width", null, nlModelMock));
    assertEquals(0, layoutParams.width);

    assertFalse(LayoutParamsManager.setAttribute(layoutParams, "notExistent", null, nlModelMock));
  }
}