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

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.MutablePicoContainer;

import java.util.ArrayDeque;
import java.util.Deque;

public class ComponentStack {
  private final ComponentManager myComponentManager;
  private final MutablePicoContainer myContainer;
  private final Deque<ComponentItem> myComponents;
  private final Deque<ComponentItem> myServices;

  public ComponentStack(@NotNull ComponentManager manager) {
    myComponentManager = manager;
    myContainer = (MutablePicoContainer)manager.getPicoContainer();
    myComponents = new ArrayDeque<>();
    myServices = new ArrayDeque<>();
  }

  public <T> void registerServiceInstance(@NotNull Class<T> key, @NotNull T instance) {
    String keyName = key.getName();
    Object old = myContainer.getComponentInstance(keyName);
    myContainer.unregisterComponent(keyName);
    myServices.push(new ComponentItem(key, old));
    myContainer.registerComponentInstance(keyName, instance);
  }

  public <T> void registerComponentInstance(@NotNull Class<T> key, @NotNull T instance) {
    Object old = myComponentManager.getComponent(key);
    myComponents.push(new ComponentItem(key, old));
    ((ComponentManagerImpl)myComponentManager).registerComponentInstance(key, instance);
  }

  public void restore() {
    while (!myComponents.isEmpty()) {
      ComponentItem component = myComponents.pop();
      ((ComponentManagerImpl)myComponentManager).registerComponentInstance((Class)component.key, component.instance);
    }
    while (!myServices.isEmpty()) {
      ComponentItem service = myServices.pop();
      myContainer.unregisterComponent(service.key.getName());
      if (service.instance != null) {
        myContainer.registerComponentInstance(service.key.getName(), service.instance);
      }
    }
  }

  private static class ComponentItem {
    private final Class key;
    private final Object instance;

    private ComponentItem(@NotNull Class key, @Nullable Object instance) {
      this.key = key;
      this.instance = instance;
    }
  }
}
