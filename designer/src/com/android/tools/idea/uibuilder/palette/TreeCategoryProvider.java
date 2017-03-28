/*
 * Copyright (C) 2016 The Android Open Source Project
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

import javax.swing.*;

public class TreeCategoryProvider extends DefaultListModel<Palette.Group> {
  static final Palette.Group ALL = new Palette.Group("All");

  public TreeCategoryProvider(@NotNull Palette palette) {
    addElement(ALL);

    palette.accept(new Palette.Visitor() {
      @Override
      public void visit(@NotNull Palette.Item item) {}

      @Override
      public void visit(@NotNull Palette.Group group) {
        addElement(group);
      }
    });
  }
}
