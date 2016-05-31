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

package com.android.tools.adtui.visual.flamegraph;

public class MethodUsage {

  private String mNamespace;
  private String mName;
  private String mFilename;
  private int invocationCounter;
  private float mPercentage;

  public MethodUsage() {
  }

  public String getNameSpace() {
    return this.mNamespace;
  }

  public String getName() {
    return this.mName;
  }

  public String getFilename() {
    return this.mFilename;
  }

  public void setNamespace(String namespace) {
    this.mNamespace = namespace;
  }

  public void setName(String name) {
    this.mName = name;
  }

  public void setFilename(String filename) {
    this.mFilename = filename;
  }

  public int getInvocationCount() {
    return invocationCounter;
  }

  public void setInvocationCount(int count) {
    this.invocationCounter = count;
  }

  public void incInvocationCounter() {
    invocationCounter++;
  }

  public float getPercentage() {
    return mPercentage;
  }

  public void setPercentage(float percentage) {
    this.mPercentage = percentage;
  }
}
