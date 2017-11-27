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
package com.android.tools.profilers.cpu.nodemodel;

import org.jetbrains.annotations.NotNull;

/**
 * Represents characteristics of Java methods.
 */
public class JavaMethodModel implements MethodModel {
  @NotNull private final String myName;

  /**
   * Method's full class name (e.g. java.lang.String).
   */
  @NotNull private final String myClassName;

  /**
   * Signature of a method, encoded by java type encoding.
   *
   * For example:
   * {@code int aMethod(List<String> a, ArrayList<T> b, boolean c, Integer[][] d)}
   * the signature is (Ljava/util/List;Ljava/util/ArrayList;Z[[Ljava/lang/Integer;)I
   *
   * Java encoding: https://docs.oracle.com/javase/7/docs/api/java/lang/Class.html#getName()
   */
  @NotNull private final String mySignature;

  private String myFullName;

  private String myId;

  public JavaMethodModel(@NotNull String name, @NotNull String className, @NotNull String signature) {
    myName = name;
    myClassName = className;
    mySignature = signature;
  }

  public JavaMethodModel(@NotNull String name, @NotNull String className) {
    this(name, className, "");
  }

  @Override
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

  @Override
  @NotNull
  public String getFullName() {
    if (myFullName == null) {
      myFullName = String.format("%s.%s", myClassName, myName);
    }
    return myFullName;
  }

  @Override
  @NotNull
  public String getId() {
    if (myId == null) {
      myId = String.format("%s%s", getFullName(), mySignature);
    }
    return myId;
  }
}
