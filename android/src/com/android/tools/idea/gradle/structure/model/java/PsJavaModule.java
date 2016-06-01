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
package com.android.tools.idea.gradle.structure.model.java;

import com.android.tools.idea.gradle.JavaProject;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsParsedDependencies;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.function.Consumer;

public class PsJavaModule extends PsModule {
  @NotNull private final JavaProject myGradleModel;

  private PsJavaDependencyCollection myDependencyCollection;

  public PsJavaModule(@NotNull PsProject parent,
                      @NotNull Module resolvedModel,
                      @NotNull String gradlePath,
                      @NotNull JavaProject gradleModel) {
    super(parent, resolvedModel, gradlePath);
    myGradleModel = gradleModel;
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.PpJdk;
  }

  @NotNull
  public JavaProject getGradleModel() {
    return myGradleModel;
  }

  @Override
  @NotNull
  public String getGradlePath() {
    String gradlePath = super.getGradlePath();
    assert gradlePath != null;
    return gradlePath;
  }

  @Override
  @NotNull
  public Module getResolvedModel() {
    Module model = super.getResolvedModel();
    assert model != null;
    return model;
  }

  public void forEachDeclaredDependency(@NotNull Consumer<PsJavaDependency> consumer) {
    getOrCreateDependencyCollection().forEachDeclaredDependency(consumer);
  }

  public void forEachDependency(@NotNull Consumer<PsJavaDependency> consumer) {
    getOrCreateDependencyCollection().forEach(consumer);
  }

  @NotNull
  private PsJavaDependencyCollection getOrCreateDependencyCollection() {
    return myDependencyCollection == null ? myDependencyCollection = new PsJavaDependencyCollection(this) : myDependencyCollection;
  }

  public void addLibraryDependency(@NotNull String library, @NotNull List<String> scopesNames) {
    // Update/reset the "parsed" model.
    addLibraryDependencyToParsedModel(scopesNames, library);

    // Reset dependencies.
    myDependencyCollection = null;
    PsJavaDependencyCollection dependencyCollection = getOrCreateDependencyCollection();

    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create(library);
    assert spec != null;

    PsParsedDependencies parsedDependencies = getParsedDependencies();
    List<ArtifactDependencyModel> matchingParsedDependencies = parsedDependencies.findLibraryDependencies(spec, null);
    for (ArtifactDependencyModel parsedDependency : matchingParsedDependencies) {
      dependencyCollection.addLibraryDependency(spec, parsedDependency);
    }

    fireLibraryDependencyAddedEvent(spec);
    setModified(true);
  }
}
