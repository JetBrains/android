/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.common.fixtures;

import com.android.tools.idea.uibuilder.LayoutTestUtilities;
import com.android.tools.adtui.common.SwingCoordinate;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MouseEventBuilder {
  private final int myX;
  private final int myY;
  private Object mySource = LayoutTestUtilities.class;
  private int myButton = 1;
  private int myMask = 0;
  private int myClickCount = 1;
  private int myId = 0;

  public MouseEventBuilder(@SwingCoordinate int x, @SwingCoordinate int y) {
    myX = x;
    myY = y;
  }

  public MouseEventBuilder withSource(Object source) {
    mySource = source;
    return this;
  }

  public MouseEventBuilder withMask(int mask) {
    myMask = mask;
    return this;
  }

  public MouseEventBuilder withButton(int button) {
    myButton = button;
    myMask |= InputEvent.getMaskForButton(button);
    return this;
  }

  public MouseEventBuilder withClickCount(int clickCount) {
    myClickCount = clickCount;
    return this;
  }

  public MouseEventBuilder withId(int id) {
    myId = id;
    return this;
  }

  public MouseEvent build() {
    MouseEvent event = mock(MouseEvent.class);
    when(event.getSource()).thenReturn(mySource);
    when(event.getX()).thenReturn(myX);
    when(event.getY()).thenReturn(myY);
    when(event.getModifiers()).thenReturn(myMask);
    when(event.getModifiersEx()).thenReturn(myMask);
    when(event.getButton()).thenReturn(myButton);
    when(event.getClickCount()).thenReturn(myClickCount);
    when(event.getPoint()).thenReturn(new Point(myX, myY));
    when(event.getWhen()).thenReturn(System.currentTimeMillis());
    when(event.getID()).thenReturn(myId);
    return event;
  }
}
