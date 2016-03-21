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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class DependenciesPerspectiveConfigurable extends BasePerspectiveConfigurable {
  private final Map<String, NamedConfigurable<? extends PsModule>> myConfigurablesByGradlePath = Maps.newHashMap();

  private final List<PsModule> myExtraTopModules = Lists.newArrayListWithExpectedSize(2);
  private final Map<PsModule, NamedConfigurable<? extends PsModule>> myExtraTopConfigurables = Maps.newHashMapWithExpectedSize(2);

  public DependenciesPerspectiveConfigurable(@NotNull PsProject project, @NotNull PsContext context) {
    super(project, context);
  }

  @Override
  @Nullable
  protected NamedConfigurable<? extends PsModule> getConfigurable(@NotNull PsModule module) {
    NamedConfigurable<? extends PsModule> configurable;
    if (module instanceof PsAllModulesFakeModule) {
      configurable = myExtraTopConfigurables.get(module);
      if (configurable == null) {
        configurable = new ProjectDependenciesConfigurable(module, getContext(), getExtraTopModules());
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
  public void dispose() {
    super.dispose();
    myConfigurablesByGradlePath.clear();
  }

  @Override
  public ActionCallback navigateTo(@Nullable Place place, boolean requestFocus) {
    // TODO: Implement
    return ActionCallback.DONE;
  }

  @Override
  public void queryPlace(@NotNull Place place) {
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
