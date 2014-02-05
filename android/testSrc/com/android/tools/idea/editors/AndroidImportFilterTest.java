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
    assertTrue(AndroidImportFilter.shouldUseFullyQualifiedName("android.R"));
    assertTrue(AndroidImportFilter.shouldUseFullyQualifiedName("android.R.anim"));
    assertTrue(AndroidImportFilter.shouldUseFullyQualifiedName("android.R.anything"));
    assertFalse(AndroidImportFilter.shouldUseFullyQualifiedName("com.android.tools.R"));
    assertTrue(AndroidImportFilter.shouldUseFullyQualifiedName("com.android.tools.R.anim"));
    assertTrue(AndroidImportFilter.shouldUseFullyQualifiedName("com.android.tools.R.layout"));
    assertTrue(AndroidImportFilter.shouldUseFullyQualifiedName("a.R.string"));
    assertFalse(AndroidImportFilter.shouldUseFullyQualifiedName("my.weird.clz.R"));
    assertFalse(AndroidImportFilter.shouldUseFullyQualifiedName("my.weird.clz.R.bogus"));
    assertFalse(AndroidImportFilter.shouldUseFullyQualifiedName(""));
    assertFalse(AndroidImportFilter.shouldUseFullyQualifiedName("."));
    assertFalse(AndroidImportFilter.shouldUseFullyQualifiedName("a.R"));
    assertFalse(AndroidImportFilter.shouldUseFullyQualifiedName("android"));
    assertFalse(AndroidImportFilter.shouldUseFullyQualifiedName("android."));
    assertFalse(AndroidImportFilter.shouldUseFullyQualifiedName("android.r"));
    assertFalse(AndroidImportFilter.shouldUseFullyQualifiedName("android.Random"));
    assertFalse(AndroidImportFilter.shouldUseFullyQualifiedName("my.R.unrelated"));
    assertFalse(AndroidImportFilter.shouldUseFullyQualifiedName("my.R.unrelated.to"));
    assertFalse(AndroidImportFilter.shouldUseFullyQualifiedName("R.string")); // R is never in the default package
  }
}
