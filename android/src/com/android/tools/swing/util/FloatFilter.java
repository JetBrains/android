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
    fb.insertString(offset, cleanString(getText(fb), text), attrs);
  }

  @Override
  public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
    throws BadLocationException {
    String currentText = getText(fb);
    currentText = currentText.substring(0, offset) + currentText.substring(offset + length, currentText.length());
    fb.replace(offset, length, cleanString(currentText, text), attrs);
  }

  private static String cleanString(String currentText, String text) {
    if (currentText.contains(".") || !text.contains(".")) {
      return text.replaceAll("\\D++", ""); // remove non-digits
    }
    text = text.replaceAll("[^0-9.]", "");
    int afterFirstDot = text.indexOf('.') + 1;
    return text.substring(0, afterFirstDot) + text.substring(afterFirstDot).replaceAll("\\.", "");
  }

  private static String getText(FilterBypass fb) throws BadLocationException {
    return fb.getDocument().getText(0, fb.getDocument().getLength());
  }
}
