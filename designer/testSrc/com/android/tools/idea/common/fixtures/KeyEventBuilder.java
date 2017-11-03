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

import java.awt.event.KeyEvent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KeyEventBuilder {
  private final int myKeyCode;
  private final char myKeyChar;
  private Object mySource = LayoutTestUtilities.class;
  private int myMask = 0;

  public KeyEventBuilder(int keyCode, char keyChar) {
    myKeyCode = keyCode;
    myKeyChar = keyChar;
  }

  public KeyEventBuilder withSource(Object source) {
    mySource = source;
    return this;
  }

  public KeyEventBuilder withMask(int mask) {
    myMask = mask;
    return this;
  }

  public KeyEvent build() {
    KeyEvent event = mock(KeyEvent.class);
    when(event.getSource()).thenReturn(mySource);
    when(event.getKeyCode()).thenReturn(myKeyCode);
    when(event.getKeyChar()).thenReturn(myKeyChar);
    when(event.getModifiers()).thenReturn(myMask);
    when(event.getModifiersEx()).thenReturn(myMask);
    when(event.getKeyLocation()).thenReturn(KeyEvent.KEY_LOCATION_UNKNOWN);
    when(event.getWhen()).thenReturn(System.currentTimeMillis());
    return event;
  }
}
