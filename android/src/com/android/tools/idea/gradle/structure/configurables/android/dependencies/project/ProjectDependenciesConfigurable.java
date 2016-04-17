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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.project;

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.AbstractDependenciesConfigurable;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.google.android.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class ProjectDependenciesConfigurable extends AbstractDependenciesConfigurable<PsModule> {
  @NotNull private final PsModule myModule;
  @NotNull private final PsContext myContext;

  private ProjectDependenciesPanel myDependenciesPanel;

  public ProjectDependenciesConfigurable(@NotNull PsModule module, @NotNull PsContext context, @NotNull List<PsModule> extraTopModules) {
    super(module, context, extraTopModules);
    myModule = module;
    myContext = context;
    setDisplayName("<All Modules>");
  }

  @Override
  public String getBannerSlogan() {
    return getDisplayName();
  }

  @Override
  public ProjectDependenciesPanel createOptionsPanel() {
    if (myDependenciesPanel == null) {
      myDependenciesPanel = new ProjectDependenciesPanel(myModule, getContext(), getExtraTopModules());
      myDependenciesPanel.setHistory(getHistory());
    }
    return myDependenciesPanel;
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
    return "all.modules.dependencies";
  }

  @Override
  public boolean isModified() {
    return myModule.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myModule.isModified()) {
      List<PsModule> toUpdate = Lists.newArrayList();
      myContext.getProject().forEachModule(module -> {
        if (module.isModified()) {
          GradleBuildModel parsedModel = module.getParsedModel();
          if (parsedModel != null && parsedModel.isModified()) {
            toUpdate.add(module);
          }
        }
      });
      if (!toUpdate.isEmpty()) {
        new WriteCommandAction(myContext.getProject().getResolvedModel(), "Applying changes...") {
          @Override
          protected void run(@NotNull Result result) throws Throwable {
            for (PsModule module : toUpdate) {
              GradleBuildModel parsedModel = module.getParsedModel();
              assert parsedModel != null;
              parsedModel.applyChanges();
              module.setModified(false);
              myModule.setModified(false);
            }
          }
        }.execute();
      }
    }
  }

  @Override
  public void reset() {
  }

  @Override
  @NotNull
  public Icon getIcon(boolean expanded) {
    return AllIcons.Nodes.ModuleGroup;
  }

  @Override
  public void disposeUIResources() {
    if (myDependenciesPanel != null) {
      Disposer.dispose(myDependenciesPanel);
    }
  }
}
