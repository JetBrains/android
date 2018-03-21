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

import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.common.property.NlProperty;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(JUnit4.class)
public class SimpleEnumSupportTest {
  @Mock
  private NlProperty myProperty;
  @Mock
  private ResourceResolver myResolver;

  private SimpleEnumSupport mySupport;

  @Before
  public void setUp() {
    initMocks(this);
    when(myProperty.getResolver()).thenReturn(myResolver);
    when(myProperty.resolveValue(anyString())).thenAnswer(invocation -> invocation.getArguments()[0]);
    when(myProperty.resolveValue("@string/font_family_body_1_material")).thenReturn("sans-serif");
    mySupport = new SimpleEnumSupport(myProperty, ImmutableList.of("item1", "item2"));
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
  public void testCreateFromItem() {
    assertThat(mySupport.createValue("item1"))
      .isEqualTo(new ValueWithDisplayString("item1", "item1"));
  }

  @Test
  public void testCreateFromResourceString() {
    assertThat(mySupport.createValue("@string/font_family_body_1_material"))
      .isEqualTo(new ValueWithDisplayString("sans-serif", "@string/font_family_body_1_material", "@string/font_family_body_1_material"));
  }
}
