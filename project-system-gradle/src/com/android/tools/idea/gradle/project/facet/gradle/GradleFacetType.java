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
package com.android.tools.idea.gradle.project.facet.gradle;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import icons.StudioIcons;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link GradleFacet}'s type.
 */
public class GradleFacetType extends FacetType<GradleFacet, GradleFacetConfiguration> {
  public GradleFacetType() {
    super(GradleFacet.getFacetTypeId(), GradleFacet.getFacetId(), GradleFacet.getFacetName());
  }

  @NotNull
  @Override
  public GradleFacetConfiguration createDefaultConfiguration() {
    return new GradleFacetConfiguration();
  }

  @NotNull
  @Override
  public GradleFacet createFacet(@NotNull Module module,
                                 @NotNull String name,
                                 @NotNull GradleFacetConfiguration configuration,
                                 @Nullable Facet underlyingFacet) {
    return new GradleFacet(module, name, configuration);
  }

  @Override
  public boolean isSuitableModuleType(ModuleType moduleType) {
    return moduleType instanceof JavaModuleType;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return StudioIcons.Common.ANDROID_HEAD;
  }
}

