/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.gradle.structure.configurables.BasePerspectiveConfigurable;
import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.android.modules.AbstractModuleConfigurable;
import com.android.tools.idea.gradle.structure.configurables.dependencies.module.MainPanel;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import org.jetbrains.annotations.NotNull;

public class AndroidModuleDependenciesConfigurable extends AbstractModuleConfigurable<PsAndroidModule, MainPanel> {

  public AndroidModuleDependenciesConfigurable(@NotNull PsAndroidModule module,
                                               @NotNull PsContext context,
                                               @NotNull BasePerspectiveConfigurable perspectiveConfigurable) {
    super(context, perspectiveConfigurable, module);
  }

  @Override
  public MainPanel createPanel() {
    return new MainPanel(getModule(), getContext());
  }

  @Override
  @NotNull
  public String getId() {
    return "android.psd.dependencies." + getDisplayName();
  }
}
