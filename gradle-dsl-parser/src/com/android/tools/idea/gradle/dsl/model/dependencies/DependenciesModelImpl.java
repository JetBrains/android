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
package com.android.tools.idea.gradle.dsl.model.dependencies;

import com.android.tools.idea.gradle.dsl.api.dependencies.*;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class DependenciesModelImpl extends GradleDslBlockModel implements DependenciesModel {
  public DependenciesModelImpl(@NotNull DependenciesDslElement dslElement) {
    super(dslElement);
  }

  /**
   * @return all the dependencies (artifact, module, etc.)
   * WIP: Do not use.
   */
  @NotNull
  @Override
  public List<DependencyModel> all() {
    List<DependencyModel> dependencies = Lists.newArrayList();
    for (String configurationName : myDslElement.getProperties()) {
      GradleDslElementList list = myDslElement.getPropertyElement(configurationName, GradleDslElementList.class);
      if (list != null) {
        for (GradleDslElement element : list.getElements(GradleDslElement.class)) {
          dependencies.addAll(ArtifactDependencyModelImpl.create(element));
          if (element instanceof GradleDslMethodCall) {
            GradleDslMethodCall methodCall = (GradleDslMethodCall)element;
            dependencies.addAll(ModuleDependencyModelImpl.create(configurationName, methodCall));
          }
        }
      }
    }
    return dependencies;
  }

  @NotNull
  @Override
  public List<ArtifactDependencyModel> artifacts(@NotNull String configurationName) {
    List<ArtifactDependencyModel> dependencies = Lists.newArrayList();
    addArtifacts(configurationName, dependencies);
    return dependencies;
  }

  @NotNull
  @Override
  public List<ArtifactDependencyModel> artifacts() {
    List<ArtifactDependencyModel> dependencies = Lists.newArrayList();
    for (String configurationName : myDslElement.getProperties()) {
      addArtifacts(configurationName, dependencies);
    }
    return dependencies;
  }

  private void addArtifacts(@NotNull String configurationName, @NotNull List<ArtifactDependencyModel> dependencies) {
    GradleDslElementList list = myDslElement.getPropertyElement(configurationName, GradleDslElementList.class);
    if (list != null) {
      for (GradleDslElement element : list.getElements(GradleDslElement.class)) {
        dependencies.addAll(ArtifactDependencyModelImpl.create(element));
      }
    }
  }

  @Override
  public boolean containsArtifact(@NotNull String configurationName, @NotNull ArtifactDependencySpec dependency) {
    for (ArtifactDependencyModel artifactDependencyModel : artifacts(configurationName)) {
      if (ArtifactDependencySpecImpl.create(artifactDependencyModel).equals(dependency)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  @Override
  public DependenciesModel addArtifact(@NotNull String configurationName, @NotNull String compactNotation) {
    ArtifactDependencySpec dependency = ArtifactDependencySpecImpl.create(compactNotation);
    if (dependency == null) {
      String msg = String.format("'%1$s' is not a valid artifact dependency", compactNotation);
      throw new IllegalArgumentException(msg);
    }
    addArtifact(configurationName, dependency);
    return this;
  }

  @NotNull
  @Override
  public DependenciesModel addArtifact(@NotNull String configurationName, @NotNull ArtifactDependencySpec dependency) {
    return addArtifact(configurationName, dependency, Collections.emptyList());
  }

  @NotNull
  @Override
  public DependenciesModel addArtifact(@NotNull String configurationName,
                                       @NotNull ArtifactDependencySpec dependency,
                                       @NotNull List<ArtifactDependencySpec> excludes) {
    GradleDslElementList list = getOrCreateGradleDslElementList(configurationName);
    ArtifactDependencyModelImpl.createAndAddToList(list, configurationName, dependency, excludes);
    return this;
  }

  @NotNull
  @Override
  public List<ModuleDependencyModel> modules() {
    List<ModuleDependencyModel> dependencies = Lists.newArrayList();
    for (String configurationName : myDslElement.getProperties()) {
      GradleDslElementList list = myDslElement.getPropertyElement(configurationName, GradleDslElementList.class);
      if (list != null) {
        for (GradleDslMethodCall element : list.getElements(GradleDslMethodCall.class)) {
          dependencies.addAll(ModuleDependencyModelImpl.create(configurationName, element));
        }
      }
    }
    return dependencies;
  }


  @NotNull
  @Override
  public DependenciesModel addModule(@NotNull String configurationName, @NotNull String path) {
    return addModule(configurationName, path, null);
  }

  @NotNull
  @Override
  public DependenciesModel addModule(@NotNull String configurationName, @NotNull String path, @Nullable String config) {
    GradleDslElementList list = getOrCreateGradleDslElementList(configurationName);
    ModuleDependencyModelImpl.createAndAddToList(list, configurationName, path, config);
    return this;
  }

  @NotNull
  @Override
  public List<FileTreeDependencyModel> fileTrees() {
    List<FileTreeDependencyModel> dependencies = Lists.newArrayList();
    for (String configurationName : myDslElement.getProperties()) {
      GradleDslElementList list = myDslElement.getPropertyElement(configurationName, GradleDslElementList.class);
      if (list != null) {
        for (GradleDslMethodCall element : list.getElements(GradleDslMethodCall.class)) {
          dependencies.addAll(FileTreeDependencyModelImpl.create(configurationName, element));
        }
      }
    }
    return dependencies;
  }

  @Override
  @NotNull
  public DependenciesModel addFileTree(@NotNull String configurationName, @NotNull String dir) {
    return addFileTree(configurationName, dir, null, null);
  }

  @Override
  @NotNull
  public DependenciesModel addFileTree(@NotNull String configurationName,
                                       @NotNull String dir,
                                       @Nullable List<String> includes,
                                       @Nullable List<String> excludes) {
    GradleDslElementList list = getOrCreateGradleDslElementList(configurationName);
    FileTreeDependencyModelImpl.createAndAddToList(list, configurationName, dir, includes, excludes);
    return this;
  }

  @NotNull
  @Override
  public List<FileDependencyModel> files() {
    List<FileDependencyModel> dependencies = Lists.newArrayList();
    for (String configurationName : myDslElement.getProperties()) {
      GradleDslElementList list = myDslElement.getPropertyElement(configurationName, GradleDslElementList.class);
      if (list != null) {
        for (GradleDslMethodCall element : list.getElements(GradleDslMethodCall.class)) {
          dependencies.addAll(FileDependencyModelImpl.create(configurationName, element));
        }
      }
    }
    return dependencies;
  }

  @Override
  @NotNull
  public DependenciesModel addFile(@NotNull String configurationName, @NotNull String file) {
    GradleDslElementList list = getOrCreateGradleDslElementList(configurationName);
    FileDependencyModelImpl.createAndAddToList(list, configurationName, file);
    return this;
  }

  @NotNull
  private GradleDslElementList getOrCreateGradleDslElementList(@NotNull String configurationName) {
    GradleDslElementList list = myDslElement.getPropertyElement(configurationName, GradleDslElementList.class);
    if (list == null) {
      list = new GradleDslElementList(myDslElement, configurationName);
      myDslElement.setNewElement(configurationName, list);
    }
    return list;
  }

  @NotNull
  @Override
  public DependenciesModel remove(@NotNull DependencyModel dependency) {
    GradleDslElementList gradleDslElementList = myDslElement.getPropertyElement(dependency.configurationName(), GradleDslElementList.class);
    if (gradleDslElementList != null) {
      if (!(dependency instanceof DependencyModelImpl)) {
        Logger.getInstance(DependenciesModelImpl.class)
          .warn("Tried to remove an unknown dependency type!");
        return this;
      }
      GradleDslElement dependencyElement = ((DependencyModelImpl)dependency).getDslElement();
      GradleDslElement parent = dependencyElement.getParent();
      if (parent instanceof GradleDslMethodCall) {
        GradleDslMethodCall methodCall = (GradleDslMethodCall)parent;
        List<GradleDslElement> arguments = methodCall.getArguments();
        if (arguments.size() == 1 && arguments.get(0).equals(dependencyElement)) {
          // If this is the last argument, remove the method call altogether.
          gradleDslElementList.removeElement(methodCall);
        }
        else {
          methodCall.remove(dependencyElement);
        }
      }
      else {
        gradleDslElementList.removeElement(dependencyElement);
      }
    }
    return this;
  }
}
