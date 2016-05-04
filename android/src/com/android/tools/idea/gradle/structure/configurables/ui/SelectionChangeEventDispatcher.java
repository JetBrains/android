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
package com.android.tools.idea.gradle.structure.configurables.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SelectionChangeEventDispatcher<T> {
  @NotNull private List<SelectionChangeListener<T>> myListeners = new CopyOnWriteArrayList<>();

  public void addListener(@NotNull final SelectionChangeListener<T> listener, @NotNull Disposable parentDisposable) {
    myListeners.add(listener);
    Disposer.register(parentDisposable, () -> myListeners.remove(listener));
  }

  public void selectionChanged(@Nullable T newSelection) {
    myListeners.forEach(listener -> listener.selectionChanged(newSelection));
  }
}
