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
package com.android.tools.idea.structure.dialog;

import com.google.common.collect.Lists;
import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ProjectStructureItemGroup {
  @NotNull private final String myGroupName;
  @NotNull private final List<? extends Configurable> myItems;

  public ProjectStructureItemGroup(@NotNull String groupName, @NotNull Configurable...items) {
    this(groupName, Lists.newArrayList(items));
  }

  public ProjectStructureItemGroup(@NotNull String groupName, @NotNull  List<? extends Configurable> items) {
    myGroupName = groupName;
    myItems = items;
  }

  @NotNull
  public String getGroupName() {
    return myGroupName;
  }

  @NotNull
  public List<? extends Configurable> getItems() {
    return myItems;
  }
}
