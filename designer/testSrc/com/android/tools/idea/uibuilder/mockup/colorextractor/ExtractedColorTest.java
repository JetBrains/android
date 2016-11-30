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
package com.android.tools.idea.uibuilder.mockup.colorextractor;

import junit.framework.TestCase;

public class ExtractedColorTest extends TestCase {
  public void testEquals() throws Exception {
    assertEquals(new ExtractedColor(255, 10, null), new ExtractedColor(255, 45, null));
    assertFalse(new ExtractedColor(255, 10, null).equals(new ExtractedColor(254, 10, null)));
  }

}