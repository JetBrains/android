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
package com.android.tools.idea.uibuilder.surface;

import junit.framework.TestCase;


public class ZoomTypeTest extends TestCase {
  public void testZoomIn() {
    StringBuilder sb = new StringBuilder();

    int percentage = 2;
    while (percentage < 1000) {

      int next = ZoomType.zoomIn(percentage);
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(Integer.toString(next)).append("%");

      int middle = (next + percentage) / 2;
      if (middle < next && middle > 25) {
        int avg = ZoomType.zoomIn(middle);
        assertEquals(avg, next);
      }
      percentage = next;
    }

    assertEquals("3%, 4%, 5%, 6%, 7%, 8%, 9%, 10%, 11%, 13%, 15%, 17%, 19%, 21%, 24%, 25%, 33%, 50%, 67%, 75%, 90%, 100%, 110%, 125%, " +
                 "150%, 200%, 300%, 400%, 500%, 600%, 700%, 800%, 900%, 1000%", sb.toString());
  }

  public void testZoomOut() {
    StringBuilder sb = new StringBuilder();

    int percentage = 1000;
    while (percentage >= 3) {

      int next = ZoomType.zoomOut(percentage);
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(Integer.toString(next)).append("%");

      int middle = (next + percentage) / 2;
      if (middle < next && middle > 25) {
        int avg = ZoomType.zoomOut(middle);
        assertEquals(avg, next);
      }
      percentage = next;
    }

    assertEquals("900%, 800%, 700%, 600%, 500%, 400%, 300%, 200%, 150%, 125%, 110%, 100%, 90%, 75%, 67%, 50%, 33%, 25%, 22%, 20%, " +
                 "18%, 16%, 14%, 12%, 10%, 9%, 8%, 7%, 6%, 5%, 4%, 3%, 2%", sb.toString());
  }
}