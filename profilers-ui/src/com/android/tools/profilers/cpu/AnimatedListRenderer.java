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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.Choreographer;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class AnimatedListRenderer<M, T extends AnimatedComponent> implements ListDataListener {
  private final Choreographer myChoreographer;
  private final ArrayList<T> myComponents;
  private final Function<M, T> myCreate;
  private final JList<M> myList;

  public AnimatedListRenderer(Choreographer choreographer, JList<M> list, Function<M, T> create) {
    myChoreographer = choreographer;
    myComponents = new ArrayList<>();
    myCreate = create;
    myList = list;

    list.getModel().addListDataListener(this);
    int size = list.getModel().getSize();
    if (size > 0) {
      intervalAdded(new ListDataEvent(list, ListDataEvent.INTERVAL_ADDED, 0, size - 1));
    }
  }

  @Override
  public void intervalAdded(ListDataEvent e) {
    List<T> toAdd = new ArrayList<T>();
    for (int i = e.getIndex0(); i <= e.getIndex1(); i++) {
      T component = myCreate.apply(myList.getModel().getElementAt(i));
      myChoreographer.register(component);
      toAdd.add(component);
    }

    myComponents.addAll(e.getIndex0(), toAdd);
  }

  @Override
  public void intervalRemoved(ListDataEvent e) {
    for (int i = e.getIndex0(); i <= e.getIndex1(); i++) {
      myChoreographer.unregister(myComponents.get(i));
    }
    myComponents.subList(e.getIndex0(), e.getIndex1() + 1).clear();
  }

  @Override
  public void contentsChanged(ListDataEvent e) {
    for (int i = e.getIndex0(); i <= e.getIndex1(); i++) {
      myChoreographer.unregister(myComponents.get(i));
      T component = myCreate.apply(myList.getModel().getElementAt(i));
      myChoreographer.register(component);
      myComponents.set(i, component);
    }
  }

  public Component get(int index) {
    return myComponents.get(index);
  }
}
