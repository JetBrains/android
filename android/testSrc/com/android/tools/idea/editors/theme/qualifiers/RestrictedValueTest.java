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

import junit.framework.TestCase;

public class RestrictedValueTest extends TestCase {
  /**
   * Tests {@link RestrictedValue#intersect(RestrictedQualifier)}
   */
  public void testIntersect() {
    assertEquals(new RestrictedValue(13, 15), new RestrictedValue(12, 15).intersect(new RestrictedValue(13, 17)));
    assertNull(new RestrictedValue(12, 15).intersect(new RestrictedValue(16, 17)));
  }
}