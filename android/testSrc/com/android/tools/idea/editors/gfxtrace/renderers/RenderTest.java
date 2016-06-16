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
package com.android.tools.idea.editors.gfxtrace.renderers;

import junit.framework.TestCase;

public class RenderTest extends TestCase {
  public void testToPointerString() throws Exception {

    assertEquals("0x00000000", Render.toPointerString(0));
    assertEquals("0x000004d2", Render.toPointerString(1234));
    assertEquals("0xffffffff", Render.toPointerString(0xFFFFFFFFl));
    assertEquals("0x0000000100000000", Render.toPointerString(0x100000000l));
    assertEquals("0xffffffffffffffff", Render.toPointerString(0xFFFFFFFFFFFFFFFFl));
  }

}
