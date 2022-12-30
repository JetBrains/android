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
package com.android.tools.idea.uibuilder.handlers.motion.editor.utils;

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.Utils;

/**
 * Provides utilities used in debugging
 */
public final class Debug {

  public static void log(String msg) {
    StackTraceElement s = new Throwable().getStackTrace()[1];
    System.out.println(".(" + s.getFileName() + ":" + s.getLineNumber() + ")" + msg);
  }

  /**
   * This logs n elements in the stack
   *
   * @param msg
   * @param n
   * @hide
   */
  public static void logStack(String msg, int n) {
    StackTraceElement[] st = new Throwable().getStackTrace();
    String s = " ";
    n = Math.min(n, st.length - 1);
    for (int i = 1; i <= n; i++) {
      StackTraceElement ste = st[i];
      String stack = ".(" + st[i].getFileName() + ":" + st[i].getLineNumber() + ") ";
      s += " ";
      System.out.println(msg + s + stack + s);
    }
  }

  /**
   * Get file name and location where this method is called.
   * Formatting it such that it is clickable by Intellij
   *
   * @return (filename : line_no)
   */
  public static String getLocation() {
    StackTraceElement s = new Throwable().getStackTrace()[1];
    return ".(" + s.getFileName() + ":" + s.getLineNumber() + ")";
  }

  public static String toString(MTag[] tags) {
    if (tags == null) {
      return " null";
    }
    if (tags.length == 0) {
      return " []";
    }
    String str = " [";
    for (int i = 0; i < tags.length; i++) {
      MTag tag = tags[i];
      if (i > 0) {
        str += ",";
      }
      str += Utils.stripID(tag.getAttributeValue("id"));
    }
    return str + "]";
  }
}
