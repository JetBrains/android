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
package com.android.tools.idea.ui.properties.constraints;

import com.android.tools.idea.ui.properties.core.IntValueProperty;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class ConstraintsTest {
  @Test
  public void testPercentConstraint() throws Exception {
    IntValueProperty percentValue = new IntValueProperty(-5);
    percentValue.addConstraint(RangeConstraint.forPercents());
    assertThat(percentValue.get()).isEqualTo(0);

    percentValue.set(-5);
    assertThat(percentValue.get()).isEqualTo(0);

    percentValue.set(105);
    assertThat(percentValue.get()).isEqualTo(100);

    percentValue.set(42);
    assertThat(percentValue.get()).isEqualTo(42);
  }
}