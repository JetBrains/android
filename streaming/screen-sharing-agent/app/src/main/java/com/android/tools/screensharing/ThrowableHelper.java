/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.screensharing;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ThrowableHelper {
  /**
   * Returns the class name and a backtrace of the stack of the given throwable as a string.
   *
   * @param t the throwable to get the description for
   * @return the string containing the description
   */
  public static String describe(Throwable t) {
    StringWriter stringWriter = new StringWriter();
    try (PrintWriter writer = new PrintWriter(stringWriter)) {
      t.printStackTrace(writer);
      return stringWriter.toString();
    } catch (Throwable t2) {
      return t.toString();
    }
  }
}
