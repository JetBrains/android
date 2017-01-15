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
package com.android.tools.profilers.common;

import org.jetbrains.annotations.Nullable;

public class CodeLocation {
  public static final int INVALID_LINE_NUMBER = -1;

  @Nullable
  private String myClassName;

  @Nullable
  private String myFileName;

  @Nullable
  private String myMethodName;

  private int myLine;

  public CodeLocation(@Nullable String className) {
    this(className, null, null, INVALID_LINE_NUMBER);
  }

  public CodeLocation(@Nullable String className, int line) {
    this(className, null, null, line);
  }

  public CodeLocation(@Nullable String className, @Nullable String fileName, @Nullable String methodName, int line) {
    myClassName = className;
    myFileName = fileName;
    myMethodName = methodName;
    myLine = line;
  }

  @Nullable
  public String getClassName() {
    return myClassName;
  }

  @Nullable
  public String getFileName() {
    return myFileName;
  }

  @Nullable
  public String getMethodName() {
    return myMethodName;
  }

  public int getLine() {
    return myLine;
  }
}
