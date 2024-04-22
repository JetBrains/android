/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion;

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEScenePicker;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Drawing;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import junit.framework.TestCase;

public class DrawTest extends TestCase {

  GeneralPath mPath = new GeneralPath();
  int mRectPathLen = 4;
  int[] mRectPathX = new int[mRectPathLen];
  int[] mRectPathY = new int[mRectPathLen];

  private String toString(GeneralPath path) {
    String result = "";
    float[] coordinates = new float[6];
    for (PathIterator it = path.getPathIterator(null); !it.isDone(); it.next()) {
      result += "{";
      result += it.currentSegment(coordinates) + ",";
      result += coordinates[0] + ", " + coordinates[1];
      result += "},";
    }
    return result;
  }

  class TestPicker extends MEScenePicker {
    String content = new String();
    @Override
    public void addLine(Object e, int range, int x1, int y1, int x2, int y2, int width) {
      super.addLine(e, range, x1, y1, x2, y2, width);
      content += "line " + range + " : " + x1 + ", " + y1 + ", " + x2 + ", " + y2 + " (" + width + ")\n";
    }

    @Override
    public String toString() {
      return content;
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mPath.reset();
  }

  public void testDrawRound() {
    mRectPathX[0] = 0;
    mRectPathY[0] = 0;
    mRectPathX[1] = 0;
    mRectPathY[1] = 100;
    mRectPathX[2] = 100;
    mRectPathY[2] = 100;
    mRectPathX[3] = 100;
    mRectPathY[3] = 0;
    mPath.moveTo(0, 0);
    Drawing.drawRound(mPath, mRectPathX, mRectPathY, mRectPathLen, 20);
    String result = toString(mPath);
    assertEquals("{0,0.0, 0.0},{1,0.0, 80.0},{3,6.4934393E-16, 85.30229}," +
                 "{3,9.607154, 97.891426},{1,20.0, 100.0},{1,80.0, 100.0},{3,85.30229, 100.0}," +
                 "{3,97.891426, 90.392845},{1,100.0, 80.0},{1,100.0, 0.0},", result);
  }

  public void testDrawPick() {
    mRectPathX[0] = 0;
    mRectPathY[0] = 0;
    mRectPathX[1] = 0;
    mRectPathY[1] = 100;
    mRectPathX[2] = 100;
    mRectPathY[2] = 100;
    mRectPathX[3] = 100;
    mRectPathY[3] = 0;
    mPath.moveTo(0, 0);
    TestPicker picker = new TestPicker();
    Drawing.drawPick(picker, null, mRectPathX, mRectPathY, mRectPathLen, 20);
    String result = picker.toString();
    assertEquals("line 4 : 0, 0, 0, 80 (4)\n" +
                 "line 4 : 20, 100, 0, 80 (4)\n" +
                 "line 4 : 20, 100, 80, 100 (4)\n" +
                 "line 4 : 100, 80, 80, 100 (4)\n" +
                 "line 4 : 100, 80, 100, 0 (4)\n", result);
  }
}
