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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class ValueWithDisplayStringTest {

  @Test
  public void testGetters() {
    ValueWithDisplayString value = new ValueWithDisplayString("bread", "cookies", "hint");
    assertThat(value.getDisplayString()).isEqualTo("bread");
    assertThat(value.getValue()).isEqualTo("cookies");
    assertThat(value.toString()).isEqualTo("bread");
    assertThat(value.getHint()).isEqualTo("hint");

    value.setUseValueForToString(true);
    assertThat(value.getDisplayString()).isEqualTo("bread");
    assertThat(value.getValue()).isEqualTo("cookies");
    assertThat(value.toString()).isEqualTo("cookies");
    assertThat(value.getHint()).isEqualTo("hint");
  }

  @Test
  public void testEquals() {
    assertThat(new ValueWithDisplayString("display", "value")).isEqualTo(new ValueWithDisplayString("display", "value"));
    assertThat(new ValueWithDisplayString("display", "value")).isNotEqualTo(new ValueWithDisplayString("display", "value", "hint"));
    assertThat(new ValueWithDisplayString("display", "value")).isNotEqualTo(new ValueWithDisplayString("different", "value"));
    assertThat(new ValueWithDisplayString("display", "value")).isNotEqualTo(new ValueWithDisplayString("display", "other"));
    assertThat(new ValueWithDisplayString("display", "value")).isNotEqualTo(new Object());
    assertThat(new ValueWithDisplayString("display", "value")).isNotEqualTo(null);
  }
}
