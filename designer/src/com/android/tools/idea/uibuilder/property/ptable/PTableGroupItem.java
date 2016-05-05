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
package com.android.tools.idea.uibuilder.property.ptable;

import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public abstract class PTableGroupItem extends PTableItem {
  private List<PTableItem> myItems;
  private boolean myExpanded;

  public void setChildren(@NotNull List<PTableItem> items) {
    myItems = items;
    for (PTableItem item : items) {
      item.setParent(this);
    }
  }

  public void addChild(@NotNull PTableItem item) {
    item.setParent(this);
    if (myItems == null) {
      myItems = Lists.newArrayList();
    }
    myItems.add(item);
  }

  @Override
  public List<PTableItem> getChildren() {
    return myItems == null ? Collections.emptyList() : myItems;
  }

  @Nullable
  public PTableItem getItemByName(@NotNull String propertyName) {
    for (PTableItem item : myItems) {
      if (item.getName().equals(propertyName)) {
        return item;
      }
    }
    return null;
  }

  @Override
  public boolean hasChildren() {
    return myItems != null && !myItems.isEmpty();
  }

  @Override
  public boolean isExpanded() {
    return myExpanded;
  }

  @Override
  public void setExpanded(boolean expanded) {
    myExpanded = expanded;
  }

  @Nullable
  @Override
  public String getValue() {
    throw new IllegalStateException();
  }

  @Nullable
  @Override
  public String getResolvedValue() {
    throw new IllegalStateException();
  }

  @Override
  public boolean isDefaultValue(@Nullable String value) {
    throw new IllegalStateException();
  }

  @Override
  public void setValue(@Nullable Object value) {
    throw new IllegalStateException();
  }
}
