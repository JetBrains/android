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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.module;

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.AbstractDependenciesConfigurable;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ModuleDependenciesConfigurable extends AbstractDependenciesConfigurable<PsAndroidModule> {
  @NotNull private final PsAndroidModule myModule;

  private ModuleDependenciesPanel myDependenciesPanel;

  public ModuleDependenciesConfigurable(@NotNull PsAndroidModule module,
                                        @NotNull PsContext context,
                                        @NotNull List<PsModule> extraTopModules) {
    super(module, context, extraTopModules);
    myModule = module;
  }

  @Override
  public ModuleDependenciesPanel createOptionsPanel() {
    if (myDependenciesPanel == null) {
      myDependenciesPanel = new ModuleDependenciesPanel(getEditableObject(), getContext(), getExtraTopModules());
      myDependenciesPanel.setHistory(getHistory());
    }
    return myDependenciesPanel;
  }

  public void putPath(@NotNull Place place, @NotNull String dependency) {
    createOptionsPanel().putPath(place, dependency);
  }

  @Override
  public ActionCallback navigateTo(@Nullable Place place, boolean requestFocus) {
    return createOptionsPanel().navigateTo(place, requestFocus);
  }

  @Override
  public void queryPlace(@NotNull Place place) {
    createOptionsPanel().queryPlace(place);
  }

  @Override
  public void setHistory(History history) {
    super.setHistory(history);
    if (myDependenciesPanel != null) {
      myDependenciesPanel.setHistory(history);
    }
  }

  @Override
  @NotNull
  public String getId() {
    return "module.dependencies." + getDisplayName();
  }

  @Override
  public boolean isModified() {
    return myModule.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myModule.isModified()) {
      GradleBuildModel parsedModel = myModule.getParsedModel();
      if (parsedModel != null && parsedModel.isModified()) {
        String name = String.format("Applying changes to module '%1$s'", myModule.getName());
        new WriteCommandAction(myModule.getParent().getResolvedModel(), name) {
          @Override
          protected void run(@NotNull Result result) throws Throwable {
            parsedModel.applyChanges();
            myModule.setModified(false);
          }
        }.execute();
      }
    }
  }

  @Override
  public void reset() {
  }

  @Override
  public void disposeUIResources() {
    if (myDependenciesPanel != null) {
      Disposer.dispose(myDependenciesPanel);
    }
  }
}
