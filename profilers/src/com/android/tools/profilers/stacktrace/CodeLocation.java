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
package com.android.tools.profilers.stacktrace;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

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
   * See {@link Builder#setMethodSignature(String, String)} for details about this field.
   */
  @Nullable
  private final String mySignature;
  private final int myLineNumber;
  private final boolean myNativeCode;
  private final int myHashcode;

  private CodeLocation(@NotNull Builder builder) {
    myClassName = builder.myClassName;
    myFileName = builder.myFileName;
    myMethodName = builder.myMethodName;
    mySignature = builder.mySignature;
    myLineNumber = builder.myLineNumber;
    myNativeCode = builder.myNativeCode;
    myHashcode = Arrays.hashCode(new int[]{myClassName.hashCode(), myFileName == null ? 0 : myFileName.hashCode(),
      myMethodName == null ? 0 : myMethodName.hashCode(), mySignature == null ? 0 : mySignature.hashCode(),
      Integer.hashCode(myLineNumber)});
  }

  @TestOnly
  @NotNull
  public static CodeLocation stub() {
    return new CodeLocation.Builder("").build();
  }

  @NotNull
  public String getClassName() {
    return myClassName;
  }

  /**
   * Convenience method for stripping all inner classes (e.g. anything following the first "$")
   * from {@link #getClassName()}. If this code location's class is already an outer class, its
   * name is returned as is.
   */
  @NotNull
  public String getOuterClassName() {
    int innerCharIndex = myClassName.indexOf('$');
    if (innerCharIndex < 0) {
      innerCharIndex = myClassName.length();
    }
    return myClassName.substring(0, innerCharIndex);
  }

  @Nullable
  public String getFileName() {
    return myFileName;
  }

  @Nullable
  public String getMethodName() {
    return myMethodName;
  }

  /**
   * @param lineNumber 0-based line number
   */
  public int getLineNumber() {
    return myLineNumber;
  }

  /**
   * See {@link Builder#setMethodSignature(String, String)} for details about this value.
   */
  @Nullable
  public String getSignature() {
    return mySignature;
  }

  public boolean isNativeCode() {
    return myNativeCode;
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

  public static final class Builder {
    @NotNull private final String myClassName;
    @Nullable String myFileName;
    @Nullable String myMethodName;
    @Nullable String mySignature;
    int myLineNumber = INVALID_LINE_NUMBER;
    boolean myNativeCode;

    public Builder(@NotNull String className) {
      myClassName = className;
    }

    public Builder(@NotNull CodeLocation rhs) {
      myClassName = rhs.getClassName();
      myFileName = rhs.getFileName();
      myMethodName = rhs.getMethodName();
      mySignature = rhs.getSignature();
      myLineNumber = rhs.getLineNumber();
    }

    @NotNull
    public Builder setFileName(@Nullable String fileName) {
      myFileName = StringUtil.nullize(fileName); // "" is an invalid name and should be converted to null
      return this;
    }

    @NotNull
    public Builder setMethodName(@Nullable String methodName) {
      myMethodName = StringUtil.nullize(methodName); // "" is an invalid name and should be converted to null
      return this;
    }

    /**
     * Signature of a method, encoded by java type encoding
     *
     * For example:
     * {@code int aMethod(List<String> a, ArrayList<T> b, boolean c, Integer[][] d)}
     * the signature is (Ljava/util/List;Ljava/util/ArrayList;Z[[Ljava/lang/Integer;)I
     *
     * Java encoding: https://docs.oracle.com/javase/7/docs/api/java/lang/Class.html#getName()
     */
    @NotNull
    public Builder setMethodSignature(@NotNull String methodName, @NotNull String signature) {
      myMethodName = methodName;
      mySignature = signature;
      return this;
    }

    /**
     * @param lineNumber 0-based line number.
     */
    @NotNull
    public Builder setLineNumber(int lineNumber) {
      myLineNumber = lineNumber;
      return this;
    }

    public Builder setNativeCode(boolean nativeCode) {
      myNativeCode = nativeCode;
      return this;
    }

    @NotNull
    public CodeLocation build() {
      return new CodeLocation(this);
    }
  }
}
