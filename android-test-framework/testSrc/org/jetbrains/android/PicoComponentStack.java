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
package org.jetbrains.android;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoContainer;

import java.util.ArrayDeque;
import java.util.Deque;

public class PicoComponentStack {
  private final MutablePicoContainer myContainer;
  private final Deque<PicoComponent> myComponents;

  public PicoComponentStack(@NotNull PicoContainer container) {
    this((MutablePicoContainer)container);
  }

  public PicoComponentStack(@NotNull MutablePicoContainer container) {
    myContainer = container;
    myComponents = new ArrayDeque<>();
  }

  public <T> void registerComponent(@NotNull Class<T> key, @NotNull T instance) {
    Object old = myContainer.getComponentInstance(key.getName());
    myContainer.unregisterComponent(key.getName());
    myComponents.push(new PicoComponent(key, old));
    myContainer.registerComponentInstance(key.getName(), instance);
  }

  public void restoreComponents() {
    while (!myComponents.isEmpty()) {
      PicoComponent component = myComponents.pop();
      myContainer.unregisterComponent(component.key.getName());
      if (component.instance != null) {
        myContainer.registerComponentInstance(component.key.getName(), component.instance);
      }
    }
  }

  private static class PicoComponent {
    private final Class key;
    private final Object instance;

    private PicoComponent(@NotNull Class key, @Nullable Object instance) {
      this.key = key;
      this.instance = instance;
    }
  }
}
