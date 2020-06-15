/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.motion;

import com.android.tools.idea.uibuilder.handlers.motion.editor.timeline.TickMarkCalculator;
import com.android.tools.idea.uibuilder.handlers.motion.editor.timeline.graph.Easing;
import com.android.tools.idea.uibuilder.handlers.motion.editor.timeline.graph.Interpolator;
import com.android.tools.idea.uibuilder.handlers.motion.editor.timeline.graph.LinearInterpolator;
import com.android.tools.idea.uibuilder.handlers.motion.editor.timeline.graph.MonotoneSpline;
import com.android.tools.idea.uibuilder.handlers.motion.editor.timeline.graph.Oscillator;
import java.text.DecimalFormat;
import junit.framework.TestCase;

public class TickMarkCalculatorTest extends TestCase {
 
  public void  testFramework_working() throws Exception {
    assertEquals(4, 2 + 2);
  }


  public void testTickMarkCalculator() throws Exception {
    TickMarkCalculator tickMarkCalculator = new TickMarkCalculator();
    tickMarkCalculator.setInsets(7,11,0,0);
    tickMarkCalculator.setRange(0, 100);
    tickMarkCalculator.calcRangeTicks( 100);
    int count = tickMarkCalculator.getCount();
    assertEquals(2,count);
      int[]ticks = new int[count];
    tickMarkCalculator.calcTicks(ticks);
    assertEquals(7,ticks[0]);
    assertEquals(89,ticks[1]);
    assertEquals(39,tickMarkCalculator.floatToPosition(40));

  }



}
