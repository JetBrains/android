/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.editor;

import com.android.tools.idea.gradle.structure.configurables.model.ModuleMergedModel;
import com.intellij.openapi.CompositeDisposable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.navigation.History;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class ModuleElementsEditor implements ModuleConfigurationEditor {
  @NotNull private final CompositeDisposable myDisposables = new CompositeDisposable();
  @NotNull private final ModuleMergedModel myModel;

  protected JComponent myComponent;
  protected History myHistory;

  protected ModuleElementsEditor(@NotNull ModuleMergedModel model) {
    myModel = model;
  }

  public void registerDisposable(@NotNull Disposable disposable) {
    myDisposables.add(disposable);
  }

  @NotNull
  public ModuleMergedModel getModel() {
    return myModel;
  }

  @Nullable
  public JComponent getComponent() {
    return createComponent();
  }

  @Override
  @Nullable
  public final JComponent createComponent() {
    if (myComponent == null) {
      myComponent = doCreateComponent();
    }
    return myComponent;
  }

  @Nullable
  protected abstract JComponent doCreateComponent();

  @Override
  public void setHistory(@Nullable History history) {
    myHistory = history;
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(myDisposables);
  }
}
