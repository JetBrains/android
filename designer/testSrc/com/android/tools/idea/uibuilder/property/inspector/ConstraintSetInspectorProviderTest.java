/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.inspector;

import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintSetInspectorProvider;
import com.android.tools.idea.uibuilder.property.PropertyTestCase;
import com.google.common.collect.ImmutableList;

import java.util.Collections;

import static com.google.common.truth.Truth.assertThat;

public class ConstraintSetInspectorProviderTest extends PropertyTestCase {
  private ConstraintSetInspectorProvider myProvider;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myProvider = new ConstraintSetInspectorProvider();
  }

  public void testIsApplicable() {
    // There must be exactly 1 element selected:
    assertThat(myProvider.isApplicable(Collections.emptyList(), Collections.emptyMap(), myPropertiesManager)).isFalse();
    assertThat(myProvider.isApplicable(ImmutableList.of(myConstraintLayout, myConstraintLayout), Collections.emptyMap(), myPropertiesManager)).isFalse();

    // If no constraintSet field, don't apply
    assertThat(myProvider.isApplicable(ImmutableList.of(myConstraintLayout), Collections.emptyMap(), myPropertiesManager)).isFalse();
    assertThat(myProvider.isApplicable(ImmutableList.of(myConstraintLayoutWithConstraintSet), Collections.emptyMap(), myPropertiesManager)).isTrue();
  }
}
