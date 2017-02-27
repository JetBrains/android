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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public final class CodeLocation {
  public static final int INVALID_LINE_NUMBER = -1;

  @NotNull
  private final String myClassName;

  @Nullable
  private final String myFileName;

  @Nullable
  private final String myMethodName;

  /**
   * Signature of a method, encoded by java type encoding
   *
   * For example:
   * {@code int aMethod(List<String> a, ArrayList<T> b, boolean c)}
   * the signature is (Ljava/util/List;Ljava/util/ArrayList;Z[[Ljava/lang/Integer;)I
   *
   * Java encoding: https://docs.oracle.com/javase/7/docs/api/java/lang/Class.html#getName()
   */
  @Nullable
  private final String mySignature;

  private final int myLineNumber;

  private final int myHashcode;

  public CodeLocation(@NotNull String className) {
    this(className, null, null, null, INVALID_LINE_NUMBER);
  }

  /**
   * @param lineNumber 0-based line number
   */
  public CodeLocation(@NotNull String className, int lineNumber) {
    this(className, null, null, null, lineNumber);
  }

  /**
   * @param lineNumber 0-based line number
   */
  public CodeLocation(@NotNull String className,
                      @Nullable String fileName,
                      @Nullable String methodName,
                      @Nullable String signature,
                      int lineNumber) {
    myClassName = className;
    myFileName = fileName;
    myMethodName = methodName;
    mySignature = signature;
    myLineNumber = lineNumber;
    myHashcode = Arrays.hashCode(new int[]{className.hashCode(), fileName == null ? 0 : fileName.hashCode(),
      methodName == null ? 0 : methodName.hashCode(), signature == null ? 0 : signature.hashCode(), Integer.hashCode(lineNumber)});
  }

  @NotNull
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

  @Nullable
  public String getSignature() {
    return mySignature;
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
           StringUtil.equals(mySignature, other.mySignature) &&
           myLineNumber == other.myLineNumber;
  }
}
