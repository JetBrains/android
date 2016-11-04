/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme.qualifiers;

import com.android.ide.common.resources.configuration.KeyboardStateQualifier;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.android.resources.KeyboardState;
import com.google.common.collect.ImmutableList;
import junit.framework.TestCase;


public class RestrictedEnumTest extends TestCase {
  /**
   * Tests {@link RestrictedEnum#intersect(RestrictedQualifier)}
   */
  public void testIntersect() {
    RestrictedEnum first = new RestrictedEnum(KeyboardState.class);
    RestrictedEnum second = new RestrictedEnum(KeyboardState.class);

    ResourceQualifier exposed = new KeyboardStateQualifier(KeyboardState.EXPOSED);
    ResourceQualifier hidden = new KeyboardStateQualifier(KeyboardState.HIDDEN);
    ResourceQualifier soft = new KeyboardStateQualifier(KeyboardState.SOFT);

    first.setRestrictions(null, ImmutableList.of(exposed));
    second.setRestrictions(null, ImmutableList.of(hidden));

    RestrictedEnum result = (RestrictedEnum)first.intersect(second);
    assertNotNull(result);
    assert result.isMatchFor(soft);
    assert !result.isMatchFor(exposed);
    assert !result.isMatchFor(hidden);

    first = new RestrictedEnum(KeyboardState.class);
    second = new RestrictedEnum(KeyboardState.class);
    first.setRestrictions(null, ImmutableList.of(exposed, hidden));
    second.setRestrictions(null, ImmutableList.of(soft));
    assertNull(first.intersect(second));
  }
}