/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.SdkConstants.ANDROIDX_PKG;
import static com.android.SdkConstants.ANDROID_SUPPORT_PKG_PREFIX;
import static com.android.SdkConstants.BUTTON;
import static com.android.SdkConstants.FRAGMENT_CONTAINER_VIEW;
import static com.android.SdkConstants.IMAGE_VIEW;
import static com.android.SdkConstants.MATERIAL2_PKG;
import static com.android.AndroidXConstants.RECYCLER_VIEW;
import static com.android.SdkConstants.SCROLL_VIEW;
import static com.android.SdkConstants.SWITCH;
import static com.android.SdkConstants.TEXT_VIEW;

import com.android.SdkConstants.PreferenceTags;
import com.android.annotations.concurrency.GuardedBy;
import com.android.annotations.concurrency.Slow;
import com.android.annotations.concurrency.UiThread;
import com.android.tools.idea.common.type.DesignerEditorFileType;
import com.android.tools.idea.uibuilder.type.LayoutEditorFileType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.ui.UIUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/**
 * The Palette UI will interact exclusively with this data model.
 *
 * This model is responsible for loading data for the wanted palette
 * and generating updates to 2 list models: category list and item list.
 */
@UiThread
public class DataModel implements Disposable {
  public static final Palette.Group COMMON = new Palette.Group("Common");
  public static final Palette.Group RESULTS = new Palette.Group("All Results");
  @VisibleForTesting
  public static final String FAVORITE_ITEMS = "Palette.Favorite.items";

  private final CategoryListModel myListModel;
  private final ItemListModel myItemModel;
  private final Condition<Palette.Item> myFilterCondition;
  private final PatternFilter myFilterPattern;
  private final DependencyManager myDependencyManager;
  private final List<String> myFavoriteItems;
  private final NlPaletteModel.UpdateListener myUpdateListener;
  private NlPaletteModel myPaletteModel;
  private LayoutEditorFileType myLayoutType;
  private final ReentrantReadWriteLock myPaletteLock = new ReentrantReadWriteLock();
  @GuardedBy("myPaletteLock")
  private Palette myPalette;
  private Palette.Group myCurrentSelectedGroup;
  private boolean myDisposed;

  public DataModel(@NotNull Disposable parent, @NotNull DependencyManager dependencyManager) {
    Disposer.register(parent, this);
    myListModel = new CategoryListModel();
    myItemModel = new ItemListModel();
    myFavoriteItems = readFavoriteItems();
    myDependencyManager = dependencyManager;
    myPalette = Palette.EMPTY;
    myCurrentSelectedGroup = COMMON;
    myUpdateListener = this::update;
    myDependencyManager.addDependencyChangeListener(this::onDependenciesChanged);

    myFilterPattern = new PatternFilter();
    // This filter will hide or display the androidx.* components in the palette depending on whether the
    // project supports the androidx libraries.
    Condition<Palette.Item> androidxFilter = item -> {
      String tagName = item.getTagName();
      boolean isAndroidxTag = tagName.startsWith(ANDROIDX_PKG) || tagName.startsWith(MATERIAL2_PKG);
      boolean isOldSupportLibTag = !isAndroidxTag && tagName.startsWith(ANDROID_SUPPORT_PKG_PREFIX);
      if (!isAndroidxTag && !isOldSupportLibTag) {
        return true;
      }

      return myDependencyManager.useAndroidXDependencies() ? isAndroidxTag : isOldSupportLibTag;
    };
    myFilterCondition = Conditions.and(androidxFilter, myFilterPattern);
  }

  @NotNull
  public CategoryListModel getCategoryListModel() {
    return myListModel;
  }

  @NotNull
  public ItemListModel getItemListModel() {
    return myItemModel;
  }

  /**
   * This method changes the {@link LayoutEditorFileType} for this palette. If different to the current one, the model will automatically
   * update. This method is asynchronous since it executes potentially blocking operations.
   * When the returned {@link CompletableFuture} completes, the {@link DataModel} will be fully up-to-date.
   */
  @NotNull
  public CompletableFuture<Void> setLayoutTypeAsync(@NotNull AndroidFacet facet, @NotNull LayoutEditorFileType layoutType) {
    NlPaletteModel paletteModel = NlPaletteModel.get(facet);
    if (layoutType.equals(myLayoutType) && paletteModel == myPaletteModel) {
      return CompletableFuture.completedFuture(null);
    }

    if (myPaletteModel != null) {
      myPaletteModel.removeUpdateListener(myUpdateListener);
    }
    myLayoutType = layoutType;
    myPaletteModel = paletteModel;
    myPaletteModel.addUpdateListener(myUpdateListener);

    return CompletableFuture.runAsync(() -> {
      Palette palette = paletteModel.getPalette(layoutType);
      myPaletteLock.writeLock().lock();
      try {
        myPalette = palette;
        myDependencyManager.setPalette(myPalette, facet.getModule());
      }
      finally {
        myPaletteLock.writeLock().unlock();
      }
    }, AppExecutorUtil.getAppExecutorService())
    .thenRunAsync(this::update, EdtExecutorService.getInstance());
  }

  @Override
  public void dispose() {
    if (myPaletteModel != null) {
      myPaletteModel.removeUpdateListener(myUpdateListener);
    }
    myDisposed = true;
  }

  public void setFilterPattern(@NotNull String pattern) {
    if (myFilterPattern.setPattern(pattern)) {
      update();
    }
  }

