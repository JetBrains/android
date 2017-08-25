/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.palette2;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.common.model.NlLayoutType;
import com.android.tools.idea.uibuilder.palette.NlPaletteModel;
import com.android.tools.idea.uibuilder.palette.Palette;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Palette UI will interact exclusively with this data model.
 *
 * This model is responsible for loading data for the wanted palette
 * and generating updates to 2 list models: category list and item list.
 */
public class DataModel {
  @VisibleForTesting
  public static final Palette.Group COMMON = new Palette.Group("Common");
  @VisibleForTesting
  public static final Palette.Group RESULTS = new Palette.Group("All Results");

  private final CategoryListModel myListModel;
  private final ItemListModel myItemModel;
  private final ItemFilter myItemFilter;
  private final DependencyManager myDependencyManager;
  private NlLayoutType myLayoutType;
  private Palette myPalette;

  public DataModel(@NotNull DependencyManager dependencyManager) {
    myListModel = new CategoryListModel();
    myItemModel = new ItemListModel();
    myItemFilter = new ItemFilter();
    myDependencyManager = dependencyManager;
    myLayoutType = NlLayoutType.UNKNOWN;
    myPalette = Palette.EMPTY;
  }

  @NotNull
  public CategoryListModel getCategoryListModel() {
    return myListModel;
  }

  @NotNull
  public ItemListModel getItemListModel() {
    return myItemModel;
  }

  public void setLayoutType(@NotNull AndroidFacet facet, @NotNull NlLayoutType layoutType) {
    if (myLayoutType.equals(layoutType)) {
      return;
    }
    NlPaletteModel paletteModel = NlPaletteModel.get(facet);
    myPalette = paletteModel.getPalette(layoutType);
    myLayoutType = layoutType;
    myDependencyManager.setPalette(myPalette, facet.getModule());
    update();
  }

  public void setFilterPattern(@NotNull String pattern) {
    if (myItemFilter.getPattern().equals(pattern)) {
      return;
    }
    myItemFilter.setPattern(pattern);
    update();
  }

  @NotNull
  public String getFilterPattern() {
    return myItemFilter.getPattern();
  }

  public int getMatchCount() {
    return myListModel.getMatchCountAt(0);
  }

  public void categorySelectionChanged(@NotNull Palette.Group selectedGroup) {
    if (myItemFilter.getPattern().isEmpty()) {
      createUnFilteredItems(selectedGroup);
    }
    else {
      createFilteredItems(selectedGroup);
    }
  }

  private void update() {
    if (myItemFilter.getPattern().isEmpty()) {
      createUnFilteredGroups();
    }
    else {
      createFilteredGroups();
    }
  }

  private void createUnFilteredGroups() {
    List<Palette.Group> groups = new ArrayList<>();

    groups.add(COMMON);
    myPalette.accept(new Palette.Visitor() {
      @Override
      public void visit(@NotNull Palette.Item item) {}

      @Override
      public void visit(@NotNull Palette.Group group) {
        groups.add(group);
      }
    });
    updateCategoryModel(groups, Collections.emptyList());
  }

  private void createFilteredGroups() {
    List<Palette.Group> groups = new ArrayList<>();
    List<Integer> matchCounts = new ArrayList<>();

    groups.add(RESULTS);
    matchCounts.add(0); // Updated later

    myPalette.accept(new Palette.Visitor() {
      private Palette.Group currentGroup = RESULTS;
      private int matchCount;

      @Override
      public void visit(@NotNull Palette.Group group) {
        currentGroup = group;
        matchCount = 0;
      }

      @Override
      public void visit(@NotNull Palette.Item item) {
        if (currentGroup.equals(item.getParent()) && myItemFilter.value(item)) {
          matchCount++;
        }
      }

      @Override
      public void visitAfter(@NotNull Palette.Group group) {
        if (matchCount > 0) {
          groups.add(group);
          matchCounts.add(matchCount);
          matchCounts.set(0, matchCounts.get(0) + matchCount);
        }
      }
    });
    updateCategoryModel(groups, matchCounts);
  }

  private void updateCategoryModel(@NotNull List<Palette.Group> groups, @NotNull List<Integer> matchCounts) {
    UIUtil.invokeLaterIfNeeded(() -> myListModel.update(groups, matchCounts));
  }

  private void createUnFilteredItems(@NotNull Palette.Group selectedGroup) {
    List<Palette.Item> items = new ArrayList<>();
    if (selectedGroup != COMMON && selectedGroup != RESULTS) {
      selectedGroup.accept(items::add);
    }
    else if (myListModel.getSize() <= 1) {
      myPalette.accept(items::add);
    }
    else {
      myPalette.accept(item -> {
        if (item.isSuggested()) {
          items.add(item);
        }
      });
    }
    updateItemModel(items);
  }

  private void createFilteredItems(@NotNull Palette.Group selectedGroup) {
    List<Palette.Item> items = new ArrayList<>();
    Palette.Visitor visitor = item -> {
      if (myItemFilter.value(item)) {
        items.add(item);
      }
    };
    if (selectedGroup != COMMON && selectedGroup != RESULTS) {
      selectedGroup.accept(visitor);
    }
    else {
      myPalette.accept(visitor);
    }
    updateItemModel(items);
  }

  private void updateItemModel(@NotNull List<Palette.Item> items) {
    UIUtil.invokeLaterIfNeeded(() -> myItemModel.update(items));
  }
}
