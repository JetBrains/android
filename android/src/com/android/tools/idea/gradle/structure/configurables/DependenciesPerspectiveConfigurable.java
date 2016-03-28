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

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.AbstractDependenciesConfigurable;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.PsAllModulesFakeModule;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.module.ModuleDependenciesConfigurable;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.project.ProjectDependenciesConfigurable;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.ui.navigation.Place.queryFurther;

public class DependenciesPerspectiveConfigurable extends BasePerspectiveConfigurable {
  @NonNls private static final String DEPENDENCIES_PLACE = "dependencies.place";

  private final Map<String, AbstractDependenciesConfigurable<? extends PsModule>> myConfigurablesByGradlePath = Maps.newHashMap();

  private final List<PsModule> myExtraTopModules = Lists.newArrayListWithExpectedSize(2);
  private final Map<PsModule, AbstractDependenciesConfigurable<? extends PsModule>> myExtraTopConfigurables = Maps.newHashMapWithExpectedSize(2);

  public DependenciesPerspectiveConfigurable(@NotNull PsProject project, @NotNull PsContext context) {
    super(project, context);
  }

  @Override
  @Nullable
  protected NamedConfigurable<? extends PsModule> getConfigurable(@NotNull PsModule module) {
    AbstractDependenciesConfigurable<? extends PsModule> configurable;
    if (module instanceof PsAllModulesFakeModule) {
      configurable = myExtraTopConfigurables.get(module);
      if (configurable == null) {
        configurable = new ProjectDependenciesConfigurable(module, getContext(), getExtraTopModules());
        configurable.setHistory(myHistory);
        myExtraTopConfigurables.put(module, configurable);
      }
    }
    else {
      String gradlePath = module.getGradlePath();
      configurable = myConfigurablesByGradlePath.get(gradlePath);
      if (configurable == null) {
        if (module instanceof PsAndroidModule) {
          PsAndroidModule androidModule = (PsAndroidModule)module;
          configurable = new ModuleDependenciesConfigurable(androidModule, getContext(), getExtraTopModules());
          configurable.setHistory(myHistory);
          myConfigurablesByGradlePath.put(gradlePath, configurable);
        }
      }
    }
    return configurable;
  }

  @Override
  @NotNull
  protected List<PsModule> getExtraTopModules() {
    if (myExtraTopModules.isEmpty()) {
      myExtraTopModules.add(new PsAllModulesFakeModule(getProject()));
    }
    return myExtraTopModules;
  }

  @Override
  public ActionCallback navigateTo(@Nullable Place place, boolean requestFocus) {
    if (place != null) {
      Object path = place.getPath(DEPENDENCIES_PLACE);
      if (path instanceof String) {
        String moduleName = (String)path;
        if (!isEmpty(moduleName)) {
          getContext().setSelectedModule(moduleName, this);
          selectModule(moduleName);
        }
      }
    }
    return ActionCallback.DONE;
  }

  @Override
  public void queryPlace(@NotNull Place place) {
    NamedConfigurable selectedConfigurable = getSelectedConfigurable();
    if (selectedConfigurable instanceof BaseNamedConfigurable) {
      PsModule module = ((BaseNamedConfigurable)selectedConfigurable).getEditableObject();
      String moduleName = module.getName();
      place.putPath(DEPENDENCIES_PLACE, moduleName);
      queryFurther(selectedConfigurable, place);
      return;
    }
    place.putPath(DEPENDENCIES_PLACE, "");
  }

  @Override
  @Nullable
  public NamedConfigurable getSelectedConfigurable() {
    TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath != null) {
      MyNode node = (MyNode)selectionPath.getLastPathComponent();
      return node.getConfigurable();
    }
    return null;
  }

  @Override
  public void dispose() {
    super.dispose();
    myConfigurablesByGradlePath.clear();
  }

  @Override
  @NotNull
  public String getId() {
    return "android.psd.dependencies";
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Dependencies";
  }
}
