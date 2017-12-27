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
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.google.common.collect.ImmutableList;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
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
public class IdEnumSupportTest {
  @Mock
  private NlProperty myProperty;
  @Mock
  private ResourceResolver myResolver;
  @Mock
  private IdAnalyzer myIdAnalyzer;

  private AttributeDefinition myDefinition;
  private IdEnumSupport mySupport;

  @Before
  public void setUp() {
    initMocks(this);
    myDefinition = new AttributeDefinition("property", null, null, Collections.emptyList());
    when(myProperty.getResolver()).thenReturn(myResolver);
    when(myProperty.resolveValue(anyString())).thenAnswer(invocation -> invocation.getArguments()[0]);
    when(myProperty.getDefinition()).thenReturn(myDefinition);
    mySupport = new IdEnumSupport(myProperty, myIdAnalyzer);
  }

  @Test
  public void testFindPossibleValues() {
    when(myIdAnalyzer.findIds()).thenReturn(ImmutableList.of("id1", "id2"));
    assertThat(mySupport.getAllValues()).containsExactly(
      new ValueWithDisplayString("id1", "@+id/id1"),
      new ValueWithDisplayString("id2", "@+id/id2")).inOrder();
  }

  @Test
  public void testFindPossibleValuesFromConstraintLayoutProperty() {
    myDefinition.addValue(SdkConstants.ATTR_PARENT);
    when(myIdAnalyzer.findIds()).thenReturn(ImmutableList.of("id1", "id2"));
    assertThat(mySupport.getAllValues()).containsExactly(
      new ValueWithDisplayString("id1", "@+id/id1"),
      new ValueWithDisplayString("id2", "@+id/id2"),
      new ValueWithDisplayString("parent", "parent")).inOrder();
  }

  @Test
  public void testFindPossibleValuesFromConstraintLayoutPropertyAndExistingParentId() {
    myDefinition.addValue(SdkConstants.ATTR_PARENT);
    when(myIdAnalyzer.findIds()).thenReturn(ImmutableList.of("id1", "id2", "parent"));
    assertThat(mySupport.getAllValues()).containsExactly(
      new ValueWithDisplayString("id1", "@+id/id1"),
      new ValueWithDisplayString("id2", "@+id/id2"),
      new ValueWithDisplayString("@+id/parent", "@+id/parent"),
      new ValueWithDisplayString("parent", "parent")).inOrder();
  }

  @Test
  public void testCreateDefaultValue() {
    assertThat(mySupport.createValue(""))
      .isEqualTo(ValueWithDisplayString.UNSET);
  }

  @Test
  public void testCreateValueWithPrefix() {
    assertThat(mySupport.createValue("@id/textBox"))
      .isEqualTo(new ValueWithDisplayString("textBox", "@id/textBox"));
  }

  @Test
  public void testCreateValueWithNewPrefix() {
    assertThat(mySupport.createValue("@+id/button"))
      .isEqualTo(new ValueWithDisplayString("button", "@+id/button"));
  }

  @Test
  public void testCreateValueFromConstraintLayoutProperty() {
    myDefinition.addValue(SdkConstants.ATTR_PARENT);
    assertThat(mySupport.createValue("parent"))
      .isEqualTo(new ValueWithDisplayString("parent", "parent"));
  }

  @Test
  public void testCreateValueFromConstraintLayoutPropertyOfExistingParentId() {
    myDefinition.addValue(SdkConstants.ATTR_PARENT);
    assertThat(mySupport.createValue("@+id/parent"))
      .isEqualTo(new ValueWithDisplayString("@+id/parent", "@+id/parent"));
  }

  @Test
  public void testCreateValueWithoutPrefix() {
    assertThat(mySupport.createValue("spinner"))
      .isEqualTo(new ValueWithDisplayString("spinner", "@+id/spinner"));
  }
}
