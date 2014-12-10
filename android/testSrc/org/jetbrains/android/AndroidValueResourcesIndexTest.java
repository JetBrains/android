/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.jetbrains.android;

import junit.framework.TestCase;

import static org.jetbrains.android.AndroidValueResourcesIndex.normalizeDelimiters;

public class AndroidValueResourcesIndexTest extends TestCase {
  public void testNormalizeDelimiters() {
    assertEquals("", normalizeDelimiters(""));
    assertEquals("foo123", normalizeDelimiters("foo123"));
    assertEquals("_", normalizeDelimiters("_"));
    assertEquals("Theme_Base", normalizeDelimiters("Theme.Base"));
    assertEquals("Theme_Base", normalizeDelimiters("Theme:Base"));
    assertEquals("___", normalizeDelimiters(":._"));

    // Check that we reuse the string when possible (since this method used to
    // be the source of a lot of GC objects)
    assertSame("foo", normalizeDelimiters("foo"));
    assertSame("foo_", normalizeDelimiters("foo_"));
  }
}