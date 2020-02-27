/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.adtui.model;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;
import javax.swing.AbstractListModel;
import javax.swing.ComboBoxModel;
import org.jetbrains.annotations.NotNull;

/**
 * Combox box model that updates the values based on a predicate. The predicate gets
 * evaluated on construction and when refresh is called. When the model is refreshed
 * the {@link ComboBoxModel::fireContentsChanged} event is triggered.
 *
 * @param E is a type that is used for elements of {@link ComboBoxModel}
 */
public final class ConditionalEnumComboBoxModel<E extends Enum<E>> extends AbstractListModel<E> implements ComboBoxModel<E> {
  private List<E> myList;
  private E mySelected;
  @NotNull Class<E> myEnumClass;
  @NotNull private final Predicate<E> myConditional;

  public ConditionalEnumComboBoxModel(@NotNull Class<E> enumClass, @NotNull Predicate<E> conditional) {
    myConditional = conditional;
    myEnumClass = enumClass;
    update();
  }

  @NotNull
  private List<E> createEnumSet() {
    EnumSet<E> set = EnumSet.allOf(myEnumClass);
    set.removeIf(myConditional.negate());
    return new ArrayList<>(set);
  }

  /**
   * Function to trigger calling {@link  myConditional} and rebuild the elements of the {@link ComboBoxModel}
   */
  public void update() {
    myList = createEnumSet();
    mySelected = myList.isEmpty() ? null : myList.get(0);
    fireContentsChanged(this, 0, getSize());
  }

  @Override
  public int getSize() {
    return myList.size();
  }

  @Override
  public E getElementAt(int index) {
    return myList.get(index);
  }

  @Override
  public void setSelectedItem(Object item) {
    @SuppressWarnings("unchecked") E e = (E)item;
    setSelectedItem(e);
  }

  public void setSelectedItem(E item) {
    mySelected = item;
    fireContentsChanged(this, 0, getSize());
  }

  @Override
  public E getSelectedItem() {
    return mySelected;
  }
}
