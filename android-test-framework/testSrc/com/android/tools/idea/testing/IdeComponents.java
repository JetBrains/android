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
package com.android.tools.idea.testing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.pico.DefaultPicoContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.ComponentAdapter;

import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

public final class IdeComponents {
  private final Project myProject;
  private final Queue<Runnable> myUndoQueue = new ArrayDeque<>();

  public IdeComponents(@Nullable Project project) {
    myProject = project;
  }

  @NotNull
  public <T> T mockService(@NotNull Class<T> serviceType) {
    T mock = mock(serviceType);
    doReplaceService(ApplicationManager.getApplication(), serviceType, mock, myUndoQueue);
    return mock;
  }

  public <T> void replaceService(@NotNull Class<T> serviceType, @NotNull T newServiceInstance) {
    doReplaceService(ApplicationManager.getApplication(), serviceType, newServiceInstance, myUndoQueue);
  }

  @NotNull
  public <T> T mockProjectService(@NotNull Class<T> serviceType) {
    T mock = mock(serviceType);
    assertNotNull(myProject);
    doReplaceService(myProject, serviceType, mock, null);
    return mock;
  }

  public void restore() {
    for (Runnable runnable : myUndoQueue) {
      runnable.run();
    }
  }

  @Override
  protected void finalize() {
    assert myUndoQueue.isEmpty() : "Forgot to restore original IDE services";
  }

  public static <T> void replaceService(@NotNull Project project, @NotNull Class<T> serviceType, @NotNull T newServiceInstance) {
    doReplaceService(project, serviceType, newServiceInstance, null);
  }

  private static <T> void doReplaceService(@NotNull ComponentManager componentManager,
                                           @NotNull Class<T> serviceType,
                                           @NotNull T newServiceInstance,
                                           @Nullable Queue<Runnable> undoQueue) {
    DefaultPicoContainer picoContainer = (DefaultPicoContainer)componentManager.getPicoContainer();

    String componentKey = serviceType.getName();

    Object componentInstance = picoContainer.getComponentInstance(componentKey);
    assert componentInstance == null || serviceType.isAssignableFrom(componentInstance.getClass());
    T oldServiceInstance = (T)componentInstance;

    ComponentAdapter componentAdapter = picoContainer.unregisterComponent(componentKey);
    assert componentAdapter != null;

    picoContainer.registerComponentInstance(componentKey, newServiceInstance);
    assertSame(newServiceInstance, picoContainer.getComponentInstance(componentKey));

    if (undoQueue != null && oldServiceInstance != null) {
      undoQueue.add(() -> doReplaceService(componentManager, serviceType, oldServiceInstance, null));
    }
  }
}
