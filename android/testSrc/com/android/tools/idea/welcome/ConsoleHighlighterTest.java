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
package com.android.tools.idea.welcome;

import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import junit.framework.TestCase;

public class ConsoleHighlighterTest extends TestCase {
  private ConsoleHighlighter myHighlighter;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myHighlighter = new ConsoleHighlighter();
    myHighlighter.print("12345", null);
    myHighlighter.print("1234567890", null);
    myHighlighter.print("12345678901234567890", null);
  }

  public void testOffsetRangeIndex() {
    assertEquals(-1, myHighlighter.getOffsetRangeIndex(-1));
    assertEquals(-1, myHighlighter.getOffsetRangeIndex(35));
    assertEquals(-1, myHighlighter.getOffsetRangeIndex(200));
    assertEquals(0, myHighlighter.getOffsetRangeIndex(0));
    assertEquals(0, myHighlighter.getOffsetRangeIndex(4));
    assertEquals(1, myHighlighter.getOffsetRangeIndex(5));
    assertEquals(1, myHighlighter.getOffsetRangeIndex(7));
    assertEquals(1, myHighlighter.getOffsetRangeIndex(14));
    assertEquals(2, myHighlighter.getOffsetRangeIndex(15));
    assertEquals(2, myHighlighter.getOffsetRangeIndex(34));
  }

  public void testIteratorEnd() {
    HighlighterIterator iterator = myHighlighter.createIterator(0);

    assertFalse(iterator.atEnd());
    iterator.retreat();
    assertTrue(iterator.atEnd());
    iterator.advance();
    assertFalse(iterator.atEnd());
    iterator.advance();
    assertFalse(iterator.atEnd());
    iterator.advance();
    assertFalse(iterator.atEnd());
    iterator.advance();
    assertTrue(iterator.atEnd());
    iterator.advance();
    assertTrue(iterator.atEnd());
  }
}