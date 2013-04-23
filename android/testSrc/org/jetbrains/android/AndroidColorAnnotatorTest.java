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

package org.jetbrains.android;

import junit.framework.TestCase;

import java.awt.*;

public class AndroidColorAnnotatorTest extends TestCase {
  public void testRGB() {
    Color c = AndroidColorAnnotator.parseColor("#0f4");
    assert c != null;
    assertEquals(0xff00ff44, c.getRGB());

    c = AndroidColorAnnotator.parseColor("#1237");
    assert c != null;
    assertEquals(0x11223377, c.getRGB());

    c = AndroidColorAnnotator.parseColor("#123456");
    assert c != null;
    assertEquals(0xff123456, c.getRGB());

    c = AndroidColorAnnotator.parseColor("#08123456");
    assert c != null;
    assertEquals(0x08123456, c.getRGB());

    c = AndroidColorAnnotator.parseColor("black");
    assert c != null;
    assertEquals(0xff000000, c.getRGB());
  }
}
