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

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.EventListener;
import java.util.List;

public abstract class PsModule extends PsChildModel {
  @Nullable private String myGradlePath;

  // Module can be null in the case of new modules created in the PSD.
  @Nullable private final Module myResolvedModel;

  private boolean myInitParsedModel;
  private GradleBuildModel myParsedModel;
  private String myModuleName;
  private PsParsedDependencies myParsedDependencies;

  private final EventDispatcher<DependenciesChangeListener> myDependenciesChangeEventDispatcher =
    EventDispatcher.create(DependenciesChangeListener.class);

  protected PsModule(@NotNull PsProject parent,
                     @NotNull Module resolvedModel,
                     @NotNull String moduleGradlePath) {
    super(parent);
    myResolvedModel = resolvedModel;
    myGradlePath = moduleGradlePath;
    myModuleName = resolvedModel.getName();
  }

  protected PsModule(@NotNull PsProject parent, @NotNull String name) {
    super(parent);
    myResolvedModel = null;
    myModuleName = name;
  }

  @Override
  @NotNull
  public PsProject getParent() {
    return (PsProject)super.getParent();
  }

  @Override
  @NotNull
  public String getName() {
    return myModuleName;
  }

  @Override
  public boolean isDeclared() {
    return myParsedModel != null;
  }

  @NotNull
  public PsParsedDependencies getParsedDependencies() {
    return myParsedDependencies == null ? myParsedDependencies = new PsParsedDependencies(getParsedModel()) : myParsedDependencies;
  }

  @Nullable
  public GradleBuildModel getParsedModel() {
    if (!myInitParsedModel) {
      myInitParsedModel = true;
      if (myResolvedModel != null) {
        myParsedModel = GradleBuildModel.get(myResolvedModel);
      }
    }
    return myParsedModel;
  }

  protected void addLibraryDependencyToParsedModel(@NotNull List<String> configurationNames, @NotNull String compactNotation) {
    GradleBuildModel parsedModel = getParsedModel();
    if (parsedModel != null) {
      DependenciesModel dependencies = parsedModel.dependencies();
      configurationNames.forEach(configurationName -> dependencies.addArtifact(configurationName, compactNotation));

      getParsedDependencies().reset(getParsedModel());
    }
  }

  public void add(@NotNull DependenciesChangeListener listener, @NotNull Disposable parentDisposable) {
    myDependenciesChangeEventDispatcher.addListener(listener, parentDisposable);
  }

  protected void fireLibraryDependencyAddedEvent(@NotNull PsArtifactDependencySpec spec) {
    myDependenciesChangeEventDispatcher.getMulticaster().libraryDependencyAdded(spec);
  }

  @Nullable
  public String getGradlePath() {
    return myGradlePath;
  }

  @Override
  @Nullable
  public Module getResolvedModel() {
    return myResolvedModel;
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.Module;
  }

  public interface DependenciesChangeListener extends EventListener {
    void libraryDependencyAdded(@NotNull PsArtifactDependencySpec spec);
  }
}
