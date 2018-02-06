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
import com.android.tools.adtui.ptable.PTableGroupItem;
import com.android.tools.adtui.ptable.PTableItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

public class LITableGroupItem extends PTableGroupItem {
  private final String myName;
  private final List<PTableItem> myChildren;

  private boolean isExpanded;

  public LITableGroupItem(@NotNull String key, @NotNull List<ViewProperty> properties) {
    super();
    myName = key;
    myChildren = properties.stream().map(prop -> new LITableItem(prop, this)).sorted().collect(Collectors.toList());
    isExpanded = false;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Nullable
  @Override
  public String getValue() {
    return null;
  }

  @Override
  public void setValue(@Nullable Object value) {
  }

  @Override
  public boolean hasChildren() {
    return true;
  }

  @Override
  public List<PTableItem> getChildren() {
    return myChildren;
  }

  @Override
  @NotNull
  public String getChildLabel(@NotNull PTableItem item) {
    return item.getName();
  }

  @Override
  public boolean isExpanded() {
    return isExpanded;
  }

  @Override
  public void setExpanded(boolean expanded) {
    isExpanded = expanded;
  }
}
