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
package com.android.tools.adtui.chart.hchart;

public class MethodUsage {
  private String mNamespace;
  private String mName;
  private String mFilename;

  private long myExclusiveDuration;
  private double myExclusivePercentage;

  private long myInclusiveDuration;
  private double myInclusivePercentage;

  public double getInclusivePercentage() {
    return myInclusivePercentage;
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

  public void setExclusiveDuration(long exclusiveDuration) {
    myExclusiveDuration = exclusiveDuration;
  }

  public void setExclusivePercentage(double exclusivePercentage) {
    myExclusivePercentage = exclusivePercentage;
  }

  public void increaseInclusiveDuration(long duration) {
    myInclusiveDuration += duration;
  }

  public long getInclusiveDuration() {
    return myInclusiveDuration;
  }

  public void setInclusivePercentage(double inclusivePercentage) {
    myInclusivePercentage = inclusivePercentage;
  }

  public long getExclusiveDuration() {
    return myExclusiveDuration;
  }

  public double getExclusivePercentage() {
    return myExclusivePercentage;
  }
}
