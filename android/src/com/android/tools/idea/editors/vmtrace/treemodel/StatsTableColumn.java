/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.editors.vmtrace.treemodel;

public enum StatsTableColumn {
  NAME("Name", "ClassNameForASample.somewhatLongMethodNameSomewhatLongMethodName"),
  INVOCATION_COUNT("Invocation Count", "123456789"),
  INCLUSIVE_TIME("Inclusive Time (\u00b5s)", "123,456,789,012"), // \u00b5 = unicode for micro (as in microseconds)
  EXCLUSIVE_TIME("Exclusive Time (\u00b5s)", "123,456,789,012");

  private final String myTitle;
  private final String mySampleText;

  StatsTableColumn(String title, String sampleText) {
    myTitle = title;
    mySampleText = sampleText;
  }

  @Override
  public String toString() {
    return myTitle;
  }

  public String getSampleText() {
    return mySampleText;
  }

  public int getColumnIndex() {
    return ordinal();
  }

  public static StatsTableColumn fromColumnIndex(int column) {
    if (column >= StatsTableColumn.values().length) {
      throw new IllegalArgumentException();
    }

    return StatsTableColumn.values()[column];
  }
}
