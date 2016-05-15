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
import com.android.tools.idea.gradle.dsl.model.repositories.JCenterDefaultRepositoryModel;
import com.android.tools.idea.gradle.dsl.model.repositories.MavenCentralRepositoryModel;
import com.android.tools.idea.gradle.dsl.model.repositories.RepositoryModel;
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepository;
import com.android.tools.idea.gradle.structure.model.repositories.search.JCenterRepository;
import com.android.tools.idea.gradle.structure.model.repositories.search.MavenCentralRepository;
import com.google.common.collect.Lists;
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

  private GradleBuildModel myParsedModel;
  private String myModuleName;
  private PsParsedDependencies myParsedDependencies;

  private final EventDispatcher<DependenciesChangeListener> myDependenciesChangeEventDispatcher =
    EventDispatcher.create(DependenciesChangeListener.class);

  protected PsModule(@NotNull PsProject parent,
                     @NotNull Module resolvedModel,
                     @NotNull String gradlePath) {
    super(parent);
    myResolvedModel = resolvedModel;
    myGradlePath = gradlePath;
    myModuleName = resolvedModel.getName();
    myParsedModel = GradleBuildModel.get(myResolvedModel);
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
    myDependenciesChangeEventDispatcher.getMulticaster().dependencyChanged(new LibraryDependencyAddedEvent(spec));
  }

  public void fireDependencyModifiedEvent(@NotNull PsDependency dependency) {
    myDependenciesChangeEventDispatcher.getMulticaster().dependencyChanged(new DependencyModifiedEvent(dependency));
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

  @NotNull
  public List<ArtifactRepository> getArtifactRepositories() {
    List<ArtifactRepository> repositories = Lists.newArrayList();
    populateRepositories(repositories);
    return repositories;
  }

  protected final void populateRepositories(@NotNull List<ArtifactRepository> repositories) {
    GradleBuildModel parsedModel = getParsedModel();
    if (parsedModel != null) {
      for (RepositoryModel repositoryModel : parsedModel.repositories().repositories()) {
        if (repositoryModel instanceof JCenterDefaultRepositoryModel) {
          repositories.add(new JCenterRepository());
          continue;
        }
        if (repositoryModel instanceof MavenCentralRepositoryModel) {
          repositories.add(new MavenCentralRepository());
        }
      }
    }
  }

  public interface DependenciesChangeListener extends EventListener {
    void dependencyChanged(@NotNull DependencyChangedEvent event);
  }

  public interface DependencyChangedEvent {
  }

  public static class LibraryDependencyAddedEvent implements DependencyChangedEvent {
    @NotNull private final PsArtifactDependencySpec mySpec;

    LibraryDependencyAddedEvent(@NotNull PsArtifactDependencySpec spec) {
      mySpec = spec;
    }

    @NotNull
    public PsArtifactDependencySpec getSpec() {
      return mySpec;
    }
  }

  public static class DependencyModifiedEvent implements DependencyChangedEvent {
    @NotNull private final PsDependency myDependency;

    DependencyModifiedEvent(@NotNull PsDependency dependency) {
      myDependency = dependency;
    }

    @NotNull
    public PsDependency getDependency() {
      return myDependency;
    }
  }
}
