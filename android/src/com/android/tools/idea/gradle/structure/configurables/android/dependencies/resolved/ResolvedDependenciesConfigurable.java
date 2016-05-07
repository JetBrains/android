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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.resolved;

import com.android.tools.idea.gradle.structure.configurables.BaseNamedConfigurable;
import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ResolvedDependenciesConfigurable extends BaseNamedConfigurable<PsAndroidModule> {
  @NotNull private final PsAndroidModule myModule;
  @NotNull private final PsContext myContext;

  private MainPanel myMainPanel;

  public ResolvedDependenciesConfigurable(@NotNull PsAndroidModule module, @NotNull PsContext context) {
    super(module);
    myModule = module;
    myContext = context;
  }

  @Override
  @NotNull
  public String getId() {
    return "module.ResolvedDependencies." + getDisplayName();
  }

  @Override
  public MainPanel createOptionsPanel() {
    if (myMainPanel == null) {
      myMainPanel = new MainPanel(myModule, myContext);
      myMainPanel.setHistory(getHistory());
    }
    return myMainPanel;
  }

  @Override
  public void setHistory(History history) {
    super.setHistory(history);
    if (myMainPanel != null) {
      myMainPanel.setHistory(history);
    }
  }

  @Override
  public boolean isModified() {
    return myModule.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
  }

  @Override
  public void reset() {
  }

  @Override
  public void disposeUIResources() {
    if (myMainPanel != null) {
      Disposer.dispose(myMainPanel);
    }
  }

  @Override
  public ActionCallback navigateTo(@Nullable Place place, boolean requestFocus) {
    return createOptionsPanel().navigateTo(place, requestFocus);
  }

  @Override
  public void queryPlace(@NotNull Place place) {
    createOptionsPanel().queryPlace(place);
  }
}
