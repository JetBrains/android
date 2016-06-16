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
package com.android.tools.idea.editors.gfxtrace.service.image;

import junit.framework.TestCase;

public class FormatTest extends TestCase {
  public void testToString() throws Exception {
    assertEquals("ALPHA", Format.ALPHA.toString());
    assertEquals("FLOAT16", Format.HALF_FLOAT.toString());
    assertEquals("FLOAT32", Format.FLOAT32.toString());
    assertEquals("LUMINANCE", Format.LUMINANCE.toString());
    assertEquals("LUMINANCE_ALPHA", Format.LUMINANCE_ALPHA.toString());
    assertEquals("RGB", Format.RGB.toString());
    assertEquals("RGBA", Format.RGBA.toString());
    assertEquals("ETC2_RGBA8_EAC", Format.ETC2_RGBA8_EAC.toString());
  }
}
