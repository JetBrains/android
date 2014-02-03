/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.editors;

import junit.framework.TestCase;

public class AndroidImportFilterTest extends TestCase {
  public void test() {
    AndroidImportFilter filter = new AndroidImportFilter();
    assertTrue(filter.shouldUseFullyQualifiedName(null, "android.R"));
    assertTrue(filter.shouldUseFullyQualifiedName(null, "android.R.anim"));
    assertTrue(filter.shouldUseFullyQualifiedName(null, "android.R.anything"));
    assertFalse(filter.shouldUseFullyQualifiedName(null, "com.android.tools.R"));
    assertTrue(filter.shouldUseFullyQualifiedName(null, "com.android.tools.R.anim"));
    assertTrue(filter.shouldUseFullyQualifiedName(null, "com.android.tools.R.layout"));
    assertTrue(filter.shouldUseFullyQualifiedName(null, "a.R.string"));
    assertFalse(filter.shouldUseFullyQualifiedName(null, "my.weird.clz.R"));
    assertFalse(filter.shouldUseFullyQualifiedName(null, "my.weird.clz.R.bogus"));
    assertFalse(filter.shouldUseFullyQualifiedName(null, ""));
    assertFalse(filter.shouldUseFullyQualifiedName(null, "."));
    assertFalse(filter.shouldUseFullyQualifiedName(null, "a.R"));
    assertFalse(filter.shouldUseFullyQualifiedName(null, "android"));
    assertFalse(filter.shouldUseFullyQualifiedName(null, "android."));
    assertFalse(filter.shouldUseFullyQualifiedName(null, "android.r"));
    assertFalse(filter.shouldUseFullyQualifiedName(null, "android.Random"));
    assertFalse(filter.shouldUseFullyQualifiedName(null, "my.R.unrelated"));
    assertFalse(filter.shouldUseFullyQualifiedName(null, "my.R.unrelated.to"));
    assertFalse(filter.shouldUseFullyQualifiedName(null, "R.string")); // R is never in the default package
  }
}
