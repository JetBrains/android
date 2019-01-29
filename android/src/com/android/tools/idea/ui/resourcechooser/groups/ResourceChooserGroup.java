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
package com.android.tools.idea.ui.resourcechooser.groups;

import com.android.resources.ResourceType;
import com.android.tools.idea.ui.resourcechooser.ResourceChooserItem;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

/**
 * Class that defines a group of {@link ResourceChooserItem}s. These elements will usually be displayed as a group with a common label to define the
 * group.
 */
public final class ResourceChooserGroup {
  @NotNull private final ImmutableList<ResourceChooserItem> myItems;
  @NotNull private final ResourceType myType;
  @NotNull private final String myGroupLabel;

  ResourceChooserGroup(@NotNull String label, @NotNull ResourceType type, @NotNull ImmutableList<ResourceChooserItem> items) {
    myGroupLabel = label;
    myItems = items;
    myType = type;
  }

  @NotNull
  public String getGroupLabel() {
    return myGroupLabel;
  }

  @NotNull
  public ResourceType getType() {
    return myType;
  }

  @NotNull
  public ImmutableList<ResourceChooserItem> getItems() {
    return myItems;
  }

  public boolean isEmpty() {
    return myItems.isEmpty();
  }
}