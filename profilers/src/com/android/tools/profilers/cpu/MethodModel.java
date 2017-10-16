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

package com.android.tools.profilers.cpu;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public class MethodModel {
  @NotNull private final String myClassName;
  @NotNull private final String myName;
  @NotNull private final String mySignature;
  @NotNull private final String mySeparator;
  private String myFullName;
  private String myId;
  /**
   * Whether the method is native.
   */
  private boolean myNative;

  public MethodModel(@NotNull String name, @NotNull String className, @NotNull String signature, @NotNull String separator) {
    myName = name;
    myClassName = className;
    mySignature = signature;
    mySeparator = separator;
  }

  public MethodModel(String name) {
    this(name, "", "", "");
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getClassName() {
    return myClassName;
  }

  @NotNull
  public String getSignature() {
    return mySignature;
  }

  @NotNull
  public String getSeparator() {
    return mySeparator;
  }

  public boolean isNative() {
    return myNative;
  }

  public void setNative(boolean isNative) {
    myNative = isNative;
  }

  @NotNull
  public String getFullName() {
    // Separator is only needed if we have a class name, otherwise we're gonna end up with a leading separator.
    // We don't have a class name, for instance, for native methods (e.g. clock_gettime)
    // or the special nodes created to represent a thread (e.g. AsyncTask #1).
    if (myFullName == null) {
      String separator = StringUtil.isEmpty(myClassName) ? "" : mySeparator;
      myFullName = String.format("%s%s%s", myClassName, separator, myName);
    }
    return myFullName;
  }

  public String getId() {
    if (myId == null) {
      myId = String.format("%s%s", getFullName(), mySignature);
    }
    return myId;
  }
}
