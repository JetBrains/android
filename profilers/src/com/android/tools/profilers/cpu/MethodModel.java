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

import org.jetbrains.annotations.NotNull;

public class MethodModel {

  private String myClassName;
  private final String myName;
  private String mySignature;

  public MethodModel(String name) {
    myName = name;
    myClassName = "";
    mySignature = "";
  }

  public String getName() {
    return myName;
  }

  public String getClassName() {
    return myClassName;
  }

  public void setClassName(String namespace) {
    myClassName = namespace;
  }

  @NotNull
  public String getSignature() {
    return mySignature;
  }

  public void setSignature(@NotNull String signature) {
    mySignature = signature;
  }

  public String getId() {
    return String.format("%s.%s%s", myClassName, myName, mySignature);
  }
}
