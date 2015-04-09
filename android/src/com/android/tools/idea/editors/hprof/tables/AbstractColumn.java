/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.hprof.tables;

import org.jetbrains.annotations.NotNull;

public abstract class AbstractColumn<T> {
  @NotNull private String myColumnName;
  @NotNull private Class<T> myClass;
  private int myHeaderJustification;
  private int myColumnWidth;
  private boolean myEnabled;

  public AbstractColumn(@NotNull String columnName,
                        @NotNull Class<T> classType,
                        int headerJustification,
                        int relativeWidth,
                        boolean enabled) {
    myColumnName = columnName;
    myClass = classType;
    myHeaderJustification = headerJustification;
    myColumnWidth = relativeWidth;
    myEnabled = enabled;
  }

  public boolean getEnabled() {
    return myEnabled;
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  @NotNull
  public Class<T> getColumnClass() {
    return myClass;
  }

  @NotNull
  public String getColumnName() {
    return myColumnName;
  }

  public int getHeaderJustification() {
    return myHeaderJustification;
  }

  public int getColumnWidth() {
    return myColumnWidth;
  }
}
