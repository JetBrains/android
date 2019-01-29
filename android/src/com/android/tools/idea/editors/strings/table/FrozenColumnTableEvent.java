/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.editors.strings.table;

import java.awt.Component;
import java.awt.Point;
import java.util.EventObject;
import org.jetbrains.annotations.NotNull;

public final class FrozenColumnTableEvent extends EventObject {
  private final int myViewRowIndex;
  private final int myViewColumnIndex;
  private final Point myPoint;
  private final Component mySubcomponent;

  FrozenColumnTableEvent(@NotNull FrozenColumnTable source,
                         int viewRowIndex,
                         int viewColumnIndex,
                         @NotNull Point point,
                         @NotNull Component subcomponent) {
    super(source);

    myViewRowIndex = viewRowIndex;
    myViewColumnIndex = viewColumnIndex;
    myPoint = point;
    mySubcomponent = subcomponent;
  }

  @Override
  public FrozenColumnTable getSource() {
    return (FrozenColumnTable)super.getSource();
  }

  public int getViewRowIndex() {
    return myViewRowIndex;
  }

  public int getModelRowIndex() {
    return getSource().convertRowIndexToModel(myViewRowIndex);
  }

  public int getViewColumnIndex() {
    return myViewColumnIndex;
  }

  public int getModelColumnIndex() {
    return getSource().convertColumnIndexToModel(myViewColumnIndex);
  }

  @NotNull
  public Point getPoint() {
    return myPoint;
  }

  @NotNull
  public Component getSubcomponent() {
    return mySubcomponent;
  }
}