  public boolean hasFilterPattern() {
    return myFilterPattern.hasPattern();
  }

  public int getMatchCount() {
    return myListModel.getMatchCountAt(0);
  }

  public void categorySelectionChanged(@NotNull Palette.Group selectedGroup) {
    createFilteredItems(selectedGroup);
    myCurrentSelectedGroup = selectedGroup;
  }

  public boolean isFavoriteItem(@NotNull Palette.Item item) {
    return myFavoriteItems.contains(item.getId());
  }

  public void addFavoriteItem(@NotNull Palette.Item item) {
    if (myFavoriteItems.contains(item.getId())) {
      return;
    }
    myFavoriteItems.add(item.getId());
    PropertiesComponent.getInstance().setList(FAVORITE_ITEMS, myFavoriteItems);
    update();
  }

  public void removeFavoriteItem(@NotNull Palette.Item item) {
    if (!myFavoriteItems.contains(item.getId())) {
      return;
    }
    myFavoriteItems.remove(item.getId());
    PropertiesComponent.getInstance().setList(FAVORITE_ITEMS, myFavoriteItems);
    update();
    if (myCurrentSelectedGroup == COMMON) {
      createFilteredItems(COMMON);
    }
  }

  @NotNull
  @VisibleForTesting
  Palette getPalette() {
    myPaletteLock.readLock().lock();
    try {
      return myPalette;
    } finally {
      myPaletteLock.readLock().unlock();
    }
  }

  @NotNull
  private static List<String> readFavoriteItems() {
    List<String> favorites = PropertiesComponent.getInstance().getList(FAVORITE_ITEMS);
    if (favorites == null) {
      return Lists.newArrayList(
        TEXT_VIEW,
        BUTTON,
        IMAGE_VIEW,
        RECYCLER_VIEW.oldName(),
        RECYCLER_VIEW.newName(),
        FRAGMENT_CONTAINER_VIEW,
        SCROLL_VIEW,
        SWITCH,
        PreferenceTags.CHECK_BOX_PREFERENCE,
        PreferenceTags.EDIT_TEXT_PREFERENCE,
        PreferenceTags.SWITCH_PREFERENCE,
        PreferenceTags.PREFERENCE_CATEGORY);
    }
    return favorites;
  }

  @Slow
  private void update(@NotNull NlPaletteModel paletteModel, @NotNull DesignerEditorFileType layoutType) {
    //noinspection UnstableApiUsage
    ApplicationManager.getApplication().assertIsNonDispatchThread();

    if (myPaletteModel != paletteModel || layoutType != myLayoutType || myDisposed || Disposer.isDisposed(paletteModel)) {
      return;
    }
    myPaletteLock.writeLock().lock();
    try {
      myPalette = paletteModel.getPalette(myLayoutType);
      myDependencyManager.setPalette(myPalette, paletteModel.getModule());
    } finally {
      myPaletteLock.writeLock().unlock();
    }
    ApplicationManager.getApplication().invokeLater(this::update);
  }

  @UiThread
  private void update() {
    ThreadingAssertions.assertEventDispatchThread();

    assert myLayoutType != null;
    boolean isUserSearch = myFilterPattern.hasPattern();
    List<Palette.Group> groups = new ArrayList<>();
    List<Integer> matchCounts = isUserSearch ? new ArrayList<>() : Collections.emptyList();

    groups.add(isUserSearch ? RESULTS : COMMON);

    if (isUserSearch) {
      matchCounts.add(0); // Updated later
    }

    getPalette().accept(new Palette.Visitor() {
      private Palette.Group currentGroup = isUserSearch ? RESULTS : COMMON;
      private int matchCount;

      @Override
      public void visit(@NotNull Palette.Group group) {
        currentGroup = group;
        matchCount = 0;
      }

      @Override
      public void visit(@NotNull Palette.Item item) {
        if (currentGroup.equals(item.getParent()) && myFilterCondition.value(item)) {
          matchCount++;
        }
      }

      @Override
      public void visitAfter(@NotNull Palette.Group group) {
        if (matchCount > 0) {
          groups.add(group);
          if (isUserSearch) {
            matchCounts.add(matchCount);
            matchCounts.set(0, matchCounts.get(0) + matchCount);
          }
        }
      }
    });

    myListModel.update(groups, matchCounts);
  }

  /**
   * The dependencies have changed so the items might need to be re-filtered.
   */
  private void onDependenciesChanged() {
    update();
    categorySelectionChanged(myCurrentSelectedGroup);
  }

  private void createFilteredItems(@NotNull Palette.Group selectedGroup) {
    List<Palette.Item> items = new ArrayList<>();
    Palette.Visitor visitor = item -> {
      if (myFilterCondition.value(item)) {
        items.add(item);
      }
    };

    if (selectedGroup != COMMON && selectedGroup != RESULTS) {
      selectedGroup.accept(visitor);
    }
    else if (myListModel.getSize() <= 1 || selectedGroup == RESULTS) {
      getPalette().accept(visitor);
    }
    else {
      Palette palette = getPalette();
      for (String id : myFavoriteItems) {
        Palette.Item item = palette.getItemById(id);
        if (item != null) {
          visitor.visit(item);
        }
      }
    }

    updateItemModel(items);
  }

  private void updateItemModel(@NotNull List<Palette.Item> items) {
    UIUtil.invokeLaterIfNeeded(() -> myItemModel.update(items));
  }
}
