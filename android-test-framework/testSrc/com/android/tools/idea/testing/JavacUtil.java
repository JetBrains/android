/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.testing;

import com.sun.tools.javac.api.JavacTool;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public class JavacUtil {
  /**
   * Returns a [JavaCompiler] that can be used to compile java files.
   */
  public static JavaCompiler getJavac() {
    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    if (javac == null) {
      // http://b/285585692
      // PathClassLoader does not support modules yet so ToolProvider will not be able to locate the JavaCompiler.
      javac = JavacTool.create();
    }
    return javac;
  }
}
