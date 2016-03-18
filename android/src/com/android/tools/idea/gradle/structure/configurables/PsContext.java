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
package com.android.tools.idea.gradle.structure.configurables;

import com.android.tools.idea.gradle.structure.model.PsIssues;
import com.intellij.openapi.Disposable;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;

public class PsContext {
  @NotNull private final EventDispatcher<ChangeListener> myEventDispatcher = EventDispatcher.create(ChangeListener.class);
  @NotNull private final PsIssues myIssues = new PsIssues();

  @Nullable private String mySelectedModuleName;

  @NotNull
  public PsIssues getIssues() {
    return myIssues;
  }

  @Nullable
  public String getSelectedModule() {
    return mySelectedModuleName;
  }

  public void setSelectedModule(@NotNull String moduleName, @NotNull Object source) {
    mySelectedModuleName = moduleName;
    myEventDispatcher.getMulticaster().moduleSelectionChanged(mySelectedModuleName, source);
  }

  public void addListener(@NotNull ChangeListener listener, @NotNull Disposable parentDisposable) {
    myEventDispatcher.addListener(listener, parentDisposable);
  }

  public interface ChangeListener extends EventListener {
    void moduleSelectionChanged(@NotNull String moduleName, @NotNull Object source);
  }
}
