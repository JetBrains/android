/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio.wizard;

import com.android.tools.idea.npw.assetstudio.IconGenerator;
import com.android.tools.idea.projectsystem.AndroidModuleTemplate;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link GenerateIconsModel} that converts its source asset into .png files.
 */
public final class GenerateImageIconsModel extends GenerateIconsModel {
  public GenerateImageIconsModel(@NotNull AndroidFacet androidFacet) {
    super(androidFacet);
  }

  @Override
  protected void generateIntoPath(@NotNull AndroidModuleTemplate paths, @NotNull IconGenerator iconGenerator) {
    iconGenerator.generateImageIconsIntoPath(paths);
  }
}
