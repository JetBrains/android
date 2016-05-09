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
package com.android.tools.idea.gradle.structure.configurables.android.buildtypes;

import com.android.tools.idea.gradle.structure.configurables.BasePerspectiveConfigurable;
import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.intellij.openapi.ui.NamedConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BuildTypesPerspectiveConfigurable extends BasePerspectiveConfigurable {
  public BuildTypesPerspectiveConfigurable(@NotNull PsContext context) {
    super(context);
  }

  @Override
  @Nullable
  protected NamedConfigurable<? extends PsModule> getConfigurable(@NotNull PsModule module) {
    if (module instanceof PsAndroidModule) {
      return new BuildTypesConfigurable((PsAndroidModule)module);
    }
    return null;
  }

  @Override
  @NotNull
  protected String getNavigationPathName() {
    return "buildTypes.place";
  }

  @Override
  @NotNull
  public String getId() {
    return "android.psd.buildtypes";
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Build Types";
  }
}
