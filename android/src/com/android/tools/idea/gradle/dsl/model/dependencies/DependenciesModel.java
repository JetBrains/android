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

import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class DependenciesModel extends GradleDslBlockModel {
  public DependenciesModel(@NotNull DependenciesDslElement dslElement) {
    super(dslElement);
  }

  /**
   * @return all the dependencies (artifact, module, etc.)
   * WIP: Do not use.
   */
  @NotNull
  public List<DependencyModel> all() {
    List<DependencyModel> dependencies = Lists.newArrayList();
    for (String configurationName : myDslElement.getProperties()) {
      GradleDslElementList list = myDslElement.getPropertyElement(configurationName, GradleDslElementList.class);
      if (list != null) {
        for (GradleDslElement element : list.getElements(GradleDslElement.class)) {
          dependencies.addAll(ArtifactDependencyModel.create(element));
          if (element instanceof GradleDslMethodCall) {
            GradleDslMethodCall methodCall = (GradleDslMethodCall)element;
            dependencies.addAll(ModuleDependencyModel.create(configurationName, methodCall));
          }
        }
      }
    }
    return dependencies;
  }

  @NotNull
  public List<ArtifactDependencyModel> artifacts(@NotNull String configurationName) {
    List<ArtifactDependencyModel> dependencies = Lists.newArrayList();
    addArtifacts(configurationName, dependencies);
    return dependencies;
  }

  @NotNull
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
        dependencies.addAll(ArtifactDependencyModel.create(element));
      }
    }
  }

  public boolean containsArtifact(@NotNull String configurationName, @NotNull ArtifactDependencySpec dependency) {
    for (ArtifactDependencyModel artifactDependencyModel : artifacts(configurationName)) {
      if (ArtifactDependencySpec.create(artifactDependencyModel).equals(dependency)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public DependenciesModel addArtifact(@NotNull String configurationName, @NotNull String compactNotation) {
    ArtifactDependencySpec dependency = ArtifactDependencySpec.create(compactNotation);
    if (dependency == null) {
      String msg = String.format("'%1$s' is not a valid artifact dependency", compactNotation);
      throw new IllegalArgumentException(msg);
    }
    addArtifact(configurationName, dependency);
    return this;
  }

  @NotNull
  public DependenciesModel addArtifact(@NotNull String configurationName, @NotNull ArtifactDependencySpec dependency) {
    return addArtifact(configurationName, dependency, Collections.emptyList());
  }

  @NotNull
  public DependenciesModel addArtifact(@NotNull String configurationName,
                                       @NotNull ArtifactDependencySpec dependency,
                                       @NotNull List<ArtifactDependencySpec> excludes) {
    GradleDslElementList list = getOrCreateGradleDslElementList(configurationName);
    ArtifactDependencyModel.createAndAddToList(list, configurationName, dependency, excludes);
    return this;
  }

  @NotNull
  public List<ModuleDependencyModel> modules() {
    List<ModuleDependencyModel> dependencies = Lists.newArrayList();
    for (String configurationName : myDslElement.getProperties()) {
      GradleDslElementList list = myDslElement.getPropertyElement(configurationName, GradleDslElementList.class);
      if (list != null) {
        for (GradleDslMethodCall element : list.getElements(GradleDslMethodCall.class)) {
          dependencies.addAll(ModuleDependencyModel.create(configurationName, element));
        }
      }
    }
    return dependencies;
  }


  @NotNull
  public DependenciesModel addModule(@NotNull String configurationName, @NotNull String path) {
    return addModule(configurationName, path, null);
  }

  @NotNull
  public DependenciesModel addModule(@NotNull String configurationName, @NotNull String path, @Nullable String config) {
    GradleDslElementList list = getOrCreateGradleDslElementList(configurationName);
    ModuleDependencyModel.createAndAddToList(list, configurationName, path, config);
    return this;
  }

  @NotNull
  public List<FileTreeDependencyModel> fileTrees() {
    List<FileTreeDependencyModel> dependencies = Lists.newArrayList();
    for (String configurationName : myDslElement.getProperties()) {
      GradleDslElementList list = myDslElement.getPropertyElement(configurationName, GradleDslElementList.class);
      if (list != null) {
        for (GradleDslMethodCall element : list.getElements(GradleDslMethodCall.class)) {
          dependencies.addAll(FileTreeDependencyModel.create(configurationName, element));
        }
      }
    }
    return dependencies;
  }

  @NotNull
  public DependenciesModel addFileTree(@NotNull String configurationName, @NotNull String dir) {
    return addFileTree(configurationName, dir, null, null);
  }

  @NotNull
  public DependenciesModel addFileTree(@NotNull String configurationName,
                                       @NotNull String dir,
                                       @Nullable List<String> includes,
                                       @Nullable List<String> excludes) {
    GradleDslElementList list = getOrCreateGradleDslElementList(configurationName);
    FileTreeDependencyModel.createAndAddToList(list, configurationName, dir, includes, excludes);
    return this;
  }

  @NotNull
  public List<FileDependencyModel> files() {
    List<FileDependencyModel> dependencies = Lists.newArrayList();
    for (String configurationName : myDslElement.getProperties()) {
      GradleDslElementList list = myDslElement.getPropertyElement(configurationName, GradleDslElementList.class);
      if (list != null) {
        for (GradleDslMethodCall element : list.getElements(GradleDslMethodCall.class)) {
          dependencies.addAll(FileDependencyModel.create(configurationName, element));
        }
      }
    }
    return dependencies;
  }

  @NotNull
  public DependenciesModel addFile(@NotNull String configurationName, @NotNull String file) {
    GradleDslElementList list = getOrCreateGradleDslElementList(configurationName);
    FileDependencyModel.createAndAddToList(list, configurationName, file);
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
  public DependenciesModel remove(@NotNull DependencyModel dependency) {
    GradleDslElementList gradleDslElementList = myDslElement.getPropertyElement(dependency.configurationName(), GradleDslElementList.class);
    if (gradleDslElementList != null) {
      GradleDslElement dependencyElement = dependency.getDslElement();
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
