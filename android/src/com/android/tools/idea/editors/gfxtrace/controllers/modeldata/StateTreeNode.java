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
package com.android.tools.idea.editors.gfxtrace.controllers.modeldata;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;

public class StateTreeNode extends DefaultMutableTreeNode {
  @NotNull private String myName;
  @Nullable private Object myValue;

  public StateTreeNode(@NotNull String name, @Nullable Object value) {
    myName = name;
    myValue = value;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Nullable
  public Object getValue() {
    return myValue;
  }

  @NotNull
  public String getValueString() {
    if (myValue == null) {
      return "";
    }
    return myValue.toString();
  }

  @Override
  @NotNull
  public String toString() {
    if (myValue != null) {
      return myName + ": " + myValue.toString();
    }
    else {
      return myName;
    }
  }

  public boolean hasChildren() {
    return getChildCount() > 0;
  }
}
