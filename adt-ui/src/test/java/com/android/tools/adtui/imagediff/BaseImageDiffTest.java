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
package com.android.tools.adtui.imagediff;

import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.Range;
import org.junit.Before;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public abstract class BaseImageDiffTest {
  /**
   * Total number of values added to the charts rendered in {@link #myContentPane}.
   */
  protected static final int TOTAL_VALUES = 50;

  /**
   * Simulated time delta, in microseconds, between each value added to the charts rendered in {@link #myContentPane}.
   */
  protected static final long TIME_DELTA_US = TimeUnit.MILLISECONDS.toMicros(50);

  protected JPanel myContentPane;

  protected Choreographer myChoreographer;

  protected long myCurrentTimeUs;

  protected Range myXRange;

  protected java.util.List<Animatable> myComponents;

  @Before
  public final void setUpBase() {
    myCurrentTimeUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
    myXRange = new Range(myCurrentTimeUs, myCurrentTimeUs + TOTAL_VALUES * TIME_DELTA_US);
    myContentPane = new JPanel(new BorderLayout());

    // We don't need to set a proper FPS to the choreographer, as we're interested in the final image only, not the animation.
    myChoreographer = new Choreographer(-1, myContentPane);
    myChoreographer.setUpdate(false);
    myComponents = new ArrayList<>();
    myComponents.add(myXRange);
  }
}
