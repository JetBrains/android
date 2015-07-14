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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Represents a selection of components
 */
public class SelectionModel {
  private List<NlComponent> mySelection = Collections.emptyList();
  private NlComponent myPrimary;
  private List<ChangeListener> myListeners;
  private Map<NlComponent, SelectionHandles> myHandles;

  @NonNull
  public Iterable<NlComponent> getSelection() {
    return mySelection;
  }

  @Nullable
  public NlComponent getPrimary() {
    return myPrimary;
  }

  public void setSelection(@NonNull Collection<NlComponent> components) {
    setSelection(components, components.isEmpty() ? null : components.iterator().next());
  }

  public void setSelection(@NonNull Collection<NlComponent> components, @Nullable NlComponent primary) {
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

  public void toggle(@NonNull NlComponent component) {
    myHandles = null;
    if (mySelection.contains(component)) {
      mySelection.remove(component);
      if (myPrimary == component) {
        myPrimary = null;
      }
    } else if (mySelection.isEmpty()) { // read-only Collections.emptyList
      mySelection = Lists.newArrayList(component);
    } else {
      mySelection.add(component);
    }
    updateListeners();
  }

  private void updateListeners() {
    if (myListeners != null) {
      for (ChangeListener listener : myListeners) {
        listener.stateChanged(new ChangeEvent(this));
      }
    }
  }

  public void addListener(@NonNull ChangeListener listener) {
    if (myListeners == null) {
      myListeners = Lists.newArrayList();
    } else {
      myListeners.remove(listener); // ensure single registration
    }
    myListeners.add(listener);
  }

  public void removeListener(@NonNull ChangeListener listener) {
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

  @NonNull
  public SelectionHandles getHandles(@NonNull NlComponent component) {
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

  public void selectAll(@NonNull NlModel model) {
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
}
