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
package com.android.tools.idea.uibuilder.palette;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class NlPaletteGroup {
  @NotNull private final String myTitle;
  @NotNull private final List<NlPaletteItem> myItems;

  public NlPaletteGroup(@NotNull String title) {
    myTitle = title;
    myItems = new ArrayList<NlPaletteItem>();
  }

  public void add(@NotNull NlPaletteItem item) {
    myItems.add(item);
  }

  @NotNull
  public String getTitle() {
    return myTitle;
  }

  @NotNull
  public List<NlPaletteItem> getItems() {
    return myItems;
  }
}
