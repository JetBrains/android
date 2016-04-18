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
package org.jetbrains.android.uipreview;

import com.intellij.ui.ColorUtil;
import junit.framework.TestCase;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

public class ColorPickerTest extends TestCase {

  public void testColorWheelResizeAndSelect() {
    ColorPicker.SaturationBrightnessComponent saturationBrightnessComponent = new ColorPicker.SaturationBrightnessComponent();
    saturationBrightnessComponent.setSize(new Dimension(1010, 710));
    saturationBrightnessComponent.setHue(0.75f);
    saturationBrightnessComponent.setOpacity(100);
    MouseEvent event = new MouseEvent(saturationBrightnessComponent, MouseEvent.MOUSE_CLICKED, 1, InputEvent.BUTTON1_MASK, 805, 505, 1, false);
    for (MouseListener mouseListener : saturationBrightnessComponent.getMouseListeners()) {
      mouseListener.mousePressed(event);
    }
    Color expectedColor = ColorUtil.toAlpha(Color.getHSBColor(0.75f, 0.8f, 1.0f - 5.0f / 7.0f), 100);
    assertEquals(expectedColor, saturationBrightnessComponent.getColor());

    saturationBrightnessComponent.setSize(new Dimension(1510, 1010));
    saturationBrightnessComponent.setHue(0.0f);
    saturationBrightnessComponent.setOpacity(100);
    event = new MouseEvent(saturationBrightnessComponent, MouseEvent.MOUSE_CLICKED, 1, InputEvent.BUTTON1_MASK, 1505, 1005, 1, false);
    for (MouseListener mouseListener : saturationBrightnessComponent.getMouseListeners()) {
      mouseListener.mousePressed(event);
    }
    expectedColor = ColorUtil.toAlpha(Color.BLACK, 100);
    assertEquals(expectedColor, saturationBrightnessComponent.getColor());

    saturationBrightnessComponent.setSize(new Dimension(1510, 1010));
    saturationBrightnessComponent.setHue(0.0f);
    saturationBrightnessComponent.setOpacity(100);
    event = new MouseEvent(saturationBrightnessComponent, MouseEvent.MOUSE_CLICKED, 1, InputEvent.BUTTON1_MASK, 1505, 5, 1, false);
    for (MouseListener mouseListener : saturationBrightnessComponent.getMouseListeners()) {
      mouseListener.mousePressed(event);
    }
    expectedColor = ColorUtil.toAlpha(Color.RED, 100);
    assertEquals(expectedColor, saturationBrightnessComponent.getColor());
  }
}
