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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.module;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.ui.dependencies.PsDependencyComparator;
import com.android.tools.idea.gradle.structure.configurables.ui.dependencies.AbstractDeclaredDependenciesTableModel;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsLibraryAndroidDependency;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Model for the table displaying the "editable" dependencies of a module.
 */
class DeclaredDependenciesTableModel extends AbstractDeclaredDependenciesTableModel<PsAndroidDependency> {
  DeclaredDependenciesTableModel(@NotNull PsAndroidModule module, @NotNull PsContext context) {
    super(module, context);
  }

  @Override
  @NotNull
  protected PsAndroidModule getModule() {
    return (PsAndroidModule)super.getModule();
  }

  @Override
  public void reset() {
    List<PsAndroidDependency> dependencies = Lists.newArrayList();
    getModule().forEachDeclaredDependency(dependencies::add);
    Collections.sort(dependencies, new PsDependencyComparator(getContext().getUiSettings()));
    setItems(dependencies);
  }

  @Override
  @Nullable
  public PsLibraryAndroidDependency findDependency(@NotNull PsArtifactDependencySpec spec) {
    PsLibraryDependency found = super.findDependency(spec);
    return found instanceof PsLibraryAndroidDependency ? (PsLibraryAndroidDependency)found : null;
  }
}
