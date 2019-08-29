// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.adtui.workbench;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.testFramework.ServiceContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.MutablePicoContainer;

import java.util.ArrayDeque;
import java.util.Deque;

// TODO: Move this to a place to be shared with AndroidTestCase
public final class ComponentStack {
  private final ComponentManager myComponentManager;
  private final MutablePicoContainer myContainer;
  private final Deque<ComponentItem> myComponents;

  public ComponentStack(@NotNull ComponentManager manager) {
    myComponentManager = manager;
    myContainer = (MutablePicoContainer)manager.getPicoContainer();
    myComponents = new ArrayDeque<>();
  }

  public <T> void registerComponentInstance(@NotNull Class<T> key, @NotNull T instance) {
    String keyName = key.getName();
    Object old = myContainer.getComponentInstance(keyName);
    myContainer.unregisterComponent(keyName);
    myComponents.push(new ComponentItem(keyName, old));
    myContainer.registerComponentInstance(keyName, instance);
  }

  public <T> void registerComponentImplementation(@NotNull Class<T> key, @NotNull T instance) {
    Object old = myComponentManager.getComponent(key);
    myComponents.push(new ComponentItem(key, old));
    ServiceContainerUtil.registerComponentInstance(myComponentManager, key, instance);
  }

  public void restoreComponents() {
    while (!myComponents.isEmpty()) {
      ComponentItem component = myComponents.pop();
      if (component.key instanceof Class) {
        //noinspection unchecked
        ServiceContainerUtil.registerComponentInstance(myComponentManager, (Class)component.key, component.instance);
      }
      else {
        myContainer.unregisterComponent(component.key.toString());
        if (component.instance != null) {
          myContainer.registerComponentInstance(component.key.toString(), component.instance);
        }
      }
    }
  }

  private static final class ComponentItem {
    private final Object key;
    private final Object instance;

    private ComponentItem(@NotNull Object key, @Nullable Object instance) {
      this.key = key;
      this.instance = instance;
    }
  }
}
