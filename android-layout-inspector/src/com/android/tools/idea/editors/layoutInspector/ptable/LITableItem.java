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
package com.android.tools.idea.editors.layoutInspector.ptable;

import com.android.layoutinspector.model.ViewProperty;
import com.android.tools.adtui.ptable.PTableItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LITableItem extends PTableItem implements Comparable<PTableItem> {

  private final String myValue;
  private final String myName;

  public LITableItem(@NotNull ViewProperty prop, @NotNull PTableItem parent) {
    myName = prop.fullName.startsWith(parent.getName()) ? prop.fullName.substring(parent.getName().length() + 1) : prop.fullName;
    myValue = prop.getValue();
    setParent(parent);
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Nullable
  @Override
  public String getValue() {
    return myValue;
  }

  @Override
  public void setValue(@Nullable Object value) {

  }

  @Override
  public boolean isDefaultValue(@Nullable String value) {
    return true;
  }

  @Override
  public int compareTo(@NotNull PTableItem other) {
    return myName.compareTo(other.getName());
  }
}
