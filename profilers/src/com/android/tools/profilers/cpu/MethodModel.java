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

public class MethodModel {

  private String mNamespace;
  private String mName;
  private String mSignature;
  private String mFilename;
  private int mLineNumber;

  public MethodModel() {
  }

  public String getNameSpace() {
    return this.mNamespace;
  }

  public String getName() {
    return this.mName;
  }

  public void setName(String name) {
    this.mName = name;
  }

  public String getSignature() {
    return this.mSignature;
  }

  public void setSignature(String signature) {
    this.mSignature = signature;
  }

  public String getFilename() {
    return this.mFilename;
  }

  public void setFilename(String filename) {
    this.mFilename = filename;
  }

  public int getLineNumber() {
    return this.mLineNumber;
  }

  public void setLineNumber(int lineNumber) {
    this.mLineNumber = lineNumber;
  }

  public void setNamespace(String namespace) {
    this.mNamespace = namespace;
  }
}
