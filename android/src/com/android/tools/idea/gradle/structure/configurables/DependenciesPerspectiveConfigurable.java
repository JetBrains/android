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

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.AndroidModuleDependenciesConfigurable;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidModuleModel;
import com.android.tools.idea.gradle.structure.model.PsdModuleModel;
import com.android.tools.idea.gradle.structure.model.PsdProjectModel;
import com.google.common.collect.Maps;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class DependenciesPerspectiveConfigurable extends BasePerspectiveConfigurable {
  private Map<String, NamedConfigurable<? extends PsdModuleModel>> myConfigurablesByGradlePath = Maps.newHashMap();

  public DependenciesPerspectiveConfigurable(@NotNull PsdProjectModel projectModel, @NotNull PsdContext context) {
    super(projectModel, context);
  }

  @Override
  @Nullable
  protected NamedConfigurable<? extends PsdModuleModel> getConfigurable(@NotNull PsdModuleModel moduleModel) {
    String gradlePath = moduleModel.getGradlePath();
    NamedConfigurable<? extends PsdModuleModel> configurable = myConfigurablesByGradlePath.get(gradlePath);
    if (configurable == null) {
      if (moduleModel instanceof PsdAndroidModuleModel) {
        PsdAndroidModuleModel androidModuleModel = (PsdAndroidModuleModel)moduleModel;
        configurable = new AndroidModuleDependenciesConfigurable(androidModuleModel, getContext());
        myConfigurablesByGradlePath.put(gradlePath, configurable);
      }
    }
    return configurable;
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
