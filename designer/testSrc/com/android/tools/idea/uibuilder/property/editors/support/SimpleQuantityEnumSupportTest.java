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

import com.android.SdkConstants;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.common.property.NlProperty;
import com.google.common.collect.ImmutableList;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

import java.util.Collections;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(JUnit4.class)
public class SimpleQuantityEnumSupportTest {
  @Mock
  private NlProperty myProperty;
  @Mock
  private ResourceResolver myResolver;
  @Mock
  private AttributeDefinition myNumericDefinition;

  private SimpleQuantityEnumSupport mySupport;

  @Before
  public void setUp() {
    initMocks(this);
    when(myProperty.getResolver()).thenReturn(myResolver);
    when(myProperty.resolveValue(anyString())).thenAnswer(invocation -> invocation.getArguments()[0]);
    when(myNumericDefinition.getFormats()).thenReturn(Collections.singleton(AttributeFormat.Dimension));
    mySupport = new SimpleQuantityEnumSupport(myProperty, ImmutableList.of("item1", "item2"));
  }

  @Test
  public void testFindPossibleValues() {
    assertThat(mySupport.getAllValues()).containsExactly(
      new ValueWithDisplayString("item1", "item1"),
      new ValueWithDisplayString("item2", "item2")).inOrder();
  }

  @Test
  public void testCreateDefaultValue() {
    assertThat(mySupport.createValue(""))
      .isEqualTo(ValueWithDisplayString.UNSET);
  }

  @Test
  public void testNumericValueWithUnits() {
    when(myProperty.getName()).thenReturn(SdkConstants.ATTR_WIDTH);
    when(myProperty.getDefinition()).thenReturn(myNumericDefinition);
    assertThat(mySupport.createValue("100dp"))
      .isEqualTo(new ValueWithDisplayString("100dp", "100dp"));
  }

  @Test
  public void testNumericValueWithoutUnits() {
    when(myProperty.getName()).thenReturn(SdkConstants.ATTR_WIDTH);
    when(myProperty.getDefinition()).thenReturn(myNumericDefinition);
    assertThat(mySupport.createValue("100"))
      .isEqualTo(new ValueWithDisplayString("100dp", "100dp"));
  }

  @Test
  public void testNumericTextSizeValueWithoutUnits() {
    when(myProperty.getName()).thenReturn(SdkConstants.ATTR_TEXT_SIZE);
    when(myProperty.getDefinition()).thenReturn(myNumericDefinition);
    assertThat(mySupport.createValue("30"))
      .isEqualTo(new ValueWithDisplayString("30sp", "30sp"));
  }
}
