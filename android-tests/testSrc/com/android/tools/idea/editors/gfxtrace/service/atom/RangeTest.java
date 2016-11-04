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
package com.android.tools.idea.editors.gfxtrace.service.atom;

  import junit.framework.TestCase;

public class RangeTest extends TestCase {
  public void testOverlaps() throws Exception {
    /* (#)-(##)(##)-(#) */
    Range[] ranges = new Range[] {
      new Range().setStart(0).setEnd(1),
      new Range().setStart(2).setEnd(4),
      new Range().setStart(4).setEnd(6),
      new Range().setStart(7).setEnd(8)
    };

    assertEquals(false, Range.overlaps(ranges, new Range().setStart(1).setEnd(2)));
    assertEquals(false, Range.overlaps(ranges, new Range().setStart(6).setEnd(7)));

    assertEquals(true, Range.overlaps(ranges, new Range().setStart(2).setEnd(3)));
    assertEquals(true, Range.overlaps(ranges, new Range().setStart(5).setEnd(6)));
    assertEquals(true, Range.overlaps(ranges, new Range().setStart(1).setEnd(3)));
    assertEquals(true, Range.overlaps(ranges, new Range().setStart(5).setEnd(7)));
    assertEquals(true, Range.overlaps(ranges, new Range().setStart(3).setEnd(5)));
    assertEquals(true, Range.overlaps(ranges, new Range().setStart(2).setEnd(5)));
    assertEquals(true, Range.overlaps(ranges, new Range().setStart(3).setEnd(6)));
  }
}
