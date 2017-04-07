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

import com.google.common.primitives.UnsignedLong;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.text.DefaultFormatter;
import java.text.ParseException;

public class HexFormatter extends DefaultFormatter {
  @Override
  public Object stringToValue(String text) throws ParseException {
    try {
      return UnsignedLong.valueOf(text, 16).bigIntegerValue();
    }
    catch (NumberFormatException nfe) {
      throw new ParseException(text, 0);
    }
  }

  @Override
  public String valueToString(Object value) throws ParseException {
    return String.format("%016X", ((Number)value).longValue());
  }
}
