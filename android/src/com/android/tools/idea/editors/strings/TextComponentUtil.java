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
package com.android.tools.idea.editors.strings;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;

public class TextComponentUtil {
  public static void setTextAndEditable(@NotNull JTextComponent component, @NotNull String text, boolean editable) {
    component.setText(text);
    component.setCaretPosition(0);
    component.setEditable(editable);
    // If a text component is not editable when it gains focus and becomes editable while still focused,
    // the caret does not appear, so we need to set the caret visibility manually
    component.getCaret().setVisible(editable && component.hasFocus());
  }

  static void formatTextComponent(@NotNull JTextComponent component) {
    if (component.getParent() instanceof JViewport) {
      component.getParent().setMinimumSize(new Dimension(0, 2 * component.getFontMetrics(component.getFont()).getHeight()));
    }
  }
}
