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
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.resolved.ResolvedDependenciesConfigurable;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.google.common.collect.Maps;
import com.intellij.openapi.ui.NamedConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class ResolvedDependenciesPerspectiveConfigurable extends BasePerspectiveConfigurable {
  @NotNull private final Map<String, BaseNamedConfigurable<? extends PsModule>> myConfigurablesByGradlePath = Maps.newHashMap();

  public ResolvedDependenciesPerspectiveConfigurable(@NotNull PsContext context) {
    super(context);
  }

  @Override
  @Nullable
  protected NamedConfigurable<? extends PsModule> getConfigurable(@NotNull PsModule module) {
    if (module instanceof PsAllModulesFakeModule) {
      return null;
    }
    String gradlePath = module.getGradlePath();
    BaseNamedConfigurable<? extends PsModule> configurable = myConfigurablesByGradlePath.get(gradlePath);
    if (configurable == null) {
      if (module instanceof PsAndroidModule) {
        PsAndroidModule androidModule = (PsAndroidModule)module;
        configurable = new ResolvedDependenciesConfigurable(androidModule, getContext());
        configurable.setHistory(myHistory);
        myConfigurablesByGradlePath.put(gradlePath, configurable);
      }
    }
    return configurable;
  }

  @Override
  @NotNull
  protected String getNavigationPathName() {
    return "resolvedDependencies.place";
  }

  @Override
  public void dispose() {
    super.dispose();
    myConfigurablesByGradlePath.clear();
  }

  @Override
  @NotNull
  public String getId() {
    return "android.psd.resolvedDependencies";
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "  Resolved";
  }
}
