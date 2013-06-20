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
  public void testColorAnnotator() {
    // TODO: Add test. Unfortunately, it looks like none of the existing Annotator classes
    // in IntelliJ have unit tests, so there doesn't appear to be fixture support for this.
  }

  public void testColorToString() {
    Color color = new Color(0x11, 0x22, 0x33, 0xf0);
    assertEquals("#f0112233", AndroidColorAnnotator.colorToString(color));

    color = new Color(0xff, 0xff, 0xff, 0x00);
    assertEquals("#ffffff", AndroidColorAnnotator.colorToString(color));
  }
}
