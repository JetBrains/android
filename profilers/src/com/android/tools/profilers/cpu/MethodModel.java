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

import com.google.common.annotations.VisibleForTesting;

public class MethodModel {

  private String myNamespace;
  private String myName;
  private String mySignature;
  private String myFilename;
  private int myLineNumber;

  @VisibleForTesting
  public MethodModel(String name) {
    myNamespace = "";
    myName = name;
    mySignature = "";
  }

  public MethodModel() {
  }

  public String getNameSpace() {
    return myNamespace;
  }

  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  public String getSignature() {
    return mySignature;
  }

  public void setSignature(String signature) {
    mySignature = signature;
  }

  public String getFilename() {
    return myFilename;
  }

  public void setFilename(String filename) {
    myFilename = filename;
  }

  public int getLineNumber() {
    return myLineNumber;
  }

  public void setLineNumber(int lineNumber) {
    myLineNumber = lineNumber;
  }

  public void setNamespace(String namespace) {
    myNamespace = namespace;
  }

  public String getId() {
    return myNamespace + ":" + myName + ":" + mySignature;
  }
}
