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
package com.android.tools.idea.gradle.structure.model;

import com.android.tools.idea.gradle.dsl.api.GradleModelProvider;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Consumer;

public class PsProject extends PsModel {
  @NotNull private final Project myProject;
  @NotNull private final ProjectBuildModel myParsedModel;

  @NotNull private final PsVariables myVariables;
  @NotNull private final PsPomDependencyCache myPomDependencyCache = new PsPomDependencies();
  @NotNull private final PsModuleCollection myModuleCollection;

  private boolean myModified;

  public PsProject(@NotNull Project project) {
    myProject = project;
    myParsedModel = GradleModelProvider.get().getProjectModel(project);
    // TODO(b/77695733): Ensure that getProjectBuildModel() is indeed not null.
    myVariables = new PsVariables(
      this, "Project: " + getName(), Objects.requireNonNull(this.myParsedModel.getProjectBuildModel()).ext(), null);
    myModuleCollection = new PsModuleCollection(this);
  }

  @Nullable
  public PsModule findModuleByName(@NotNull String moduleName) {
    return myModuleCollection.items().stream().filter(it -> Objects.equals(it.getName(), moduleName)).findFirst().orElse(null);
  }

  @Nullable
  public PsModule findModuleByGradlePath(@NotNull String gradlePath) {
    return myModuleCollection.items().stream().filter(it -> Objects.equals(it.getGradlePath(), gradlePath)).findFirst().orElse(null);
  }

  @Override
  @NotNull
  public Project getResolvedModel() {
    return myProject;
  }

  @Override
  @NotNull
  public String getName() {
    return myProject.getName();
  }

  public void forEachModule(@NotNull Consumer<PsModule> consumer) {
    myModuleCollection.items().stream().sorted(Comparator.comparing(v -> v.getName().toLowerCase())).forEachOrdered(consumer);
  }

  @Override
  @Nullable
  public PsModel getParent() {
    return null;
  }

  @Override
  public boolean isDeclared() {
    return true;
  }

  @Override
  @Nullable
  public Icon getIcon() {
    return null;
  }

  public int getModelCount() {
    return myModuleCollection.items().size();
  }

  public void applyChanges() {
    if (isModified()) {
      new WriteCommandAction(getResolvedModel(), "Applying changes to the project structure.") {
        @Override
        protected void run(@NotNull Result result) {
          myParsedModel.applyChanges();
          setModified(false);
        }
      }.execute();
    }
  }

  @NotNull
  public PsVariables getVariables() {
    return myVariables;
  }

  @NotNull
  public PsPomDependencyCache getPomDependencyCache() {
    return myPomDependencyCache;
  }

  @NotNull
  public ProjectBuildModel getParsedModel() {
    return myParsedModel;
  }
}
