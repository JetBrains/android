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

import com.android.tools.idea.util.ListenerCollection;
import com.android.utils.ImmutableCollectors;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a selection of components
 */
public class SelectionModel {
  @NotNull
  private ImmutableList<NlComponent> mySelection = ImmutableList.of();
  private NlComponent myPrimary;
  private final ListenerCollection<SelectionListener> myListeners = ListenerCollection.createWithDirectExecutor();
  @Nullable
  Object mySecondarySelection;

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
    //noinspection EqualsBetweenInconvertibleTypes   This currentlly erroneously shows on this line during psq
    if (components.equals(mySelection)) {
      return;
    }
    mySelection = ImmutableList.copyOf(components);
    mySecondarySelection = null;
    myPrimary = primary;
    notifySelectionChanged();
  }

  public void clear() {
    if (mySelection.isEmpty()) {
      return;
    }
    mySelection = ImmutableList.of();
    myPrimary = null;

    mySecondarySelection = null;
    notifySelectionChanged();
  }

  public void clearSecondary() {
    if (mySecondarySelection == null) {
      return;
    }
    mySecondarySelection = null;
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

    mySecondarySelection = null;
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

  /**
   * Returns true if the given component is part of the selection
   */
  public boolean isSelected(@NotNull NlComponent component) {
    return mySelection.contains(component);
  }

  /**
   * Set the secondary selection.
   * Secondary selections must be associated with a NlComponent which is considered to be selected
   *
   * @param component the parent component of the secondary selection
   * @param secondary the secondary selection the object can be of any type but should implement equals
   */
  public void setSecondarySelection(NlComponent component, Object secondary) {
    if (component == null) {
      mySelection = ImmutableList.of();
      myPrimary = null;
      mySecondarySelection = null;
    }
    else {
      mySecondarySelection = secondary;
      mySelection = ImmutableList.of(component);
      myPrimary = component;
    }
    notifySelectionChanged();
  }

  /**
   * This returns the secondary selection. Users of this api should check the type and treat it as null if the type is not known.
   *
   * @return the secondary selection
   */
  public Object getSecondarySelection() {
    return mySecondarySelection;
  }

  /**
   * Test if the object is the secondary selection.
   * Object is the selection if object == secondary || object.equals(secondary)
   *
   * @param object
   * @return
   */
  public boolean isSecondarySelected(Object object) {
    if (mySecondarySelection == object) return true;
    return mySecondarySelection != null && mySecondarySelection.equals(object);
  }
}
