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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public final class CodeLocation {
  public static final int INVALID_LINE_NUMBER = -1;

  @Nullable
  private final String myClassName;

  @Nullable
  private final String myFileName;

  @Nullable
  private final String myMethodName;

  private final int myLineNumber;

  private final int myHashcode;

  public CodeLocation(@Nullable String className) {
    this(className, null, null, INVALID_LINE_NUMBER);
  }

  /**
   * @param lineNumber 0-based line number
   */
  public CodeLocation(@Nullable String className, int lineNumber) {
    this(className, null, null, lineNumber);
  }

  /**
   * @param lineNumber 0-based line number
   */
  public CodeLocation(@Nullable String className, @Nullable String fileName, @Nullable String methodName, int lineNumber) {
    myClassName = className;
    myFileName = fileName;
    myMethodName = methodName;
    myLineNumber = lineNumber;
    myHashcode = Arrays.hashCode(new int[]{className == null ? 0 : className.hashCode(), fileName == null ? 0 : fileName.hashCode(),
      methodName == null ? 0 : methodName.hashCode(), Integer.hashCode(lineNumber)});
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

  public int getLineNumber() {
    return myLineNumber;
  }

  @Override
  public int hashCode() {
    return myHashcode;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof CodeLocation)) {
      return false;
    }

    CodeLocation other = (CodeLocation)obj;
    return StringUtil.equals(myClassName, other.myClassName) &&
           StringUtil.equals(myFileName, other.myFileName) &&
           StringUtil.equals(myMethodName, other.myMethodName) &&
           myLineNumber == other.myLineNumber;
  }
}
