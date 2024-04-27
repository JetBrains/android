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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.serviceContainer.ComponentManagerImpl;
import com.intellij.testFramework.ServiceContainerUtil;
import java.util.ArrayDeque;
import java.util.Deque;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO: Move this to a place to be shared with AndroidTestCase
public class ComponentStack {
  private final ComponentManagerImpl myComponentManager;
  private final Deque<ComponentItem> myComponents;
  private final Deque<ComponentItem> myServices;
  private final Disposable myDisposable;

  public ComponentStack(@NotNull ComponentManager manager) {
    myComponentManager = (ComponentManagerImpl)manager;
    myComponents = new ArrayDeque<>();
    myServices = new ArrayDeque<>();
    myDisposable = Disposer.newDisposable();
  }

  public <T> void registerServiceInstance(@NotNull Class<T> key, @NotNull T instance) {
    T oldInstance = myComponentManager.getServiceIfCreated(key);
    if (oldInstance == null) {
      ServiceContainerUtil.registerServiceInstance(myComponentManager, key, instance);
      myServices.push(new ComponentItem(key, oldInstance));
    } else {
      ServiceContainerUtil.replaceService(myComponentManager, key, instance, myDisposable);
      // Don't add oldInstance to myServices; BaseComponentAdaptor.replaceInstance will register a Disposable to restore it.
    }
  }

  public <T> void registerComponentInstance(@NotNull Class<T> key, @NotNull T instance) {
    Object old = myComponentManager.getComponent(key);
    myComponents.push(new ComponentItem(key, old));
    ServiceContainerUtil.registerComponentInstance(myComponentManager, key, instance, myDisposable);
  }

  public void restore() {
    Disposer.dispose(myDisposable);
    while (!myComponents.isEmpty()) {
      ComponentItem component = myComponents.pop();
      ServiceContainerUtil.registerComponentInstance(myComponentManager, component.key, component.instance, null);
    }
    while (!myServices.isEmpty()) {
      myComponentManager.unregisterComponent(myServices.pop().key);
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
