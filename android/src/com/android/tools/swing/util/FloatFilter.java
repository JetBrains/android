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
package com.android.tools.swing.util;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

public class FloatFilter extends DocumentFilter {
  @Override
  public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs)
    throws BadLocationException {
    String current = getText(fb);
    fb.insertString(offset, cleanString(current.substring(0, offset), text, current.substring(offset, current.length())), attrs);
  }

  @Override
  public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
    throws BadLocationException {
    String current = getText(fb);
    fb.replace(offset, length, cleanString(current.substring(0, offset) , text, current.substring(offset + length, current.length())), attrs);
  }

  private static String cleanString(String before, String text, String after) {
    boolean allowSign = before.isEmpty() && (after.isEmpty() || (after.charAt(0) != '+' && after.charAt(0) != '-'));
    boolean allowDot = !before.contains(".") && !after.contains(".");
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '+' || c == '-') {
        if (i == 0 && allowSign) {
          allowSign = false;
          sb.append(c);
        }
      }
      else if (c == '.') {
        if (allowDot) {
          sb.append('.');
          allowDot = false;
        }
      } else if (c >= '0' && c <= '9') {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  private static String getText(FilterBypass fb) throws BadLocationException {
    return fb.getDocument().getText(0, fb.getDocument().getLength());
  }
}
