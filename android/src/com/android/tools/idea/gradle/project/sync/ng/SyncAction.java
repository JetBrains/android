/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.builder.model.AndroidProject;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.*;

/**
 * Action that executed inside Gradle to obtain the project structure (IDEA project and modules) and the custom models for each module (e.g.
 * {@link AndroidProject}.
 */
// (This class replaces org.jetbrains.plugins.gradle.model.ProjectImportAction.)
public class SyncAction implements BuildAction<SyncProjectModels>, Serializable {
  @NotNull private final Set<Class<?>> myExtraAndroidModelTypes;
  @NotNull private final Set<Class<?>> myExtraJavaModelTypes;

  public SyncAction(@NotNull Set<Class<?>> extraAndroidModelTypes, @NotNull Set<Class<?>> extraJavaModelTypes) {
    myExtraAndroidModelTypes = extraAndroidModelTypes;
    myExtraJavaModelTypes = extraJavaModelTypes;
  }

  @Override
  @Nullable
  public SyncProjectModels execute(@NotNull BuildController controller) {
    SyncProjectModels models = new SyncProjectModels(myExtraAndroidModelTypes, myExtraJavaModelTypes);
    models.populate(controller);
    return models;
  }
}
