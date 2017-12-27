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

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class JComboBoxView<T, A extends Enum<A>> implements ItemListener {

  private final Supplier<List<T>> myList;
  private final Supplier<T> myGet;
  private final Consumer<T> mySet;
  private final JComboBox<T> myCombo;
  private final AspectModel<A> myObject;
  private final A myAspect;
  private final AspectObserver myAspectObserver;
  private boolean myIgnoreEvents;

  public JComboBoxView(JComboBox<T> combo, AspectModel<A> object, A aspect, Supplier<List<T>> list, Supplier<T> get, Consumer<T> set) {
    myAspectObserver = new AspectObserver();
    myList = list;
    myGet = get;
    mySet = set;
    myCombo = combo;
    myObject = object;
    myAspect = aspect;
  }

  public void bind() {
    myCombo.addItemListener(this);
    myObject.addDependency(myAspectObserver).onChange(myAspect, this::changed);
    changed();
  }

  @Override
  public void itemStateChanged(ItemEvent event) {
    if (event.getStateChange() == ItemEvent.SELECTED && !myIgnoreEvents) {
      T item = (T)event.getItem();
      mySet.accept(item);
    }
  }

  private void changed() {
    List<T> list = myList.get();
    T selected = myGet.get();

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
