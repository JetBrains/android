/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme;

import java.awt.Color;
import org.jetbrains.android.AndroidTestCase;

@SuppressWarnings("InspectionUsingGrayColors")
public class ColorUtilsTest extends AndroidTestCase {
  public void testCalculateContrastRatio() {
    assertEquals(21.0, ColorUtils.calculateContrastRatio(Color.BLACK, Color.WHITE), 0.01);
    assertEquals(1.87, ColorUtils.calculateContrastRatio(Color.decode("#80CBC4"), Color.WHITE), 0.01);
    assertEquals(ColorUtils.calculateContrastRatio(Color.decode("#80CBC4"), Color.WHITE),
                 ColorUtils.calculateContrastRatio(Color.WHITE, Color.decode("#80CBC4")));
    assertEquals(1.96, ColorUtils.calculateContrastRatio(Color.WHITE, Color.decode("#CBBC06")), 0.01);
    assertEquals(1.24, ColorUtils.calculateContrastRatio(Color.BLACK, Color.decode("#2E054A")), 0.01);
    assertEquals(16.98, ColorUtils.calculateContrastRatio(Color.WHITE, Color.decode("#2E054A")), 0.01);
    assertEquals(5.25, ColorUtils.calculateContrastRatio(Color.BLACK, Color.RED), 0.01);
    assertEquals(ColorUtils.calculateContrastRatio(Color.BLACK, Color.RED), ColorUtils.calculateContrastRatio(Color.BLACK, Color.RED));
    assertEquals(9.10, ColorUtils.calculateContrastRatio(Color.decode("#2E054A"), Color.decode("#80CBC4")), 0.01);
  }

  public void testWorstContrastColor() {
    assertEquals(new Color(48, 78, 241), ColorUtils.worstContrastColor(new Color(48, 78, 241, 15), new Color(120, 46, 97, 0)));
    assertEquals(new Color(0, 0, 0), ColorUtils.worstContrastColor(new Color(48, 78, 241, 15), new Color(120, 46, 97, 255)));
    assertEquals(new Color(0, 255, 177), ColorUtils.worstContrastColor(new Color(10, 150, 100, 15), new Color(100, 10, 50, 155)));
    assertEquals(new Color(0, 255, 177), ColorUtils.worstContrastColor(new Color(10, 150, 100, 200), new Color(100, 10, 50, 155)));
  }
}
