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
package com.android.tools.profilers;

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.AspectObserver;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JComboBox;

public class JComboBoxView<T, A extends Enum<A>> implements ActionListener {

  private final Supplier<List<T>> myList;
  private final Supplier<T> myGetFromModel;
  private final Consumer<T> mySet;
  private final JComboBox<T> myCombo;
  private final AspectModel<A> myObject;
  private final A myAspect;
  private final AspectObserver myAspectObserver;
  private boolean myIgnoreEvents;
  private final Supplier<T> myGetFromList;

  public JComboBoxView(JComboBox<T> combo, AspectModel<A> object, A aspect, Supplier<List<T>> list, Supplier<T> getFromModel, Consumer<T> set) {
    this(combo, object, aspect, list, getFromModel, set, getFromModel);
  }

  /**
   * @param list Supplies the list of valid states
   * @param getFromModel Supplies the current state of the underlying model.
   *                     This is used to check for the actual current state when handling
   *                     events, to avoid repeatedly setting to the same state.
   * @param set Sets the current state of the underlying model
   * @param getFromList Supplies the current state's "home"-state in what returned by `list`.
   *                    Defaulting to `getFromModel` gives the reasonable behavior.
   *                    This is for use when `get` may return a state not in `list`.
   *                    If the user is ever in a state not in `list`, the combobox will
   *                    display the current item as `getFromList`. Then if the user ever
   *                    re-selects this same item, they'll transition to `getFromList`.
   *                    This function is used when re-populating the menu and setting the
   *                    current selected item, which may be related, but not exactly,
   *                    the current state.
   */
  public JComboBoxView(JComboBox<T> combo,
                       AspectModel<A> object,
                       A aspect,
                       Supplier<List<T>> list,
                       Supplier<T> getFromModel,
                       Consumer<T> set,
                       Supplier<T> getFromList) {
    myAspectObserver = new AspectObserver();
    myList = list;
    myGetFromModel = getFromModel;
    mySet = set;
    myCombo = combo;
    myObject = object;
    myAspect = aspect;
    myGetFromList = getFromList;
  }

  public void bind() {
    myCombo.addActionListener(this);
    myObject.addDependency(myAspectObserver).onChange(myAspect, this::changed);
    changed();
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    T item = (T) myCombo.getSelectedItem();
    if (item != null && !myIgnoreEvents && !Objects.equals(myGetFromModel.get(), item)) {
      mySet.accept(item);
    }
  }

  private void changed() {
    List<T> list = myList.get();
    T selected = myGetFromList.get();

    myIgnoreEvents = true;
    myCombo.removeAllItems();
    if (list.isEmpty()) {
      // We add a null item so combobox can get its layout updated and width set properly,
      // for the empty message (e.g., "No Connected Devices").
      myCombo.addItem(null);
    } else {
      for (T t : list) {
        myCombo.addItem(t);
      }
    }
    myCombo.setSelectedItem(selected);
    myIgnoreEvents = false;
  }
}
