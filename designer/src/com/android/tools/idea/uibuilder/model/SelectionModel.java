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
package com.android.tools.idea.uibuilder.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.idea.uibuilder.api.DragType;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.awt.datatransfer.Transferable;
import java.util.*;

/**
 * Represents a selection of components
 */
public class SelectionModel {
  private List<NlComponent> mySelection = Collections.emptyList();
  private NlComponent myPrimary;
  private List<SelectionListener> myListeners;
  private Map<NlComponent, SelectionHandles> myHandles;

  @NotNull
  public List<NlComponent> getSelection() {
    return Collections.unmodifiableList(mySelection);
  }

  @Nullable
  public NlComponent getPrimary() {
    return myPrimary;
  }

  public void setSelection(@NotNull Collection<NlComponent> components) {
    setSelection(components, components.isEmpty() ? null : components.iterator().next());
  }

  public void setSelection(@NotNull Collection<NlComponent> components, @Nullable NlComponent primary) {
    if (components.equals(mySelection)) {
      return;
    }
    myHandles = null;
    mySelection = Lists.newArrayList(components);
    myPrimary = primary;
    updateListeners();
  }

  public void clear() {
    if (mySelection == null || mySelection.isEmpty()) {
      return;
    }
    myHandles = null;
    mySelection = Collections.emptyList();
    myPrimary = null;
    updateListeners();
  }

  public void toggle(@NotNull NlComponent component) {
    myHandles = null;
    if (mySelection.contains(component)) {
      mySelection.remove(component);
      if (myPrimary == component) {
        myPrimary = null;
      }
    } else if (mySelection.isEmpty()) { // read-only Collections.emptyList
      mySelection = Lists.newArrayList(component);
      myPrimary = component;
    } else {
      mySelection.add(component);
    }
    updateListeners();
  }

  private void updateListeners() {
    if (myListeners != null) {
      List<NlComponent> selection = getSelection();
      for (SelectionListener listener : myListeners) {
        listener.selectionChanged(this, selection);
      }
    }
  }

  public void addListener(@NotNull SelectionListener listener) {
    if (myListeners == null) {
      myListeners = Lists.newArrayList();
    } else {
      myListeners.remove(listener); // ensure single registration
    }
    myListeners.add(listener);
  }

  public void removeListener(@NotNull SelectionListener listener) {
    if (myListeners != null) {
      myListeners.remove(listener);
    }
  }

  public boolean isEmpty() {
    return mySelection.isEmpty();
  }

  @Nullable
  public SelectionHandle findHandle(@AndroidCoordinate int x, @AndroidCoordinate int y, @AndroidCoordinate int maxDistance) {
    if (myHandles == null) {
      return null;
    }
    for (SelectionHandles handles : myHandles.values()) {
      SelectionHandle handle = handles.findHandle(x, y, maxDistance);
      if (handle != null) {
        return handle;
      }
    }

    return null;
  }

  @Nullable
  public NlComponent findComponent(@AndroidCoordinate int x, @AndroidCoordinate int y) {
    for (NlComponent component : mySelection) {
      if (component.x <= x && component.y <= y && component.x + component.w >= x && component.y + component.h >= y) {
        return component;
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

  public void selectAll(@NotNull NlModel model) {
    List<NlComponent> all = Lists.newArrayList();
    for (NlComponent component : model.getComponents()) {
      addComponent(all, component);
    }
    setSelection(all);
  }

  private static void addComponent(List<NlComponent> all, NlComponent component) {
    all.add(component);
    for (NlComponent child : component.getChildren()) {
      addComponent(all, child);
    }
  }

  /** Returns true if the given component is part of the selection */
  public boolean isSelected(@NotNull NlComponent component) {
    return mySelection.contains(component);
  }

  public Transferable getTransferable(long modelId) {
    List<DnDTransferComponent> components = new ArrayList<DnDTransferComponent>(mySelection.size());
    for (NlComponent component : mySelection) {
      components.add(new DnDTransferComponent(component.getTagName(), component.getTag().getText(), component.w, component.h));
    }
    return new ItemTransferable(new DnDTransferItem(modelId, components));
  }
}
