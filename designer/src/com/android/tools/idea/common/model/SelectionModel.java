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
package com.android.tools.idea.common.model;

import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.util.ListenerCollection;
import com.android.utils.ImmutableCollectors;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Represents a selection of components
 */
public class SelectionModel {
  @NotNull
  private ImmutableList<NlComponent> mySelection = ImmutableList.of();
  private NlComponent myPrimary;
  private final ListenerCollection<SelectionListener> myListeners = ListenerCollection.createWithDirectExecutor();
  private Map<NlComponent, SelectionHandles> myHandles;

  @NotNull
  public ImmutableList<NlComponent> getSelection() {
    return mySelection;
  }

  @Nullable
  public NlComponent getPrimary() {
    return myPrimary;
  }

  public void setSelection(@NotNull List<NlComponent> components) {
    setSelection(ImmutableList.copyOf(components), components.isEmpty() ? null : components.iterator().next());
  }

  public void setSelection(@NotNull List<NlComponent> components, @Nullable NlComponent primary) {
    if (components.equals(mySelection)) {
      return;
    }
    myHandles = null;
    mySelection = ImmutableList.copyOf(components);
    myPrimary = primary;
    notifySelectionChanged();
  }

  public void clear() {
    if (mySelection.isEmpty()) {
      return;
    }
    myHandles = null;
    mySelection = ImmutableList.of();
    myPrimary = null;
    notifySelectionChanged();
  }

  public void toggle(@NotNull NlComponent component) {
    ImmutableList<NlComponent> newSelection;
    NlComponent newPrimary;
    int size = mySelection.size();
    if (size == 0) {
      // Nothing was selected, just select the given component
      newSelection = ImmutableList.of(component);
      newPrimary = component;
    }
    else if (mySelection.contains(component)) {
      // The selection already contains the given component. Remove it
      if (size == 1) {
        newSelection = ImmutableList.of();
        newPrimary = null;
      }
      else {
        // Filter out the element
        newSelection = mySelection.stream()
          .filter(selection -> !selection.equals(component)).collect(ImmutableCollectors.toImmutableList());
        newPrimary = myPrimary == component ? null : myPrimary;
      }
    }
    else {
      // We need to add it
      ImmutableList.Builder<NlComponent> builder = ImmutableList.builder();
      builder.addAll(mySelection);
      builder.add(component);
      newSelection = builder.build();
      newPrimary = myPrimary;
    }
    setSelection(newSelection, newPrimary);
  }

  private void notifySelectionChanged() {
    myListeners.forEach(l -> l.selectionChanged(this, mySelection));

  }

  public void addListener(@NotNull SelectionListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(@Nullable SelectionListener listener) {
    myListeners.remove(listener);
  }

  public boolean isEmpty() {
    return mySelection.isEmpty();
  }

  @Nullable
  public SelectionHandle findHandle(@AndroidDpCoordinate int x,
                                    @AndroidDpCoordinate int y,
                                    @AndroidDpCoordinate int maxDistance,
                                    @NotNull DesignSurface surface) {
    if (myHandles == null) {
      return null;
    }

    for (SelectionHandles handles : myHandles.values()) {
      SelectionHandle handle = handles.findHandle(x, y, maxDistance, surface);
      if (handle != null) {
        return handle;
      }
    }

    return null;
  }

  @NotNull
  public SelectionHandles getHandles(@NotNull NlComponent component) {
    if (myHandles == null) {
      myHandles = Maps.newHashMap();
    }
    SelectionHandles handles = myHandles.get(component);
    if (handles == null) {
      handles = new SelectionHandles(component);
      myHandles.put(component, handles);
    }
    return handles;
  }

  /** Returns true if the given component is part of the selection */
  public boolean isSelected(@NotNull NlComponent component) {
    return mySelection.contains(component);
  }

  public ItemTransferable getTransferable(long modelId) {
    ImmutableList<DnDTransferComponent> components =
      mySelection.stream().map(component -> new DnDTransferComponent(component.getTagName(), component.getTag().getText(),
                                                                     NlComponentHelperKt.getW(component),
                                                                     NlComponentHelperKt.getH(component))).collect(
        ImmutableCollectors.toImmutableList());
    return new ItemTransferable(new DnDTransferItem(modelId, components));
  }
}
